package by.ilya.restaurantbot.ingestion.firecrawl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static by.ilya.restaurantbot.ingestion.firecrawl.RestaurantImportCandidate.ExtractionStatus.*;

class FirecrawlCandidateTest {
    static final String SOURCE = "https://fixture.example/";
    static final Instant FETCHED = Instant.parse("2026-10-07T09:00:00Z");
    private final FirecrawlCandidateExtractor extractor = new FirecrawlCandidateExtractor();

    static String fixture(String name) throws Exception {
        try (var input = FirecrawlCandidateTest.class.getResourceAsStream("/ingestion/firecrawl/" + name + ".json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void mapsFieldsMenuDecimalPricesAndRawOvernightHours() throws Exception {
        var candidate = extractor.extract(fixture("complete"), SOURCE, FETCHED);
        assertThat(candidate.extractionStatus()).isEqualTo(EXTRACTED);
        assertThat(candidate.name()).isEqualTo("Fixture Bistro");
        assertThat(candidate.address()).isEqualTo("Минск, ул. Тестовая, 1");
        assertThat(candidate.cuisines()).containsExactly("Italian", "European");
        assertThat(candidate.openingHours()).isEqualTo("Пн–Чт 12:00–23:00; Пт–Сб 12:00–02:00; Вс 12:00–22:00");
        assertThat(candidate.menuUrl()).isEqualTo("https://fixture.example/menu.pdf");
        assertThat(candidate.menuItems()).hasSize(2);
        assertThat(candidate.menuItems().getFirst().name()).isEqualTo("Паста с грибами");
        assertThat(candidate.menuItems().getFirst().priceByn()).isEqualByComparingTo("25.50");
        assertThat(candidate.sourceUrl()).isEqualTo(SOURCE);
        assertThat(candidate.fetchedAt()).isEqualTo(FETCHED);
        assertThat(candidate.missingFields()).isEmpty();
        assertThat(FirecrawlPoc.mapper().writeValueAsString(candidate)).doesNotContain("VerifiedAt", "verifiedAt");
    }

    @Test
    void missingOptionalFieldsStayMissingAndIdentityRemainsUsable() throws Exception {
        var candidate = extractor.extract(fixture("partial"), SOURCE, FETCHED);
        assertThat(candidate.extractionStatus()).isEqualTo(PARTIAL);
        assertThat(candidate.missingFields()).containsExactly("cuisines", "openingHours", "menuUrl", "menuItems");
        assertThat(candidate.openingHours()).isNull();
        assertThat(candidate.menuItems()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{broken", "null", "[]", "{}", "{\"success\":true}",
            "{\"success\":\"true\",\"data\":{\"json\":{}}}",
            "{\"success\":true,\"data\":{\"json\":[]}}",
            "{\"success\":true,\"success\":false}",
            "{\"success\":true,\"data\":{\"json\":{}}} {}"})
    void malformedResponseIsInvalidWithoutExposingPayload(String response) throws Exception {
        var candidate = extractor.extract(response, SOURCE, FETCHED);
        assertThat(candidate.extractionStatus()).isEqualTo(INVALID);
        assertThat(candidate.missingFields()).hasSize(6);
        assertThat(candidate.sourceUrl()).isEqualTo(SOURCE);
        assertThat(FirecrawlPoc.mapper().writeValueAsString(candidate)).doesNotContain("broken", "error");
    }

    @Test
    void failedScrapesErrorPagesWarningsAndMissingIdentityAreNeverComplete() throws Exception {
        assertThat(extractor.extract(fixture("failed"), SOURCE, FETCHED).extractionStatus()).isEqualTo(INVALID);
        var root = FirecrawlPoc.mapper().readTree(fixture("complete"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("data").path("metadata")).put("statusCode", 404);
        assertThat(extractor.extract(root.toString(), SOURCE, FETCHED).extractionStatus()).isEqualTo(INVALID);
        root = FirecrawlPoc.mapper().readTree(fixture("complete"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("data")).put("warning", "Synthetic warning");
        assertThat(extractor.extract(root.toString(), SOURCE, FETCHED).extractionStatus()).isEqualTo(INVALID);
        assertThat(extractor.extract(fixture("complete").replace("  Fixture Bistro  ", "..."), SOURCE, FETCHED)
                .missingFields()).contains("name");
        assertThat(extractor.extract(fixture("partial").replace("Минск, ул. Примерная, 2", ""), SOURCE, FETCHED)
                .extractionStatus()).isEqualTo(INVALID);
    }

    @Test
    void dropsCoercedUnknownCurrencyNegativeAndOverprecisePricesWithoutGuessing() throws Exception {
        var root = FirecrawlPoc.mapper().readTree(fixture("complete"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("data").path("json")).set("menuItems",
                FirecrawlPoc.mapper().readTree("""
                [{"name":"String price","priceByn":"20.00","currency":"BYN"},
                 {"name":"No currency","priceByn":20},
                 {"name":"Euro","priceByn":20,"currency":"EUR"},
                 {"name":"Negative","priceByn":-1,"currency":"BYN"},
                 {"name":"Precision","priceByn":1.234,"currency":"BYN"},
                 {"name":"Huge","priceByn":1e100,"currency":"BYN"}]
                """));
        var candidate = extractor.extract(root.toString(), SOURCE, FETCHED);
        assertThat(candidate.extractionStatus()).isEqualTo(PARTIAL);
        assertThat(candidate.menuItems()).isEmpty();
        assertThat(candidate.missingFields()).containsExactly("menuItems");
    }

    @Test
    void doesNotTrustExtractedProvenanceOrVerificationAndRejectsUnsafeMenuLinks() throws Exception {
        var response = fixture("complete").replace("/menu.pdf", "javascript:alert(1)")
                .replace("\"name\": \"  Fixture Bistro  ", "\"catalogVerifiedAt\":\"2026-10-07\",\"name\": \"  Fixture Bistro  ");
        var candidate = extractor.extract(response, SOURCE, FETCHED);
        assertThat(candidate.menuUrl()).isNull();
        assertThat(candidate.extractionStatus()).isEqualTo(PARTIAL);
        assertThat(candidate.sourceUrl()).isEqualTo(SOURCE);
        assertThat(FirecrawlPoc.mapper().writeValueAsString(candidate)).doesNotContain("catalogVerifiedAt");
    }

    @Test
    void oversizedResponseAndWrongFieldTypesCannotBecomeFacts() throws Exception {
        assertThat(extractor.extract("x".repeat(1_000_001), SOURCE, FETCHED).extractionStatus()).isEqualTo(INVALID);
        var candidate = extractor.extract("""
                {"success":true,"data":{"json":{"name":123,"address":[],"cuisines":"Italian",
                "openingHours":false,"menuUrl":42,"menuItems":{}}}}
                """, SOURCE, FETCHED);
        assertThat(candidate.extractionStatus()).isEqualTo(INVALID);
        assertThat(candidate.missingFields()).hasSize(6);
    }

    @Test
    void duplicateIdentityNormalizesPunctuationCaseAndUnicodeWhitespaceButKeepsBranches() throws Exception {
        assertThat(CandidateNormalizer.normalized("  «CAFE»\u00a0\tOne,  ")).isEqualTo("cafe one");
        var first = extractor.extract(fixture("partial"), SOURCE, FETCHED);
        var same = extractor.extract(fixture("partial").replace("Fixture Cafe", "«FIXTURE  CAFE»")
                .replace("Минск, ул. Примерная, 2", "  МИНСК ул Примерная 2 "), SOURCE, FETCHED);
        var branch = extractor.extract(fixture("partial").replace("Примерная, 2", "Примерная, 3"), SOURCE, FETCHED);
        var empty = extractor.invalid(SOURCE, FETCHED);
        assertThat(CandidateNormalizer.duplicates(List.of(first, same, branch, empty, empty)))
                .containsExactly(new CandidateNormalizer.Duplicate(0, 1));
        assertThat(CandidateNormalizer.identity(empty)).isEmpty();
    }
}
