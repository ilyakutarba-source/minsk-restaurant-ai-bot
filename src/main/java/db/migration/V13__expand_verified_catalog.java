package db.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import by.ilya.restaurantbot.catalog.CuratedCatalogDataset;
import by.ilya.restaurantbot.catalog.CuratedCatalogImport;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Reviewed local batch, add-only in the Flyway transaction. No HTTP or Spring services. */
public class V13__expand_verified_catalog extends BaseJavaMigration {
    private static final String DATASET = "/db/seed/curated-catalog-v13.json";
    private static final Map<String, String> BRANDS = Map.of(
            "la-scala-nemiga-36", "la-scala",
            "mziuri-pritytskogo-97b", "mziuri",
            "pekaridze-platonova-45", "pekaridze",
            "sorrento-internatsionalnaya-38", "sorrento",
            "da-claudio-karla-marksa-25", "da-claudio",
            "yangtze-svobody-8", "yangtze");

    @Override public Integer getChecksum() {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) throw new IllegalStateException("Missing V13 catalog dataset");
            var checksum = new CRC32();
            var text = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            checksum.update(text.getBytes(StandardCharsets.UTF_8));
            // Explicit membership is immutable migration data too; include it in validation.
            new TreeMap<>(BRANDS).forEach((seed, brand) ->
                    checksum.update((seed + "=" + brand + "\n").getBytes(StandardCharsets.UTF_8)));
            return (int) checksum.getValue();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read V13 catalog dataset", e);
        }
    }

    @Override public void migrate(Context context) throws Exception {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) throw new IllegalStateException("Missing V13 catalog dataset");
            CuratedCatalogImport.apply(context.getConnection(), CuratedCatalogDataset.read(input), BRANDS);
        }
    }
}
