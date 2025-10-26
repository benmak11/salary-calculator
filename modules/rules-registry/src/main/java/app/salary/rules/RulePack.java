package app.salary.rules;

import java.util.List;
import java.util.Map;

public class RulePack {
    private Metadata metadata;
    private Federal federal;
    private Fica fica;
    private Map<String, StateRules> states;
    private IncomeTax incomeTax;
    private NationalInsurance ni;
    private Map<String, StudentLoanRules> studentLoan;

    public static class Metadata {
        private String country;
        private Integer taxYear;
        private String version;

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country;}
        public Integer getTaxYear() { return taxYear; }
        public void setTaxYear(Integer taxYear) { this.taxYear = taxYear; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public static class Federal {
        private Map<String, Double> standardDeductions;
        private List<TaxBracket> brackets;

        public Map<String, Double> getStandardDeductions() { return standardDeductions; }
        public void setStandardDeductions(Map<String, Double> standardDeductions) { this.standardDeductions = standardDeductions;}
        public List<TaxBracket> getBrackets() { return brackets; }
        public void setBrackets(List<TaxBracket> brackets) { this.brackets = brackets; }
    }

    public static class TaxBracket {
        private Double upTo;
        private Double rate;

        public Double getUpTo() { return upTo; }
        public void setUpTo(Double upTo) { this.upTo = upTo; }
        public Double getRate() { return rate; }
        public void setRate(Double rate) { this.rate = rate; }
    }

    public static class Fica {
        private Double ssRate;
        private Double ssWageBase;
        private Double medicareRate;
        private Double additionalMedicareThreshold;
        private Double additionalRate;

        public Double getSsRate() { return ssRate; }
        public void setSsRate(Double ssRate) { this.ssRate = ssRate; }
        public Double getSsWageBase() { return ssWageBase; }
        public void setSsWageBase(Double ssWageBase) { this.ssWageBase = ssWageBase; }
        public Double getMedicareRate() { return medicareRate; }
        public void setMedicareRate(Double medicareRate) { this.medicareRate = medicareRate; }
        public Double getAdditionalMedicareThreshold() { return additionalMedicareThreshold; }
        public void setAdditionalMedicareThreshold(Double threshold) {
            this.additionalMedicareThreshold = threshold;
        }
        public Double getAdditionalRate() { return additionalRate; }
        public void setAdditionalRate(Double additionalRate) { this.additionalRate = additionalRate; }
    }

    public static class StateRules {
        private List<TaxBracket> brackets;
        private Double local;

        public List<TaxBracket> getBrackets() { return brackets; }
        public void setBrackets(List<TaxBracket> brackets) { this.brackets = brackets; }
        public Double getLocal() { return local; }
        public void setLocal(Double local) { this.local = local; }
    }

    public static class IncomeTax {
        private Double personalAllowance;
        private Double taperStart;
        private Double taperRate;
        private List<TaxBracket> bands;

        public Double getPersonalAllowance() { return personalAllowance; }
        public void setPersonalAllowance(Double personalAllowance) {
            this.personalAllowance = personalAllowance;
        }
        public Double getTaperStart() { return taperStart; }
        public void setTaperStart(Double taperStart) { this.taperStart = taperStart; }
        public Double getTaperRate() { return taperRate; }
        public void setTaperRate(Double taperRate) { this.taperRate = taperRate; }
        public List<TaxBracket> getBands() { return bands; }
        public void setBands(List<TaxBracket> bands) { this.bands = bands; }
    }

    public static class NationalInsurance {
        private Double primaryThresholdAnnual;
        private Double upperEarningsLimit;
        private Double mainRate;
        private Double upperRate;

        public Double getPrimaryThresholdAnnual() { return primaryThresholdAnnual; }
        public void setPrimaryThresholdAnnual(Double primaryThresholdAnnual) {
            this.primaryThresholdAnnual = primaryThresholdAnnual;
        }
        public Double getUpperEarningsLimit() { return upperEarningsLimit; }
        public void setUpperEarningsLimit(Double upperEarningsLimit) {
            this.upperEarningsLimit = upperEarningsLimit;
        }
        public Double getMainRate() { return mainRate; }
        public void setMainRate(Double mainRate) { this.mainRate = mainRate; }
        public Double getUpperRate() { return upperRate; }
        public void setUpperRate(Double upperRate) { this.upperRate = upperRate; }
    }

    public static class StudentLoanRules {
        private Double threshold;
        private Double rate;

        public Double getThreshold() { return threshold; }
        public void setThreshold(Double threshold) { this.threshold = threshold; }
        public Double getRate() { return rate; }
        public void setRate(Double rate) { this.rate = rate; }
    }

    // Main getters and setters
    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
    public Federal getFederal() { return federal; }
    public void setFederal(Federal federal) { this.federal = federal; }
    public Fica getFica() { return fica; }
    public void setFica(Fica fica) { this.fica = fica; }
    public Map<String, StateRules> getStates() { return states; }
    public void setStates(Map<String, StateRules> states) { this.states = states; }
    public IncomeTax getIncomeTax() { return incomeTax; }
    public void setIncomeTax(IncomeTax incomeTax) { this.incomeTax = incomeTax; }
    public NationalInsurance getNi() { return ni; }
    public void setNi(NationalInsurance ni) { this.ni = ni; }
    public Map<String, StudentLoanRules> getStudentLoan() { return studentLoan; }
    public void setStudentLoan(Map<String, StudentLoanRules> studentLoan) {
        this.studentLoan = studentLoan;
    }


}
