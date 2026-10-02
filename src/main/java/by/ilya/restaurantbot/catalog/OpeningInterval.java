package by.ilya.restaurantbot.catalog;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class OpeningInterval {
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false, length = 9)
    private DayOfWeek weekday;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "closes_next_day", nullable = false)
    private boolean closesNextDay;

    protected OpeningInterval() {
    }

    public OpeningInterval(DayOfWeek weekday, LocalTime opensAt, LocalTime closesAt, boolean closesNextDay) {
        this.weekday = Objects.requireNonNull(weekday);
        this.opensAt = Objects.requireNonNull(opensAt);
        this.closesAt = Objects.requireNonNull(closesAt);
        if (closesNextDay ? !closesAt.isBefore(opensAt) : !closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("Opening interval must be positive and shorter than 24 hours");
        }
        this.closesNextDay = closesNextDay;
    }

    public DayOfWeek getWeekday() { return weekday; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public boolean isClosesNextDay() { return closesNextDay; }
}
