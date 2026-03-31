package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.common.dto.CalculateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/reports")
@Tag(name = "Reports", description = "Report generation endpoints")
public class ReportsController {

    private final CalculationStore calculationStore;

    public ReportsController(CalculationStore calculationStore) {
        this.calculationStore = calculationStore;
    }

    /**
     * PDF payslip generation endpoint.
     *
     * TODO: Integrate with a PDF generation library (e.g. Apache PDFBox, iText) and
     *       upload the generated file to GCS, returning a signed download URL.
     *       Currently returns 202 Accepted with a stub message.
     */
    @PostMapping("/pdf")
    @Operation(summary = "Generate a PDF payslip for a completed calculation")
    public ResponseEntity<Map<String, String>> generatePdf(
            @RequestBody PdfReportRequest request) {

        if (request.getCalculationId() == null || request.getCalculationId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "calculationId is required"));
        }

        Optional<CalculateResponse> stored = calculationStore.find(request.getCalculationId());
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // PDF generation not yet implemented — return 202 Accepted with a placeholder message.
        return ResponseEntity.accepted()
                .body(Map.of(
                    "status", "PENDING",
                    "message", "PDF generation is not yet available. Your report will be emailed when ready.",
                    "calculationId", request.getCalculationId()
                ));
    }

    // ── Inner request DTO (kept here since it is only used by this controller) ─

    public static class PdfReportRequest {
        @NotBlank
        private String calculationId;

        public String getCalculationId() { return calculationId; }
        public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
    }
}
