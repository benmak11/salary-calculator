package app.salary.api.migration;

import app.salary.api.store.FirestoreAccountDirectory;
import app.salary.api.store.FirestoreBudgetStore;
import app.salary.api.store.FirestoreCalculationStore;
import app.salary.api.store.FirestoreGrantStore;
import app.salary.api.store.FirestoreUserDirectory;
import app.salary.api.store.StoreLayout;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Shell entry point for the B-1b Phase 2 backfill. Runs against whatever project the
 * ambient gcloud credentials resolve to.
 *
 * <pre>
 *   ./gradlew :modules:api:backfill                      # DRY RUN — the default
 *   ./gradlew :modules:api:backfill --args='--apply'     # write
 *   ./gradlew :modules:api:backfill --args='--apply --after SUB'   # resume
 * </pre>
 *
 * <p>Progress goes to {@code backfill-progress.tsv} in the working directory, one line per
 * user: {@code sub, accountId, created, calculations, grants, budget}. That file is the
 * resume cursor <em>and</em> the {@code sub -> accountId} mapping the reverse backfill needs
 * if the migration is ever rolled back after Phase 5. Keep it.
 *
 * <p>Deliberately not an HTTP endpoint and not wired into {@code Main}: a migration that
 * can be triggered by a request can be triggered twice by a retry.
 */
public final class LegacyBackfillMain {
    private static final Logger log = LoggerFactory.getLogger(LegacyBackfillMain.class);

    private static final Path PROGRESS = Path.of("backfill-progress.tsv");

    private LegacyBackfillMain() {
    }

    public static void main(String[] args) throws IOException {
        List<String> argv = List.of(args);
        boolean apply = argv.contains("--apply");
        String after = null;
        int at = argv.indexOf("--after");
        if (at >= 0 && at + 1 < argv.size()) {
            after = argv.get(at + 1);
        }

        String projectId = System.getenv().getOrDefault("GCP_PROJECT_ID", "salary-calculator-prod");
        Firestore firestore = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(projectId).build().getService();
        ObjectMapper mapper = objectMapper();

        log.info("backfill {} against project {}{}",
                apply ? "APPLYING" : "DRY RUN", projectId,
                after == null ? "" : " (resuming after " + after + ")");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(PROGRESS, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            LegacyBackfill.Progress progress = (sub, accountId, created, calcs, grants, budget) -> {
                out.printf("%s\t%s\t%s\t%d\t%d\t%s%n", sub, accountId, created, calcs, grants, budget);
                out.flush();
            };

            LegacyBackfill backfill = new LegacyBackfill(
                    new FirestoreUserDirectory(firestore),
                    new FirestoreAccountDirectory(firestore),
                    stores(firestore, mapper, StoreLayout.SUB_KEYED),
                    stores(firestore, mapper, StoreLayout.ACCOUNT_KEYED),
                    !apply, progress);

            LegacyBackfill.Report report = backfill.run(after);
            log.info("backfill {}: users={} accountsCreated={} calculations={} grants={} budgets={} lastSub={}",
                    apply ? "DONE" : "DRY RUN DONE", report.users(), report.accountsCreated(),
                    report.calculations(), report.grants(), report.budgets(), report.lastSub());
            log.info("progress + sub->accountId mapping written to {}", PROGRESS.toAbsolutePath());
        }
    }

    private static LegacyBackfill.Stores stores(Firestore firestore, ObjectMapper mapper, StoreLayout layout) {
        return new LegacyBackfill.Stores(
                new FirestoreCalculationStore(firestore, mapper, layout),
                new FirestoreGrantStore(firestore, mapper, layout),
                new FirestoreBudgetStore(firestore, mapper, layout));
    }

    /** Same settings as {@code Main.buildObjectMapper}, so stored payloads round-trip identically. */
    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
