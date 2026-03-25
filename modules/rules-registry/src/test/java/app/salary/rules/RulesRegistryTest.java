package app.salary.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RulesRegistryTest {

    private RulesRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RulesRegistry();
    }

    @Test
    void getRulePack_withValidCountryAndYear_shouldReturnRulePack() {
        RulePack rulePack = registry.getRulePack("UK", 2025);

        assertNotNull(rulePack);
        assertNotNull(rulePack.getMetadata());
        assertEquals("UK", rulePack.getMetadata().getCountry());
        assertEquals(2025, rulePack.getMetadata().getTaxYear());
    }

    @Test
    void getRulePack_withUSCountryAndYear_shouldReturnRulePack() {
        RulePack rulePack = registry.getRulePack("US", 2025);

        assertNotNull(rulePack);
        assertNotNull(rulePack.getMetadata());
        assertEquals("US", rulePack.getMetadata().getCountry());
    }

    @Test
    void getRulePack_calledTwice_shouldReturnCachedResult() {
        RulePack first = registry.getRulePack("UK", 2025);
        RulePack second = registry.getRulePack("UK", 2025);

        assertSame(first, second);
    }

    @Test
    void getRulePack_withDifferentCountries_shouldReturnDifferentRulePacks() {
        RulePack ukPack = registry.getRulePack("UK", 2025);
        RulePack usPack = registry.getRulePack("US", 2025);

        assertNotEquals(ukPack, usPack);
    }

    @Test
    void getRulePack_withInvalidCountry_shouldThrowException() {
        assertThrows(RuntimeException.class, () -> registry.getRulePack("XX", 2025));
    }

    @Test
    void getRulePack_withInvalidYear_shouldThrowException() {
        assertThrows(RuntimeException.class, () -> registry.getRulePack("UK", 1990));
    }

    @Test
    void clearCache_shouldClearAllCachedRulePacks() {
        RulePack first = registry.getRulePack("UK", 2025);
        registry.clearCache();
        RulePack second = registry.getRulePack("UK", 2025);

        // After clearing cache, a new instance should be loaded
        // They should be equal in content but not the same instance
        assertNotSame(first, second);
    }

    @Test
    void getRulePack_ukRulePack_shouldHaveIncomeTaxRules() {
        RulePack rulePack = registry.getRulePack("UK", 2025);

        assertNotNull(rulePack.getIncomeTax());
        assertNotNull(rulePack.getIncomeTax().getPersonalAllowance());
        assertNotNull(rulePack.getIncomeTax().getBands());
        assertFalse(rulePack.getIncomeTax().getBands().isEmpty());
    }

    @Test
    void getRulePack_ukRulePack_shouldHaveNationalInsuranceRules() {
        RulePack rulePack = registry.getRulePack("UK", 2025);

        assertNotNull(rulePack.getNi());
        assertNotNull(rulePack.getNi().getPrimaryThresholdAnnual());
        assertNotNull(rulePack.getNi().getMainRate());
    }

    @Test
    void getRulePack_usRulePack_shouldHaveFederalRules() {
        RulePack rulePack = registry.getRulePack("US", 2025);

        assertNotNull(rulePack.getFederal());
        assertNotNull(rulePack.getFederal().getBrackets());
        assertFalse(rulePack.getFederal().getBrackets().isEmpty());
    }

    @Test
    void getRulePack_usRulePack_shouldHaveFicaRules() {
        RulePack rulePack = registry.getRulePack("US", 2025);

        assertNotNull(rulePack.getFica());
        assertNotNull(rulePack.getFica().getSsRate());
        assertNotNull(rulePack.getFica().getMedicareRate());
    }
}
