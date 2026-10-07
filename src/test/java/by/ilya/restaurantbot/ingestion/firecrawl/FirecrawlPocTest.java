package by.ilya.restaurantbot.ingestion.firecrawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.*;

class FirecrawlPocTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(FirecrawlCandidateTest.FETCHED, ZoneOffset.UTC);

    @Test
    void tenSyntheticBrandsRunOnceEachAndWriteUnapprovedJsonOnly() throws Exception {
        var targets = IntStream.range(0, 10).mapToObj(i -> new FirecrawlPoc.Target("Fixture " + i,
                "https://brand" + i + ".example/")).toList();
        var calls = new AtomicInteger();
        var output = directory.resolve("candidates.json");
        var fixture = FirecrawlCandidateTest.fixture("complete");
        var report = FirecrawlPoc.run(targets, output, source -> {
            int i = calls.getAndIncrement();
            return fixture.replace("Fixture Bistro", "Fixture " + i);
        }, clock);
        assertThat(calls.get()).isEqualTo(10);
        assertThat(report.results()).hasSize(10);
        assertThat(report.results()).allSatisfy(result -> {
            assertThat(result.candidate().extractionStatus()).isEqualTo(RestaurantImportCandidate.ExtractionStatus.EXTRACTED);
            assertThat(result.candidate().fetchedAt()).isEqualTo(FirecrawlCandidateTest.FETCHED);
        });
        assertThat(report.duplicates()).isEmpty();
        assertThat(Files.readString(output)).contains("UNAPPROVED").doesNotContain("verifiedAt", "VerifiedAt");
    }

    @Test
    void failuresStayInvalidAndNeverCopyExceptionOrProviderErrorToOutput() throws Exception {
        var output = directory.resolve("candidates.json");
        var report = FirecrawlPoc.run(List.of(new FirecrawlPoc.Target("Fixture", FirecrawlCandidateTest.SOURCE)),
                output, source -> { throw new IOException("sensitive-provider-body"); }, clock);
        assertThat(report.results().getFirst().failureCode()).isEqualTo("ACQUISITION_FAILED");
        assertThat(report.results().getFirst().candidate().extractionStatus())
                .isEqualTo(RestaurantImportCandidate.ExtractionStatus.INVALID);
        assertThat(Files.readString(output)).doesNotContain("sensitive-provider-body");
    }

    @Test
    void rejectsOverLimitRepeatedBrandsAndRepeatedSourcesBeforeCalls() {
        var overLimit = IntStream.range(0, 11).mapToObj(i -> new FirecrawlPoc.Target("Fixture " + i,
                "https://brand" + i + ".example/")).toList();
        assertThatThrownBy(() -> FirecrawlPoc.validate(overLimit)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirecrawlPoc.validate(List.of(
                new FirecrawlPoc.Target("Fixture", "https://a.example/"),
                new FirecrawlPoc.Target(" «FIXTURE» ", "https://b.example/"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirecrawlPoc.validate(List.of(
                new FirecrawlPoc.Target("A", "https://a.example/"),
                new FirecrawlPoc.Target("B", "https://a.example/"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirecrawlPoc.validate(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existingOutputFailsBeforeSpendingCredits() throws Exception {
        var output = directory.resolve("candidates.json");
        Files.writeString(output, "earlier evidence");
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> FirecrawlPoc.run(List.of(new FirecrawlPoc.Target("Fixture", FirecrawlCandidateTest.SOURCE)),
                output, source -> { calls.incrementAndGet(); return "{}"; }, clock)).isInstanceOf(IOException.class);
        assertThat(calls.get()).isZero();
        assertThat(Files.readString(output)).isEqualTo("earlier evidence");
    }

    @Test
    void interruptionStopsBatchImmediatelyWithoutRetry() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> FirecrawlPoc.run(List.of(
                new FirecrawlPoc.Target("A", "https://a.example/"),
                new FirecrawlPoc.Target("B", "https://b.example/")), directory.resolve("interrupted.json"),
                source -> { calls.incrementAndGet(); throw new InterruptedException(); }, clock))
                .isInstanceOf(InterruptedException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
