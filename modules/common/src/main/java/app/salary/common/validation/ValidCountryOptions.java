package app.salary.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CountryOptionsValidator.class)
@Documented
public @interface ValidCountryOptions {
    String message() default "Invalid country-specific options";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
