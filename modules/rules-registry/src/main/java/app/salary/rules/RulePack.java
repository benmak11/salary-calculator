package app.salary.rules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class RulePack {
    // Main getters and setters
    private Metadata metadata;
    private Federal federal;
    private Fica fica;
    private Map<String, StateRules> states;
    private IncomeTax incomeTax;
    private NationalInsurance ni;
    private Map<String, StudentLoanRules> studentLoan;

    @Setter
    @Getter
    public static class Metadata {
        private String country;
        private Integer taxYear;
        private String version;
    }

    @Setter
    public static class Federal {
        @Getter
        private Map<String, Double> standardDeductions;
        /** Default brackets (SINGLE / MARRIED). Use bracketsByFilingStatus when available. */
        @Getter
        private List<TaxBracket> brackets;
        /** Per-filing-status bracket overrides (e.g. HEAD_OF_HOUSEHOLD). Falls back to brackets if absent. */
        @Getter
        private Map<String, List<TaxBracket>> bracketsByFilingStatus;
        /** Flat federal withholding rate applied to supplemental / bonus wages (default 0.22). */
        private Double supplementalWithholdingRate = 0.22;
        /** Annual dollar value of one withholding allowance (pre-2020 W-4 only; default 4300). */
        private Double withholdingAllowance = 4300.0;

        public Double getSupplementalWithholdingRate() {
            return supplementalWithholdingRate != null ? supplementalWithholdingRate : 0.22;
        }

        public Double getWithholdingAllowance() {
            return withholdingAllowance != null ? withholdingAllowance : 4300.0;
        }

        /** Returns the appropriate federal brackets for the given filing status. */
        public List<TaxBracket> getBracketsForFilingStatus(String filingStatus) {
            if (bracketsByFilingStatus != null && bracketsByFilingStatus.containsKey(filingStatus)) {
                return bracketsByFilingStatus.get(filingStatus);
            }
            return brackets;
        }
    }

    @Setter
    @Getter
    public static class TaxBracket {
        /**
         * Lower bound: this rate applies to income above this amount. Mirrors how the IRS,
         * HMRC and state revenue departments publish their tables ("22% on income over
         * $50,400"), so a pack can be diffed straight against the source with no
         * off-by-one-bracket translation step.
         */
        private Double over;

        /**
         * Legacy upper-bound form, superseded by {@link #over}.
         *
         * <p>Retained purely so rule packs published to the rule-pack-service before the
         * schema change still deserialize. The mapper wired into {@code HttpRulePackClient}
         * disables {@code FAIL_ON_UNKNOWN_PROPERTIES}, so dropping this field would make a
         * stale remote pack parse into all-null thresholds and silently compute nonsense
         * rather than failing. {@code TaxBracketCalculator} normalizes it away.
         *
         * @deprecated remove once every stored remote pack has been republished with
         *     {@code over}.
         */
        @Deprecated(since = "US-2026.1.0")
        private Double upTo;

        private Double rate;
    }

    @Setter
    @Getter
    public static class Fica {
        private Double ssRate;
        private Double ssWageBase;
        private Double medicareRate;
        private Double additionalMedicareThreshold;
        private Double additionalRate;
    }

    @Setter
    @Getter
    public static class StateRules {
        private List<TaxBracket> brackets;
        private Double local;
    }

    @Setter
    @Getter
    public static class IncomeTax {
        private Double personalAllowance;
        private Double taperStart;
        private Double taperRate;
        private List<TaxBracket> bands;
    }

    @Setter
    @Getter
    public static class NationalInsurance {
        private Double primaryThresholdAnnual;
        private Double upperEarningsLimit;
        private Double mainRate;
        private Double upperRate;
    }

    @Setter
    @Getter
    public static class StudentLoanRules {
        private Double threshold;
        private Double rate;

    }
}
