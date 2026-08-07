#!/usr/bin/env python3
"""Structural validation for the embedded rule packs.

Catches the class of mistake that unit tests do not: a rule pack that
deserializes cleanly and computes a plausible-looking but wrong number.
Bracket data is transcribed by hand from published tax tables, so the failure
mode is a silently mis-keyed threshold rather than a crash.

Checks per pack:
  - every US pack covers all 50 states + DC, and nothing else
  - bracket thresholds strictly increase within a bracket list
  - every bracket states an explicit `over`, and the first is 0
  - rates are within a sane range and non-decreasing across brackets
  - required federal/FICA/UK fields are present and sanely bounded
  - year-over-year drift on shared keys is flagged for review

Usage:
    python3 validate_rulepacks.py [path/to/rulepacks]

Exits non-zero on any error. Warnings do not fail the run.
"""
import json
import pathlib
import sys

STATES = {
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID",
    "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS",
    "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK",
    "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
    "WI", "WY", "DC",
}

MAX_STATE_RATE = 0.15    # CA's 13.3% top bracket is the real-world ceiling
MAX_FEDERAL_RATE = 0.40

errors: list[str] = []
warnings: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def check_brackets(label: str, brackets: list, max_rate: float) -> None:
    """Validate ordering and rate sanity for one bracket list.

    Brackets are lower-bound (`over`) form, matching published tax tables: the
    first band starts at 0 and each subsequent `over` strictly increases. The
    legacy upper-bound `upTo` form is no longer understood anywhere — the Java
    model dropped the field, so a pack still using it now deserializes with null
    bounds and is rejected outright by HttpRulePackClient.
    """
    if not brackets:
        return  # a state with no income tax legitimately has none

    legacy = [i for i, b in enumerate(brackets) if "upTo" in b]
    if legacy:
        err(f"{label}: uses the retired 'upTo' form at {legacy}; convert to 'over'")

    missing = [i for i, b in enumerate(brackets) if b.get("over") is None]
    if missing:
        err(f"{label}: brackets {missing} have no 'over' bound")

    first = brackets[0].get("over")
    if first != 0:
        err(f"{label}[0]: first bracket must start at over=0, got {first}")

    previous = None
    for i, b in enumerate(brackets):
        rate = b.get("rate")
        if rate is None:
            err(f"{label}[{i}]: missing 'rate'")
        elif not 0 <= rate <= max_rate:
            err(f"{label}[{i}]: rate {rate} outside 0..{max_rate}")

        over = b.get("over")
        if over is None:
            err(f"{label}[{i}]: missing 'over'")
            continue
        if over < 0:
            err(f"{label}[{i}]: over {over} must not be negative")
        if previous is not None and over <= previous:
            err(f"{label}[{i}]: over {over} not greater than previous {previous}")
        previous = over

    rates = [b["rate"] for b in brackets if "rate" in b]
    for i in range(1, len(rates)):
        if rates[i] < rates[i - 1]:
            err(f"{label}: rate decreases at index {i} ({rates[i-1]} -> {rates[i]})")


def check_us(pack: dict, name: str) -> None:
    fed = pack.get("federal") or {}
    if not fed:
        err(f"{name}: missing 'federal'")
        return

    check_brackets(f"{name} federal.brackets", fed.get("brackets", []), MAX_FEDERAL_RATE)
    for status, br in (fed.get("bracketsByFilingStatus") or {}).items():
        check_brackets(f"{name} federal.{status}", br, MAX_FEDERAL_RATE)

    deductions = fed.get("standardDeductions") or {}
    for status in ("SINGLE", "MARRIED", "HEAD_OF_HOUSEHOLD"):
        value = deductions.get(status)
        if value is None:
            err(f"{name}: missing standardDeductions.{status}")
        elif not 1000 < value < 100000:
            err(f"{name}: standardDeductions.{status} = {value} looks implausible")
    if deductions.get("MARRIED") and deductions.get("SINGLE"):
        if deductions["MARRIED"] <= deductions["SINGLE"]:
            err(f"{name}: MARRIED deduction must exceed SINGLE")

    rate = fed.get("supplementalWithholdingRate")
    if rate is None or not 0 < rate < 0.5:
        err(f"{name}: supplementalWithholdingRate = {rate} outside 0..0.5")
    if fed.get("withholdingAllowance") is None:
        err(f"{name}: missing federal.withholdingAllowance")

    fica = pack.get("fica") or {}
    for field, low, high in (
        ("ssRate", 0.0, 0.1),
        ("medicareRate", 0.0, 0.05),
        ("additionalRate", 0.0, 0.05),
    ):
        value = fica.get(field)
        if value is None or not low <= value <= high:
            err(f"{name}: fica.{field} = {value} outside {low}..{high}")
    wage_base = fica.get("ssWageBase")
    if wage_base is None or not 50000 < wage_base < 500000:
        err(f"{name}: fica.ssWageBase = {wage_base} looks implausible")

    states = pack.get("states") or {}
    missing = STATES - set(states)
    extra = set(states) - STATES
    if missing:
        err(f"{name}: missing states {sorted(missing)}")
    if extra:
        err(f"{name}: unknown states {sorted(extra)}")

    for code, entry in sorted(states.items()):
        check_brackets(f"{name} states.{code}", entry.get("brackets", []), MAX_STATE_RATE)
        local = entry.get("local")
        if local is not None and not 0 <= local <= 0.1:
            err(f"{name}: states.{code}.local = {local} outside 0..0.1")


