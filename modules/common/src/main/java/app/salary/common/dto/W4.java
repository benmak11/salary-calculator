package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@ExcludeFromCodeCoverage
@Schema(description = "US Form W-4 fields (modern 2020+ version unless useOldW4=true)")
public class W4 {
    @Schema(description = "Use 2019-or-earlier W-4 (allowances-based) instead of modern W-4", example = "false")
    private Boolean useOldW4 = false;

    @Schema(description = "Filer is a nonresident alien", example = "false")
    private Boolean nonresidentAlien = false;

    @Min(0)
    @Schema(description = "W-4 step 3 dependents credit dollar amount ($2000 per qualifying child + $500 per other dependent)", example = "0")
    private Double dependentsAmount = 0.0;

    @Min(0)
    @Schema(description = "W-4 step 4(a) — annual non-job income", example = "0")
    private Double otherIncome = 0.0;

    @Min(0)
    @Schema(description = "W-4 step 4(b) — itemized deductions (overrides standard deduction if greater)", example = "0")
    private Double itemizedDeductions = 0.0;

    @Min(0)
    @Schema(description = "W-4 step 4(c) — additional federal withholding PER PAY PERIOD", example = "0")
    private Double additionalWithholding = 0.0;

    @Schema(description = "Exempt from federal income tax withholding", example = "false")
    private Boolean exemptFederal = false;

    @Schema(description = "Exempt from Social Security tax", example = "false")
    private Boolean exemptSocialSecurity = false;

    @Schema(description = "Exempt from Medicare tax (also disables additional Medicare)", example = "false")
    private Boolean exemptMedicare = false;

    public Boolean getUseOldW4() { return useOldW4; }
    public void setUseOldW4(Boolean useOldW4) { this.useOldW4 = useOldW4; }
    public Boolean getNonresidentAlien() { return nonresidentAlien; }
    public void setNonresidentAlien(Boolean nonresidentAlien) { this.nonresidentAlien = nonresidentAlien; }
    public Double getDependentsAmount() { return dependentsAmount; }
    public void setDependentsAmount(Double dependentsAmount) { this.dependentsAmount = dependentsAmount; }
    public Double getOtherIncome() { return otherIncome; }
    public void setOtherIncome(Double otherIncome) { this.otherIncome = otherIncome; }
    public Double getItemizedDeductions() { return itemizedDeductions; }
    public void setItemizedDeductions(Double itemizedDeductions) { this.itemizedDeductions = itemizedDeductions; }
    public Double getAdditionalWithholding() { return additionalWithholding; }
    public void setAdditionalWithholding(Double additionalWithholding) { this.additionalWithholding = additionalWithholding; }
    public Boolean getExemptFederal() { return exemptFederal; }
    public void setExemptFederal(Boolean exemptFederal) { this.exemptFederal = exemptFederal; }
    public Boolean getExemptSocialSecurity() { return exemptSocialSecurity; }
    public void setExemptSocialSecurity(Boolean exemptSocialSecurity) { this.exemptSocialSecurity = exemptSocialSecurity; }
    public Boolean getExemptMedicare() { return exemptMedicare; }
    public void setExemptMedicare(Boolean exemptMedicare) { this.exemptMedicare = exemptMedicare; }
}
