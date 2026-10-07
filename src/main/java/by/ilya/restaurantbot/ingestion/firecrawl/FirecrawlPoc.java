package by.ilya.restaurantbot.ingestion.firecrawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Manually invoked developer CLI. Starts no application, database, Telegram or AI runtime. */
public final class FirecrawlPoc {
    private FirecrawlPoc() { }

    public record Target(String brand, String sourceUrl) { }
    public record Result(String brand, RestaurantImportCandidate candidate, String failureCode) { }
    public record Report(String approval, List<Result> results, List<CandidateNormalizer.Duplicate> duplicates) { }

    @FunctionalInterface
    interface Scraper {
        String scrape(String sourceUrl) throws IOException, InterruptedException;
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    static void validate(List<Target> targets) {
        if (targets == null || targets.isEmpty() || targets.size() > 10) {
            throw new IllegalArgumentException("PoC requires 1 to 10 distinct brands");
        }
        var brands = new HashSet<String>();
        var sources = new HashSet<String>();
        for (var target : targets) {
            if (target == null || CandidateNormalizer.normalized(target.brand()).isEmpty()
                    || !brands.add(CandidateNormalizer.normalized(target.brand()))
                    || !sources.add(CandidateNormalizer.source(target.sourceUrl()).toString())) {
                throw new IllegalArgumentException("Distinct brands and source URLs are required");
            }
        }
    }

    static Report run(List<Target> targets, Path output, Scraper scraper, Clock clock)
            throws IOException, InterruptedException {
        validate(targets); // Reject the entire batch before any paid call or filesystem mutation.
        var results = new ArrayList<Result>();
        var extractor = new FirecrawlCandidateExtractor();
        // Fail on existing/unwritable output before spending credits. Never overwrite earlier evidence.
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (var target : targets) {
                RestaurantImportCandidate candidate;
                String failure = null;
                try {
                    var response = scraper.scrape(target.sourceUrl());
                    candidate = extractor.extract(response, target.sourceUrl(), clock.instant());
                    if (candidate.extractionStatus() == RestaurantImportCandidate.ExtractionStatus.INVALID) {
                        failure = "INVALID_RESPONSE";
                    }
                } catch (IOException failed) {
                    candidate = extractor.invalid(target.sourceUrl(), clock.instant());
                    failure = "ACQUISITION_FAILED";
                }
                results.add(new Result(target.brand(), candidate, failure));
            }
            var candidates = results.stream().map(Result::candidate).toList();
            var report = new Report("UNAPPROVED", List.copyOf(results), CandidateNormalizer.duplicates(candidates));
            mapper().writerWithDefaultPrettyPrinter().writeValue(writer, report);
            return report;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: FirecrawlPoc <targets.json> <new-output.json>");
            System.exit(2);
        }
        var key = System.getenv("FIRECRAWL_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("USER_INPUT_REQUIRED: set FIRECRAWL_API_KEY in the local process environment");
            System.exit(2);
        }
        try {
            var targets = mapper().readValue(Files.readString(Path.of(args[0])), new TypeReference<List<Target>>() { });
            var report = run(targets, Path.of(args[1]), new FirecrawlClient(key)::scrape, Clock.systemUTC());
            long usable = report.results().stream()
                    .filter(r -> r.candidate().extractionStatus() != RestaurantImportCandidate.ExtractionStatus.INVALID).count();
            System.out.println("PoC finished: attempted=" + report.results().size() + ", withIdentity=" + usable
                    + ", duplicates=" + report.duplicates().size() + ", approval=UNAPPROVED");
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            System.err.println("PoC interrupted; no retry. Output may be incomplete");
            System.exit(1);
        } catch (IOException | RuntimeException failed) {
            System.err.println("PoC failed; check targets and output path. No exception payload logged");
            System.exit(1);
        }
    }
}
