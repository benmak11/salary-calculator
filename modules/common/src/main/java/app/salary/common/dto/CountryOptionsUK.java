package app.salary.common.dto;

public class CountryOptionsUK {
    private String taxCode = "1257L";
    private Boolean scottishResident = false;
    private String niCategory = "A";

    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public Boolean getScottishResident() { return scottishResident; }
    public void setScottishResident(Boolean scottishResident) { this.scottishResident = scottishResident; }
    public String getNiCategory() { return niCategory; }
    public void setNiCategory(String niCategory) { this.niCategory = niCategory; }
}
