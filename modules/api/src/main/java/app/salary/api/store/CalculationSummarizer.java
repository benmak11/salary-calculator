package app.salary.api.store;

import app.salary.common.constants.Country;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import app.salary.common.dto.LineItem;
import app.salary.common.dto.LineItemCategory;
import app.salary.common.dto.SavedCalculationSummary;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Derives the denormalized {@link SavedCalculationSummary} stored alongside the full
 * request/response, so the List endpoint can render rows without deserializing the
 * full payload.
 */
final class CalculationSummarizer {
    private CalculationSummarizer() {}

    static SavedCalculationSummary summarize(String id,
                                             Instant savedAt,
                                             CalculateRequest request,
                                             CalculateResponse response) {
        SavedCalculationSummary s = new SavedCalculationSummary();
        s.setId(id);
        s.setSavedAt(DateTimeFormatter.ISO_INSTANT.format(savedAt));
        s.setCountry(request.getCountry() == null ? null : request.getCountry().name());
        s.setTaxYear(request.getTaxYear());
        s.setCadence(request.getCadence() == null ? null : request.getCadence().name());

        String stateCode = null;
        String stateName = null;
        if (request.getCountry() == Country.US
                && request.getCountryOptions() != null
                && request.getCountryOptions().getUs() != null) {
            stateCode = request.getCountryOptions().getUs().getState();
            stateName = US_STATE_NAMES.get(stateCode);
        }
        s.setStateCode(stateCode);
        s.setStateName(stateName);
        s.setCurrency(response.getCurrency());

        double gross = orZero(response.getGrossPerCadence());
        double net   = orZero(response.getNetPerCadence());
        s.setTakeHomePerPeriod(net);
        s.setGrossPerPeriod(gross);
        s.setTaxesPerPeriod(sumByCategory(response.getLineItems(),
                LineItemCategory.TAX_FEDERAL, LineItemCategory.TAX_STATE, LineItemCategory.TAX_FICA));
        s.setBenefitsPerPeriod(sumByCategory(response.getLineItems(),
                LineItemCategory.PRE_TAX_BENEFIT, LineItemCategory.RETIREMENT, LineItemCategory.POST_TAX));
        s.setTakeHomePct(gross > 0 ? (net / gross) * 100.0 : 0.0);
        return s;
    }

    private static double orZero(Double d) {
        return d == null ? 0.0 : d;
    }

    private static double sumByCategory(List<LineItem> items, LineItemCategory... categories) {
        if (items == null || items.isEmpty()) return 0.0;
        double sum = 0.0;
        for (LineItem item : items) {
            for (LineItemCategory c : categories) {
                if (item.getCategory() == c) {
                    sum += orZero(item.getAmount());
                    break;
                }
            }
        }
        return sum;
    }

    // Mirrors the list in CalculateController.getUsStates(); kept in sync intentionally
    // — there's nowhere obvious in the calculator module to hoist this without a wider refactor.
    private static final Map<String, String> US_STATE_NAMES = Map.<String, String>ofEntries(
            Map.entry("AL", "Alabama"), Map.entry("AK", "Alaska"),
            Map.entry("AZ", "Arizona"), Map.entry("AR", "Arkansas"),
            Map.entry("CA", "California"), Map.entry("CO", "Colorado"),
            Map.entry("CT", "Connecticut"), Map.entry("DE", "Delaware"),
            Map.entry("FL", "Florida"), Map.entry("GA", "Georgia"),
            Map.entry("HI", "Hawaii"), Map.entry("ID", "Idaho"),
            Map.entry("IL", "Illinois"), Map.entry("IN", "Indiana"),
            Map.entry("IA", "Iowa"), Map.entry("KS", "Kansas"),
            Map.entry("KY", "Kentucky"), Map.entry("LA", "Louisiana"),
            Map.entry("ME", "Maine"), Map.entry("MD", "Maryland"),
            Map.entry("MA", "Massachusetts"), Map.entry("MI", "Michigan"),
            Map.entry("MN", "Minnesota"), Map.entry("MS", "Mississippi"),
            Map.entry("MO", "Missouri"), Map.entry("MT", "Montana"),
            Map.entry("NE", "Nebraska"), Map.entry("NV", "Nevada"),
            Map.entry("NH", "New Hampshire"), Map.entry("NJ", "New Jersey"),
            Map.entry("NM", "New Mexico"), Map.entry("NY", "New York"),
            Map.entry("NC", "North Carolina"), Map.entry("ND", "North Dakota"),
            Map.entry("OH", "Ohio"), Map.entry("OK", "Oklahoma"),
            Map.entry("OR", "Oregon"), Map.entry("PA", "Pennsylvania"),
            Map.entry("RI", "Rhode Island"), Map.entry("SC", "South Carolina"),
            Map.entry("SD", "South Dakota"), Map.entry("TN", "Tennessee"),
            Map.entry("TX", "Texas"), Map.entry("UT", "Utah"),
            Map.entry("VT", "Vermont"), Map.entry("VA", "Virginia"),
            Map.entry("WA", "Washington"), Map.entry("WV", "West Virginia"),
            Map.entry("WI", "Wisconsin"), Map.entry("WY", "Wyoming"),
            Map.entry("DC", "District of Columbia")
    );
}