def check_uk(pack: dict, name: str) -> None:
    income_tax = pack.get("incomeTax") or {}
    check_brackets(f"{name} incomeTax.bands", income_tax.get("bands", []), 0.6)

    allowance = income_tax.get("personalAllowance")
    if allowance is None or not 0 < allowance < 50000:
        err(f"{name}: personalAllowance = {allowance} looks implausible")

    ni = pack.get("ni") or {}
    lower = ni.get("primaryThresholdAnnual")
    upper = ni.get("upperEarningsLimit")
    if lower is None or upper is None:
        err(f"{name}: missing NI thresholds")
    elif upper <= lower:
        err(f"{name}: upperEarningsLimit {upper} must exceed primaryThreshold {lower}")

    for plan, cfg in (pack.get("studentLoan") or {}).items():
        threshold = cfg.get("threshold")
        rate = cfg.get("rate")
        if threshold is None or not 0 < threshold < 100000:
            err(f"{name}: studentLoan.{plan}.threshold = {threshold} implausible")
        if rate is None or not 0 < rate < 0.2:
            err(f"{name}: studentLoan.{plan}.rate = {rate} implausible")


def compare_years(packs: dict) -> None:
    """Flag large year-over-year moves — usually a transcription slip."""
    by_country: dict[str, dict[int, dict]] = {}
    for pack in packs.values():
        meta = pack.get("metadata") or {}
        by_country.setdefault(meta.get("country"), {})[meta.get("taxYear")] = pack

    for country, years in by_country.items():
        ordered = sorted(y for y in years if y is not None)
        for older, newer in zip(ordered, ordered[1:]):
            a, b = years[older], years[newer]
            if country != "US":
                continue
            for code in sorted(set(a.get("states", {})) & set(b.get("states", {}))):
                ra = [x["rate"] for x in a["states"][code].get("brackets", [])]
                rb = [x["rate"] for x in b["states"][code].get("brackets", [])]
                if ra and rb and abs(max(rb) - max(ra)) > 0.02:
                    warn(f"{country} {code}: top rate moved {max(ra)} -> {max(rb)} "
                         f"between {older} and {newer}")


def main() -> int:
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else \
        pathlib.Path(__file__).resolve().parents[2] / "main/resources/rulepacks"

    files = sorted(root.glob("*.json"))
    if not files:
        print(f"no rule packs found under {root}", file=sys.stderr)
        return 1

    packs = {}
    for path in files:
        try:
            packs[path.name] = json.loads(path.read_text())
        except json.JSONDecodeError as exc:
            err(f"{path.name}: invalid JSON — {exc}")

    for name, pack in packs.items():
        meta = pack.get("metadata") or {}
        country, year = meta.get("country"), meta.get("taxYear")
        if not country or not year:
            err(f"{name}: metadata must carry country and taxYear")
            continue
        if not name.startswith(f"{country}-{year}"):
            err(f"{name}: filename disagrees with metadata {country}-{year}")
        if country == "US":
            check_us(pack, name)
        elif country == "UK":
            check_uk(pack, name)
        else:
            err(f"{name}: unknown country {country}")

    compare_years(packs)

    for w in warnings:
        print(f"warning: {w}")
    for e in errors:
        print(f"ERROR: {e}", file=sys.stderr)

    print(f"\nchecked {len(packs)} pack(s): {len(errors)} error(s), {len(warnings)} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
