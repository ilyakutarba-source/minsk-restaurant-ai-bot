package by.ilya.restaurantbot.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;

/** Explicit developer-side import used by Flyway; caller owns the transaction. No Spring bean or API. */
public final class CuratedCatalogImport {
    private CuratedCatalogImport() { }

    public static void apply(Connection connection, CuratedCatalogDataset dataset) throws SQLException {
        dataset.validate();
        var keys = new HashSet<String>();
        var identities = new HashSet<CuratedCatalogDataset.Identity>();
        try (var query = connection.prepareStatement("SELECT seed_key, name, address FROM restaurants");
             var rows = query.executeQuery()) {
            while (rows.next()) {
                keys.add(rows.getString(1));
                identities.add(CuratedCatalogDataset.identity(rows.getString(2), rows.getString(3)));
            }
        }
        // Check the entire batch against existing (including inactive) records before the first write.
        for (var v : dataset.venues()) {
            if (keys.contains(v.seedKey()) || identities.contains(CuratedCatalogDataset.identity(v.name(), v.address()))) {
                throw new IllegalArgumentException("Curated import would duplicate an existing venue");
            }
        }
        for (var v : dataset.venues()) insert(connection, v);
    }

    private static void insert(Connection connection, CuratedCatalogDataset.Venue v) throws SQLException {
        long id;
        try (var insert = connection.prepareStatement("""
                INSERT INTO restaurants (seed_key,name,address,active,catalog_source,catalog_verified_at,
                    estimated_check_per_guest,check_estimation_type,check_source,check_verified_at,
                    hours_source,hours_verified_at,menu_source,menu_verified_at,menu_coverage)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1,v.seedKey()); insert.setString(2,v.name()); insert.setString(3,v.address());
            insert.setBoolean(4,v.active()); insert.setString(5,v.catalogSource());
            insert.setObject(6,v.catalogVerifiedAt()); insert.setBigDecimal(7,v.check().amount());
            insert.setString(8,v.check().type().name()); insert.setString(9,v.check().provenance());
            insert.setObject(10,v.check().verifiedAt()); insert.setString(11,v.hoursSource());
            insert.setObject(12,v.hoursVerifiedAt());
            var menu = v.menu();
            insert.setString(13,menu == null ? null : menu.source());
            insert.setObject(14,menu == null ? null : menu.verifiedAt());
            insert.setString(15,menu == null ? null : menu.coverage().name());
            insert.executeUpdate();
            try (var generated = insert.getGeneratedKeys()) {
                if (!generated.next()) throw new SQLException("Missing generated restaurant ID");
                id = generated.getLong(1);
            }
        }
        try (var insert = connection.prepareStatement("INSERT INTO restaurant_cuisines (restaurant_id,cuisine) VALUES (?,?)")) {
            for (var cuisine : v.cuisines().stream().sorted().toList()) {
                insert.setLong(1,id); insert.setString(2,cuisine.name()); insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var insert = connection.prepareStatement("""
                INSERT INTO opening_intervals (restaurant_id,weekday,opens_at,closes_at,closes_next_day) VALUES (?,?,?,?,?)
                """)) {
            for (var h : v.openingIntervals()) {
                insert.setLong(1,id); insert.setString(2,h.weekday().name()); insert.setObject(3,h.opensAt());
                insert.setObject(4,h.closesAt()); insert.setBoolean(5,h.closesNextDay()); insert.addBatch();
            }
            insert.executeBatch();
        }
        if (v.menu() != null) try (var insert = connection.prepareStatement("""
                INSERT INTO menu_items (restaurant_id,seed_key,name,category,dish_type,price_byn,portion,description,source,active)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (var item : v.menu().items()) {
                insert.setLong(1,id); insert.setString(2,item.seedKey()); insert.setString(3,item.name());
                insert.setString(4,item.category().name()); insert.setString(5,item.dishType() == null ? null : item.dishType().name());
                insert.setBigDecimal(6,item.priceByn()); insert.setString(7,item.portion()); insert.setString(8,item.description());
                insert.setString(9,item.source()); insert.setBoolean(10,item.active()); insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
