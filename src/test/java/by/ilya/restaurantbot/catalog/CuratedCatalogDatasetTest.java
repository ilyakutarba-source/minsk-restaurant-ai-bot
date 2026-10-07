package by.ilya.restaurantbot.catalog;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CuratedCatalogDatasetTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ObjectNode tree() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/seed/curated-catalog-v11.json")) {
            return (ObjectNode) mapper.readTree(input);
        }
    }
    private CuratedCatalogDataset read(ObjectNode tree) throws Exception {
        return CuratedCatalogDataset.read(new ByteArrayInputStream(mapper.writeValueAsBytes(tree)));
    }
    @Test void reviewedResourceHasTwentyNewVenuesAndFourteenBrands() throws Exception {
        var data = read(tree());
        assertThat(data.venues()).hasSize(20);
        assertThat(data.venues().stream().map(CuratedCatalogDataset.Venue::name).distinct()).hasSize(14);
        assertThat(data.venues()).allSatisfy(v -> {
            assertThat(v.check().amount()).isPositive();
            assertThat(v.catalogVerifiedAt()).hasToString("2026-10-07");
            assertThat(v.check().verifiedAt()).isEqualTo(v.catalogVerifiedAt());
            assertThat(v.hoursVerifiedAt()).isEqualTo(v.catalogVerifiedAt());
        });
    }
    @ParameterizedTest
    @ValueSource(strings={"seedKey","name","address","active","reviewStatus","cuisines","catalogSource",
            "catalogVerifiedAt","check","hoursSource","hoursVerifiedAt","openingIntervals"})
    void missingAdmissionFieldsAreRejected(String field) throws Exception {
        var data=tree(); ((ObjectNode)data.withArray("venues").get(0)).remove(field);
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"amount","type","source","verifiedAt","explanation"})
    void missingCheckFieldsAreRejected(String field) throws Exception {
        var data=tree(); ((ObjectNode)data.withArray("venues").get(0).get("check")).remove(field);
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void candidatesUnknownFieldsUnknownEnumsAndTrailingDocumentsAreRejected() throws Exception {
        var data=tree(); var venue=(ObjectNode)data.withArray("venues").get(0);
        venue.put("reviewStatus","UNAPPROVED");
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
        venue.put("reviewStatus","MANUALLY_VERIFIED"); venue.put("firecrawlScore",1);
        assertThatThrownBy(() -> read(data)).isInstanceOf(java.io.IOException.class);
        venue.remove("firecrawlScore"); venue.putArray("cuisines").add("UNKNOWN");
        assertThatThrownBy(() -> read(data)).isInstanceOf(java.io.IOException.class);
        var bytes=(mapper.writeValueAsString(tree())+" {}").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> CuratedCatalogDataset.read(new ByteArrayInputStream(bytes)))
                .isInstanceOf(java.io.IOException.class);
    }
    @Test void duplicateNormalizedIdentityAndDuplicateKeysAreRejected() throws Exception {
        var data=tree(); var first=(ObjectNode)data.withArray("venues").get(0);
        var second=first.deepCopy(); second.put("seedKey","different-key"); second.remove("menu");
        second.put("name",first.get("name").asText().toUpperCase(java.util.Locale.ROOT));
        second.put("address",first.get("address").asText().replace(",", "   "));
        data.withArray("venues").add(second);
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
        second.put("name","Other venue"); second.put("seedKey",first.get("seedKey").asText());
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","10.001","100000000.00"})
    void invalidMoneyIsRejected(String amount) throws Exception {
        var data=tree(); ((ObjectNode)data.withArray("venues").get(0).get("check"))
                .put("amount",new java.math.BigDecimal(amount));
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidHoursAndMismatchedMenuAreRejected() throws Exception {
        var data=tree(); var venue=(ObjectNode)data.withArray("venues").get(0);
        ((ObjectNode)venue.withArray("openingIntervals").get(0)).put("closesAt","12:00:00");
        assertThatThrownBy(() -> read(data)).isInstanceOf(IllegalArgumentException.class);
        var invalid=tree(); ((ObjectNode)invalid.withArray("venues").get(0).get("menu")).put("restaurantSeedKey","other");
        assertThatThrownBy(() -> read(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void emptyPartialMenuIsAnAllowedUnavailableMenu() throws Exception {
        var data=tree(); ((ObjectNode)data.withArray("venues").get(0).get("menu")).putArray("items");
        assertThat(read(data).venues().getFirst().menu().items()).isEmpty();
    }
}
