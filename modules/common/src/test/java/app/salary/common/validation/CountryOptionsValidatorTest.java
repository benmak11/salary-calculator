package app.salary.common.validation;

import app.salary.common.constants.Country;
import app.salary.common.constants.FilingStatus;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CountryOptions;
import app.salary.common.dto.CountryOptionsUS;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = LENIENT)
class CountryOptionsValidatorTest {

    private CountryOptionsValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @BeforeEach
    void setUp() {
        validator = new CountryOptionsValidator();
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
        when(nodeBuilder.addConstraintViolation()).thenReturn(context);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void isValid_nullRequest_shouldReturnTrue() {
        assertTrue(validator.isValid(null, context));
        verifyNoInteractions(context);
    }

    @Test
    void isValid_nullCountry_shouldReturnTrue() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(null);

        assertTrue(validator.isValid(request, context));
        verifyNoInteractions(context);
    }

    @Test
    void isValid_ukCountry_shouldReturnTrue() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);

        assertTrue(validator.isValid(request, context));
        verifyNoInteractions(context);
    }

    @Test
    void isValid_usCountryWithoutCountryOptions_shouldReturnFalse() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        request.setCountryOptions(null);

        assertFalse(validator.isValid(request, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
            "US calculations require state and filing status. Please provide countryOptions.US with state and filingStatus fields."
        );
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    void isValid_usCountryWithoutUsOptions_shouldReturnFalse() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        options.setUs(null);
        request.setCountryOptions(options);

        assertFalse(validator.isValid(request, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
            "US calculations require state and filing status. Please provide countryOptions.US with state and filingStatus fields."
        );
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    void isValid_usCountryWithNullState_shouldReturnFalse() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState(null);
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        options.setUs(usOptions);
        request.setCountryOptions(options);

        assertFalse(validator.isValid(request, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("State is required for US tax calculations");
        verify(violationBuilder).addPropertyNode("countryOptions.US.state");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    void isValid_usCountryWithBlankState_shouldReturnFalse() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("   ");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        options.setUs(usOptions);
        request.setCountryOptions(options);

        assertFalse(validator.isValid(request, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("State is required for US tax calculations");
        verify(violationBuilder).addPropertyNode("countryOptions.US.state");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    void isValid_usCountryWithNullFilingStatus_shouldReturnFalse() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(null);
        options.setUs(usOptions);
        request.setCountryOptions(options);

        assertFalse(validator.isValid(request, context));

        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Filing status is required for US tax calculations");
        verify(violationBuilder).addPropertyNode("countryOptions.US.filingStatus");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    void isValid_usCountryWithValidOptions_shouldReturnTrue() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("CA");
        usOptions.setFilingStatus(FilingStatus.SINGLE);
        options.setUs(usOptions);
        request.setCountryOptions(options);

        assertTrue(validator.isValid(request, context));

        verifyNoInteractions(context);
    }

    @Test
    void isValid_usCountryWithValidMarriedOptions_shouldReturnTrue() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.US);
        CountryOptions options = new CountryOptions();
        CountryOptionsUS usOptions = new CountryOptionsUS();
        usOptions.setState("NY");
        usOptions.setFilingStatus(FilingStatus.MARRIED);
        usOptions.setAllowances(2);
        options.setUs(usOptions);
        request.setCountryOptions(options);

        assertTrue(validator.isValid(request, context));

        verifyNoInteractions(context);
    }
}
