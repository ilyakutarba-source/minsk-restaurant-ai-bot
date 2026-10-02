package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    @Column(name = "seed_key", nullable = false, length = 100)
    private String seedKey;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private MenuCategory category;
    @Enumerated(EnumType.STRING)
    @Column(name = "dish_type", length = 5)
    private DishType dishType;
    @Column(name = "price_byn", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceByn;
    @Column(length = 100)
    private String portion;
    @Column(length = 1000)
    private String description;
    @Column(length = 2000)
    private String source;
    @Column(nullable = false)
    private boolean active;

    protected MenuItem() {
    }

    public MenuItem(Restaurant restaurant, MenuDataset.Item item) {
        this.restaurant = Objects.requireNonNull(restaurant);
        update(item);
    }

    public void update(MenuDataset.Item item) {
        MenuDataset.validateItem(item);
        if (seedKey != null && !seedKey.equals(item.seedKey())) {
            throw new IllegalArgumentException("Menu item key cannot change");
        }
        seedKey = item.seedKey();
        name = item.name();
        category = item.category();
        dishType = item.dishType();
        priceByn = item.priceByn();
        portion = item.portion();
        description = item.description();
        source = item.source();
        active = item.active();
    }

    public Long getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getSeedKey() { return seedKey; }
    public String getName() { return name; }
    public MenuCategory getCategory() { return category; }
    public DishType getDishType() { return dishType; }
    public BigDecimal getPriceByn() { return priceByn; }
    public String getPortion() { return portion; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public boolean isActive() { return active; }
}
