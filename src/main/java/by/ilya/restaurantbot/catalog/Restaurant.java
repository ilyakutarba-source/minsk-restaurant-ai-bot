package by.ilya.restaurantbot.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurants")
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seed_key", nullable = false, unique = true, length = 100)
    private String seedKey;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 300)
    private String address;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "catalog_source", nullable = false, length = 2000)
    private String catalogSource;
    @Column(name = "catalog_verified_at", nullable = false)
    private LocalDate catalogVerifiedAt;
    @Column(name = "estimated_check_per_guest", precision = 10, scale = 2)
    private BigDecimal estimatedCheckPerGuest;
    @Enumerated(EnumType.STRING)
    @Column(name = "check_estimation_type", length = 9)
    private CheckEstimationType checkEstimationType;
    @Column(name = "check_source", length = 2000)
    private String checkSource;
    @Column(name = "check_verified_at")
    private LocalDate checkVerifiedAt;
    @Column(name = "hours_source", length = 2000)
    private String hoursSource;
    @Column(name = "hours_verified_at")
    private LocalDate hoursVerifiedAt;

    @ElementCollection
    @CollectionTable(name = "restaurant_cuisines", joinColumns = @JoinColumn(name = "restaurant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "cuisine", nullable = false, length = 20)
    private Set<Cuisine> cuisines = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "restaurant_tags", joinColumns = @JoinColumn(name = "restaurant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 20)
    private Set<RestaurantTag> tags = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "opening_intervals", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<OpeningInterval> openingIntervals = new ArrayList<>();

    protected Restaurant() {
    }

    public Restaurant(String seedKey, String name, String address, boolean active,
                      String catalogSource, LocalDate catalogVerifiedAt, Set<Cuisine> cuisines) {
        this.seedKey = seedKey;
        this.name = name;
        this.address = address;
        this.active = active;
        this.catalogSource = catalogSource;
        this.catalogVerifiedAt = catalogVerifiedAt;
        this.cuisines.addAll(cuisines);
    }

    public void setEstimatedCheck(BigDecimal amount, CheckEstimationType type, String source, LocalDate verifiedAt) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 8
                || type == null || source == null || source.isBlank() || verifiedAt == null) {
            throw new IllegalArgumentException("A positive BYN check requires type, source and verification date");
        }
        this.estimatedCheckPerGuest = amount;
        this.checkEstimationType = type;
        this.checkSource = source;
        this.checkVerifiedAt = verifiedAt;
    }

    public void setOpeningHours(List<OpeningInterval> intervals, String source, LocalDate verifiedAt) {
        if (intervals.isEmpty() || source == null || source.isBlank() || verifiedAt == null) {
            throw new IllegalArgumentException("Opening hours require intervals, source and verification date");
        }
        this.openingIntervals = new ArrayList<>(intervals);
        this.hoursSource = source;
        this.hoursVerifiedAt = verifiedAt;
    }

    public Long getId() { return id; }
    public String getSeedKey() { return seedKey; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public String getCatalogSource() { return catalogSource; }
    public LocalDate getCatalogVerifiedAt() { return catalogVerifiedAt; }
    public BigDecimal getEstimatedCheckPerGuest() { return estimatedCheckPerGuest; }
    public CheckEstimationType getCheckEstimationType() { return checkEstimationType; }
    public String getCheckSource() { return checkSource; }
    public LocalDate getCheckVerifiedAt() { return checkVerifiedAt; }
    public String getHoursSource() { return hoursSource; }
    public LocalDate getHoursVerifiedAt() { return hoursVerifiedAt; }
    public Set<Cuisine> getCuisines() { return Set.copyOf(cuisines); }
    public Set<RestaurantTag> getTags() { return Set.copyOf(tags); }
    public List<OpeningInterval> getOpeningIntervals() { return List.copyOf(openingIntervals); }
}
