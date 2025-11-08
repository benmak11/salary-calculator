package app.salary.common.validation;

import app.salary.common.constants.Country;
import app.salary.common.dto.CalculateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CountryOptionsValidator implements ConstraintValidator<ValidCountryOptions, CalculateRequest> {

    @Override
    public boolean isValid(CalculateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getCountry() == null) {
            return true; // Let @NotNull handle null checks
        }

        if (request.getCountry() == Country.US) {
            // For US, we need country options with US-specific fields
            if (request.getCountryOptions() == null || request.getCountryOptions().getUs() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "US calculations require state and filing status. Please provide countryOptions.US with state and filingStatus fields."
                ).addConstraintViolation();
                return false;
            }

            // Check that state and filingStatus are provided
            var usOptions = request.getCountryOptions().getUs();
            if (usOptions.getState() == null || usOptions.getState().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "State is required for US tax calculations"
                ).addPropertyNode("countryOptions.US.state").addConstraintViolation();
                return false;
            }

            if (usOptions.getFilingStatus() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "Filing status is required for US tax calculations"
                ).addPropertyNode("countryOptions.US.filingStatus").addConstraintViolation();
                return false;
            }
        }

        // UK has sensible defaults, so no validation needed
        return true;
    }
}
