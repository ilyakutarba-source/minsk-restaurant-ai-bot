package db.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import by.ilya.restaurantbot.catalog.CuratedCatalogDataset;
import by.ilya.restaurantbot.catalog.CuratedCatalogImport;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Immutable, manually verified local data. Flyway supplies a transaction; no network calls. */
public class V11__expand_curated_catalog extends BaseJavaMigration {
    private static final String DATASET = "/db/seed/curated-catalog-v11.json";

    @Override public Integer getChecksum() {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) throw new IllegalStateException("Missing curated catalog dataset");
            var checksum = new CRC32();
            var text = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            checksum.update(text.getBytes(StandardCharsets.UTF_8));
            return (int) checksum.getValue();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read curated catalog dataset", e);
        }
    }

    @Override public void migrate(Context context) throws Exception {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) throw new IllegalStateException("Missing curated catalog dataset");
            CuratedCatalogImport.apply(context.getConnection(), CuratedCatalogDataset.read(input));
        }
    }
}
