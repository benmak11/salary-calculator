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
    void getRulePack_calledTwice_shouldReturnEquivalentRulePacks() {
        // Caching is handled by Spring's @Cacheable proxy — in a plain unit test
        // (no Spring context) each call reloads from classpath, so instances differ.
        // We verify that both calls return the same logical content.
        RulePack first = registry.getRulePack("UK", 2025);
        RulePack second = registry.getRulePack("UK", 2025);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getMetadata().getCountry(), second.getMetadata().getCountry());
        assertEquals(first.getMetadata().getTaxYear(), second.getMetadata().getTaxYear());
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
    void clearCache_shouldNotThrowAndRegistryShouldRemainUsable() {
        registry.getRulePack("UK", 2025);
        assertDoesNotThrow(() -> registry.clearCache());
        // Registry should still be usable after a cache eviction
        assertNotNull(registry.getRulePack("UK", 2025));
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
