package db.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import by.ilya.restaurantbot.catalog.MenuDataset;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Applies the immutable local dataset without network calls or application services. */
public class V4__seed_partial_menu extends BaseJavaMigration {
    private static final String DATASET = "/db/seed/partial-menu-v4.json";

    @Override
    public Integer getChecksum() {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) {
                throw new IllegalStateException("Missing initial menu dataset");
            }
            var checksum = new CRC32();
            // Text checkouts may use CRLF; the same dataset must validate on every OS.
            var text = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            checksum.update(text.getBytes(StandardCharsets.UTF_8));
            return (int) checksum.getValue();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read initial menu dataset", e);
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        try (var input = getClass().getResourceAsStream(DATASET)) {
            if (input == null) {
                throw new IllegalStateException("Missing initial menu dataset");
            }
            var dataset = MenuDataset.read(input);
            var connection = context.getConnection();
            for (var menu : dataset.menus()) {
                long restaurantId;
                try (var query = connection.prepareStatement("SELECT id FROM restaurants WHERE seed_key = ?")) {
                    query.setString(1, menu.restaurantSeedKey());
                    try (var result = query.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Initial menu restaurant does not exist");
                        }
                        restaurantId = result.getLong(1);
                    }
                }
                try (var update = connection.prepareStatement(
                        "UPDATE restaurants SET menu_source = ?, menu_verified_at = ?, menu_coverage = 'PARTIAL' WHERE id = ?")) {
                    update.setString(1, menu.source());
                    update.setObject(2, menu.verifiedAt());
                    update.setLong(3, restaurantId);
                    update.executeUpdate();
                }
                try (var insert = connection.prepareStatement("""
                        INSERT INTO menu_items (restaurant_id, seed_key, name, category, dish_type,
                            price_byn, portion, description, source, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (var item : menu.items()) {
                        insert.setLong(1, restaurantId);
                        insert.setString(2, item.seedKey());
                        insert.setString(3, item.name());
                        insert.setString(4, item.category().name());
                        insert.setString(5, item.dishType() == null ? null : item.dishType().name());
                        insert.setBigDecimal(6, item.priceByn());
                        insert.setString(7, item.portion());
                        insert.setString(8, item.description());
                        insert.setString(9, item.source());
                        insert.setBoolean(10, item.active());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        }
    }
}
