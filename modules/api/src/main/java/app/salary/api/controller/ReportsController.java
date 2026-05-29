package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.Optional;

public class ReportsController {

    private final CalculationStore calculationStore;

    public ReportsController(CalculationStore calculationStore) {
        this.calculationStore = calculationStore;
    }

    public void register(RoutesConfig routes) {
        routes.post("/v1/reports/pdf", this::generatePdf);
    }

    /**
     * PDF payslip generation endpoint.
     *
     * TODO: Integrate with a PDF generation library (e.g. Apache PDFBox, iText) and
     *       upload the generated file to GCS, returning a signed download URL.
     */
    private void generatePdf(Context ctx) {
        PdfReportRequest request = ctx.bodyAsClass(PdfReportRequest.class);

        if (request.getCalculationId() == null || request.getCalculationId().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", "calculationId is required"));
            return;
        }

        Optional<CalculateResponse> stored = calculationStore.find(request.getCalculationId());
        if (stored.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        ctx.status(HttpStatus.ACCEPTED).json(Map.of(
                "status",        "PENDING",
                "message",       "PDF generation is not yet available. Your report will be emailed when ready.",
                "calculationId", request.getCalculationId()
        ));
    }

    public static class PdfReportRequest {
        @NotBlank
        private String calculationId;

        public String getCalculationId() { return calculationId; }
        public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
    }
}
