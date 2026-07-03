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
        assertThrows(RulePackLoadException.class, () -> registry.getRulePack("XX", 2025));
    }

    @Test
    void getRulePack_withInvalidYear_shouldThrowException() {
        assertThrows(RulePackLoadException.class, () -> registry.getRulePack("UK", 1990));
    }

    @Test
    void rulePackLoadException_preservesMessageAndCause() {
        // The cause-carrying constructor guards the IOException path in loadRulePack,
        // which valid classpath rule packs can't trigger — covered directly instead.
        var cause = new java.io.IOException("bad json");
        var ex = new RulePackLoadException("Failed to load rule pack: /rulepacks/UK-2025.json", cause);

        assertEquals("Failed to load rule pack: /rulepacks/UK-2025.json", ex.getMessage());
        assertEquals(cause, ex.getCause());
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

    // ── Phase 3 rule pack additions ──────────────────────────────────────────────

    @Test
    void getRulePack_us2025_shouldBeAtLeastVersion11_0() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        // Phase 3 bumped version from 10.0 → 11.0 (2025 IRS-current brackets + new states)
        assertEquals("US-2025.11.0", rulePack.getMetadata().getVersion());
    }

    @Test
    void getRulePack_us2025_shouldHave2025StandardDeductions() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        // 2025 IRS values (Rev. Proc. 2024-40)
        assertEquals(15750.0, rulePack.getFederal().getStandardDeductions().get("SINGLE"));
        assertEquals(31500.0, rulePack.getFederal().getStandardDeductions().get("MARRIED"));
        assertEquals(23625.0, rulePack.getFederal().getStandardDeductions().get("HEAD_OF_HOUSEHOLD"));
    }

    @Test
    void getRulePack_us2025_shouldHaveMarriedFilingJointlyBrackets() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        var byStatus = rulePack.getFederal().getBracketsByFilingStatus();
        assertNotNull(byStatus);
        assertTrue(byStatus.containsKey("MARRIED"), "MFJ brackets should be present");
        var mfjBrackets = byStatus.get("MARRIED");
        // First bracket: 10% up to $23,850
        assertEquals(23850.0, mfjBrackets.get(0).getUpTo());
        assertEquals(0.10, mfjBrackets.get(0).getRate());
    }

    @Test
    void getRulePack_us2025_shouldHaveHohOverrideBrackets() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        var byStatus = rulePack.getFederal().getBracketsByFilingStatus();
        assertNotNull(byStatus);
        assertTrue(byStatus.containsKey("HEAD_OF_HOUSEHOLD"));
        // First bracket: 10% up to $17,000
        var hohBrackets = byStatus.get("HEAD_OF_HOUSEHOLD");
        assertEquals(17000.0, hohBrackets.get(0).getUpTo());
    }

    @Test
    void getRulePack_us2025_shouldUseRefreshedSsWageBase() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        // Phase 3 bumped to 2025 IRS value
        assertEquals(176100.0, rulePack.getFica().getSsWageBase());
    }

    @Test
    void getRulePack_us2025_shouldIncludeTopTenStates() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        var states = rulePack.getStates();
        // Top-10 (in-scope per migration plan)
        for (String code : new String[]{"CA", "NY", "TX", "FL", "IL", "PA", "OH", "GA", "NC", "MI", "MD"}) {
            assertTrue(states.containsKey(code), code + " should be in the rule pack");
        }
    }

    @Test
    void getRulePack_us2025_noStateIncomeTaxStates_shouldHaveEmptyBrackets() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        assertTrue(rulePack.getStates().get("TX").getBrackets().isEmpty());
        assertTrue(rulePack.getStates().get("FL").getBrackets().isEmpty());
    }

    @Test
    void getRulePack_us2025_flatRateStates_shouldHaveSingleCatchAllBracket() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        // IL flat 4.95%
        var ilBrackets = rulePack.getStates().get("IL").getBrackets();
        assertEquals(1, ilBrackets.size());
        assertEquals(0.0495, ilBrackets.get(0).getRate());
        assertNull(ilBrackets.get(0).getUpTo());

        // PA flat 3.07%
        var paBrackets = rulePack.getStates().get("PA").getBrackets();
        assertEquals(0.0307, paBrackets.get(0).getRate());

        // NC flat 4.5%
        assertEquals(0.045, rulePack.getStates().get("NC").getBrackets().get(0).getRate());

        // MI flat 4.25%
        assertEquals(0.0425, rulePack.getStates().get("MI").getBrackets().get(0).getRate());
    }

    @Test
    void getRulePack_us2025_nyShouldBeProgressiveWithNineBrackets() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        var nyBrackets = rulePack.getStates().get("NY").getBrackets();
        assertEquals(9, nyBrackets.size());
        // Lowest: 4% up to $8,500
        assertEquals(0.04, nyBrackets.get(0).getRate());
        assertEquals(8500.0, nyBrackets.get(0).getUpTo());
        // Top bracket: 10.9% (catch-all)
        assertEquals(0.109, nyBrackets.get(nyBrackets.size() - 1).getRate());
        assertNull(nyBrackets.get(nyBrackets.size() - 1).getUpTo());
    }

    @Test
    void getRulePack_us2025_mdShouldRetainLocalRate() {
        RulePack rulePack = registry.getRulePack("US", 2025);
        // MD's county-level surcharge is captured by the `local` field
        assertEquals(0.032, rulePack.getStates().get("MD").getLocal());
    }
}
