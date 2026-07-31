// rsu-cards.jsx — Surface A (Equity/RSUs card, 3 states) and Surface F
// (Supplemental income card for Insights). Uses B2Card/B2CardHeader/
// B2MoneyField from variation-b-v2.jsx and tokens from inc-theme.css.

const RSUIcon = ({ color }) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2">
    <path d="M12 2l3 6.5 7 1-5 5 1.5 7L12 18l-6.5 3.5 1.5-7-5-5 7-1z" />
  </svg>
);

// ── Surface A: Equity / RSUs card ──────────────────────────────
function RSUEquityCard({ rsu }) {
  if (!rsu.signedIn) {
    return (
      <B2Card>
        <B2CardHeader
          icon={<RSUIcon color="var(--inc-sage)" />}
          title="Equity / RSUs"
          subtitle="Taxed as supplemental income"
        />
        <B2MoneyField
          label="RSU value vesting this year (annual)"
          value={rsu.signedOutValue}
          onChange={rsu.setSignedOutValue}
        />
        <div style={{ fontSize: 12, color: 'var(--inc-textMute)', marginTop: -8, lineHeight: '17px' }}>
          <a href="#" onClick={(e) => e.preventDefault()} style={{ color: 'var(--inc-sage)', fontWeight: 600, textDecoration: 'none' }}>
            Sign in
          </a> to model grants and vesting schedules.
        </div>
      </B2Card>
    );
  }

  if (rsu.grants.length === 0) {
    return (
      <B2Card>
        <B2CardHeader
          icon={<RSUIcon color="var(--inc-sage)" />}
          title="Equity / RSUs"
          subtitle="Taxed as supplemental income"
        />
        <button onClick={() => rsu.setView('list')} style={{
          width: '100%', background: 'var(--inc-sageBg)', border: `1.5px dashed var(--inc-sageSoft)`,
          borderRadius: 14, padding: '16px', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
          fontFamily: 'inherit', fontSize: 14, fontWeight: 700, color: 'var(--inc-sageDeep)',
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          Add your RSU grants
        </button>
      </B2Card>
    );
  }

  const s = rsu.summary;
  return (
    <B2Card>
      <button onClick={() => rsu.setView('list')} style={{
        width: '100%', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left',
        padding: 0, display: 'flex', alignItems: 'center', gap: 12, fontFamily: 'inherit',
      }}>
        <div style={{
          width: 38, height: 38, borderRadius: 12, background: 'var(--inc-sageBg)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}><RSUIcon color="var(--inc-sage)" /></div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>
            RSUs vesting in {CURRENT_TAX_YEAR} · ~{fmtMoney(s.total, { cents: false })}
          </div>
          <div style={{ fontSize: 12, color: 'var(--inc-textDim)', marginTop: 2 }}>
            {s.count} grant{s.count !== 1 ? 's' : ''} · {s.tickers}
          </div>
        </div>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--inc-textMute)" strokeWidth="2.5" style={{ flexShrink: 0 }}>
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>
      <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--inc-hairline)' }}>
        {!rsu.overrideOpen ? (
          <button onClick={() => rsu.setOverrideOpen(true)} style={{
            background: 'none', border: 'none', cursor: 'pointer', padding: 0,
            fontFamily: 'inherit', fontSize: 12.5, fontWeight: 700, color: 'var(--inc-sage)',
          }}>Override amount</button>
        ) : (
          <div>
            <B2MoneyField
              label={`Override: ${CURRENT_TAX_YEAR} RSU value`}
              value={rsu.manualOverride}
              onChange={rsu.setManualOverride}
              placeholder={String(Math.round(s.total))}
            />
            <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', marginTop: -12 }}>
              Explicit override replaces the grant-derived total above.
            </div>
          </div>
        )}
      </div>
    </B2Card>
  );
}

// ── Surface F: Supplemental income card (Insights) ────────────
function RSUSupplementalCard({ bonus = 0, bonusDate = '', commission = 0, rsuVesting = 0 }) {
  const gross = bonus + commission + rsuVesting;
  if (gross <= 0) return null;

  const fed = gross * 0.22;
  const ss = gross * 0.062;
  const medicare = gross * 0.0145;
  const net = gross - fed - ss - medicare;

  const Row = ({ label, value, indent, bold, emphasize }) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', paddingLeft: indent ? 14 : 0 }}>
      <span style={{
        fontSize: emphasize ? 16 : (indent ? 12.5 : 14),
        fontFamily: emphasize ? 'var(--inc-serif, inherit)' : 'inherit',
        color: emphasize ? 'var(--inc-text)' : (indent ? 'var(--inc-textDim)' : 'var(--inc-text)'),
        fontWeight: bold || emphasize ? 700 : 500,
      }}>{label}</span>
      <span style={{
        fontSize: emphasize ? 20 : (indent ? 12.5 : 14),
        color: emphasize ? 'var(--inc-sage)' : (indent ? 'var(--inc-textDim)' : 'var(--inc-text)'),
        fontWeight: bold || emphasize ? 700 : 500,
        fontVariantNumeric: 'tabular-nums',
      }}>{value}</span>
    </div>
  );

  return (
    <B2Card>
      <B2CardHeader
        icon={<RSUIcon color="var(--inc-sage)" />}
        title="Supplemental income"
        subtitle="Taxed at 22% supplemental"
      />
      {bonus > 0 && <Row label={bonusDate ? `Bonus · ${new Date(bonusDate + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}` : 'Bonus'} value={fmtMoney(bonus, { cents: false })} indent />}
      {commission > 0 && <Row label="Commission" value={fmtMoney(commission, { cents: false })} indent />}
      {rsuVesting > 0 && <Row label={`RSU vesting`} value={fmtMoney(rsuVesting, { cents: false })} indent />}

      <div style={{ height: 1, background: 'var(--inc-hairline)', margin: '6px 0' }} />
      <Row label="Federal (22% supplemental)" value={fmtMoney(-fed, { signed: true, cents: false })} indent />
      <Row label="Social Security" value={fmtMoney(-ss, { signed: true, cents: false })} indent />
      <Row label="Medicare" value={fmtMoney(-medicare, { signed: true, cents: false })} indent />

      <div style={{ height: 2, background: 'var(--inc-sageBg)', margin: '12px 0' }} />
      <Row label="Net supplemental" value={fmtMoney(net, { cents: false })} emphasize />

      <div style={{ marginTop: 12, fontSize: 11.5, color: 'var(--inc-textMute)', lineHeight: '16px' }}>
        Estimates value all {CURRENT_TAX_YEAR} vests at today's price. Actual tax withholding happens
        at each vest at that day's price. Excludes state tax, folded into the main state line.
      </div>
    </B2Card>
  );
}

Object.assign(window, { RSUEquityCard, RSUSupplementalCard, RSUIcon });
