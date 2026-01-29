package app.salary.calculator.engine;

import app.salary.common.dto.Explanation;
import app.salary.common.dto.LineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalculationResultTest {

    private CalculationResult result;

    @BeforeEach
    void setUp() {
        result = new CalculationResult();
    }

    @Test
    void newCalculationResult_shouldHaveEmptyLineItems() {
        assertNotNull(result.getLineItems());
        assertTrue(result.getLineItems().isEmpty());
    }

    @Test
    void newCalculationResult_shouldHaveEmptyExplanations() {
        assertNotNull(result.getExplanations());
        assertTrue(result.getExplanations().isEmpty());
    }

    @Test
    void setGrossAnnual_shouldStoreValue() {
        result.setGrossAnnual(50000.0);
        assertEquals(50000.0, result.getGrossAnnual(), 0.01);
    }

    @Test
    void setNetAnnual_shouldStoreValue() {
        result.setNetAnnual(40000.0);
        assertEquals(40000.0, result.getNetAnnual(), 0.01);
    }

    @Test
    void setCurrency_shouldStoreValue() {
        result.setCurrency("GBP");
        assertEquals("GBP", result.getCurrency());
    }

    @Test
    void setRulePackVersion_shouldStoreValue() {
        result.setRulePackVersion("UK-2025.1.0");
        assertEquals("UK-2025.1.0", result.getRulePackVersion());
    }

    @Test
    void addLineItem_shouldAddToList() {
        result.addLineItem("Income Tax", 5000.0);

        assertEquals(1, result.getLineItems().size());
        assertEquals("Income Tax", result.getLineItems().get(0).getName());
        assertEquals(5000.0, result.getLineItems().get(0).getAmount(), 0.01);
    }

    @Test
    void addLineItem_multipleItems_shouldAddAll() {
        result.addLineItem("Income Tax", 5000.0);
        result.addLineItem("National Insurance", 3000.0);
        result.addLineItem("Student Loan", 1000.0);

        assertEquals(3, result.getLineItems().size());
    }

    @Test
    void addExplanation_shouldAddToList() {
        result.addExplanation("tax_code", "Using tax code 1257L");

        assertEquals(1, result.getExplanations().size());
        assertEquals("tax_code", result.getExplanations().get(0).getId());
        assertEquals("Using tax code 1257L", result.getExplanations().get(0).getText());
    }

    @Test
    void addExplanation_multipleExplanations_shouldAddAll() {
        result.addExplanation("tax_code", "Using tax code 1257L");
        result.addExplanation("personal_allowance", "Full personal allowance applied");
        result.addExplanation("ni_rate", "Standard NI rate applied");

        assertEquals(3, result.getExplanations().size());
    }

    @Test
    void setLineItems_shouldReplaceList() {
        result.addLineItem("Old Item", 1000.0);

        List<LineItem> newItems = new ArrayList<>();
        newItems.add(new LineItem("New Item 1", 2000.0));
        newItems.add(new LineItem("New Item 2", 3000.0));

        result.setLineItems(newItems);

        assertEquals(2, result.getLineItems().size());
        assertEquals("New Item 1", result.getLineItems().get(0).getName());
    }

    @Test
    void setExplanations_shouldReplaceList() {
        result.addExplanation("old_id", "Old explanation");

        List<Explanation> newExplanations = new ArrayList<>();
        newExplanations.add(new Explanation("new_id_1", "New explanation 1"));
        newExplanations.add(new Explanation("new_id_2", "New explanation 2"));

        result.setExplanations(newExplanations);

        assertEquals(2, result.getExplanations().size());
        assertEquals("new_id_1", result.getExplanations().get(0).getId());
    }

    @Test
    void nullValues_shouldBeAllowed() {
        result.setGrossAnnual(null);
        result.setNetAnnual(null);
        result.setCurrency(null);
        result.setRulePackVersion(null);

        assertNull(result.getGrossAnnual());
        assertNull(result.getNetAnnual());
        assertNull(result.getCurrency());
        assertNull(result.getRulePackVersion());
    }
}
