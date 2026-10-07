package by.ilya.restaurantbot.ingestion.firecrawl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static by.ilya.restaurantbot.ingestion.firecrawl.RestaurantImportCandidate.ExtractionStatus.*;

/** Parses the documented v2 scrape data.json envelope, with no DB or Spring dependencies. */
public final class FirecrawlCandidateExtractor {
    static final int MAX_RESPONSE_BYTES = 1_000_000;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public RestaurantImportCandidate extract(String response, String sourceUrl, Instant fetchedAt) {
        CandidateNormalizer.source(sourceUrl);
        if (fetchedAt == null) throw new IllegalArgumentException("Fetch timestamp is required");
        try {
            if (response == null || response.length() > MAX_RESPONSE_BYTES) return invalid(sourceUrl, fetchedAt);
            var root = mapper.readTree(response);
            if (root == null || !root.path("success").isBoolean() || !root.path("success").booleanValue()) {
                return invalid(sourceUrl, fetchedAt);
            }
            var data = root.path("data");
            var status = data.path("metadata").path("statusCode");
            if ((!status.isMissingNode() && (!status.isIntegralNumber() || status.intValue() < 200
                    || status.intValue() >= 300)) || data.hasNonNull("warning")
                    || data.path("metadata").hasNonNull("error")) return invalid(sourceUrl, fetchedAt);
            var json = data.path("json");
            if (!json.isObject()) return invalid(sourceUrl, fetchedAt);
            var name = text(json.path("name"), 200);
            var address = text(json.path("address"), 300);
            var cuisines = new ArrayList<String>();
            if (json.path("cuisines").isArray()) {
                for (var cuisine : json.path("cuisines")) {
                    var clean = text(cuisine, 100);
                    if (clean != null && !cuisines.contains(clean) && cuisines.size() < 10) cuisines.add(clean);
                }
            }
            var hours = text(json.path("openingHours"), 2000);
            var menuUrl = menuUrl(json.path("menuUrl"), sourceUrl);
            var items = new ArrayList<RestaurantImportCandidate.MenuItem>();
            if (json.path("menuItems").isArray()) {
                for (var item : json.path("menuItems")) {
                    var itemName = text(item.path("name"), 200);
                    var price = price(item.path("priceByn"));
                    // No currency inference, conversion, rounding or derived restaurant check.
                    if (itemName != null && price != null && "BYN".equals(text(item.path("currency"), 3))
                            && items.size() < 10) items.add(new RestaurantImportCandidate.MenuItem(itemName, price));
                }
            }
            var missing = missing(name, address, cuisines, hours, menuUrl, items);
            var extractionStatus = CandidateNormalizer.normalized(name).isEmpty()
                    || CandidateNormalizer.normalized(address).isEmpty() ? INVALID
                    : missing.isEmpty() ? EXTRACTED : PARTIAL;
            return new RestaurantImportCandidate(name, address, cuisines, hours, menuUrl, items,
                    sourceUrl, fetchedAt, extractionStatus, missing);
        } catch (java.io.IOException | IllegalArgumentException ignored) {
            // Never expose provider body, parser snippets or exception causes to logs/evidence.
            return invalid(sourceUrl, fetchedAt);
        }
    }

    public RestaurantImportCandidate invalid(String sourceUrl, Instant fetchedAt) {
        return new RestaurantImportCandidate(null, null, List.of(), null, null, List.of(), sourceUrl,
                fetchedAt, INVALID, List.of("name", "address", "cuisines", "openingHours", "menuUrl", "menuItems"));
    }

    private static String text(JsonNode node, int max) {
        if (!node.isTextual()) return null;
        var clean = CandidateNormalizer.text(node.textValue());
        return clean == null || clean.length() > max ? null : clean;
    }

    private static String menuUrl(JsonNode node, String source) {
        var value = text(node, 2000);
        if (value == null) return null;
        try {
            return CandidateNormalizer.source(CandidateNormalizer.source(source).resolve(value).toString()).toString();
        } catch (IllegalArgumentException ignored) { return null; }
    }

    private static BigDecimal price(JsonNode node) {
        if (!node.isNumber()) return null;
        var price = node.decimalValue();
        return price.signum() > 0 && price.scale() <= 2 && price.precision() - price.scale() <= 8 ? price : null;
    }

    private static List<String> missing(String name, String address, List<String> cuisines, String hours,
                                        String menuUrl, List<RestaurantImportCandidate.MenuItem> items) {
        var missing = new ArrayList<String>();
        if (CandidateNormalizer.normalized(name).isEmpty()) missing.add("name");
        if (CandidateNormalizer.normalized(address).isEmpty()) missing.add("address");
        if (cuisines.isEmpty()) missing.add("cuisines");
        if (hours == null) missing.add("openingHours");
        if (menuUrl == null) missing.add("menuUrl");
        if (items.isEmpty()) missing.add("menuItems");
        return missing;
    }
}
