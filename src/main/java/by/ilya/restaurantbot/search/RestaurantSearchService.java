package by.ilya.restaurantbot.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import by.ilya.restaurantbot.catalog.Restaurant;
import by.ilya.restaurantbot.catalog.RestaurantDetails;
import by.ilya.restaurantbot.catalog.RestaurantRepository;
import by.ilya.restaurantbot.search.SearchResult.Candidate;
import by.ilya.restaurantbot.search.SearchResult.ReasonCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantSearchService {
    private static final Comparator<Candidate> ORDER = Comparator
            .comparingInt(Candidate::matchCount).reversed()
            .thenComparing(Candidate::estimatedTotalByn)
            .thenComparing(c -> c.restaurant().id());
    private final RestaurantRepository repository;
    private final Clock clock;

    public RestaurantSearchService(RestaurantRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public SearchResult search(SearchRequest request) {
        var criteria = SearchCriteria.normalize(request, clock);
        var candidates = repository.findAllByOrderByIdAsc().stream()
                .filter(Restaurant::isActive)
                .filter(r -> criteria.cuisine() == null || r.getCuisines().contains(criteria.cuisine()))
                .filter(RestaurantSearchService::hasVerifiedCheck)
                .filter(r -> estimatedTotal(r, criteria).compareTo(criteria.totalBudgetByn()) <= 0)
                .filter(r -> isOpen(r, criteria))
                .map(r -> candidate(r, criteria))
                .sorted(ORDER)
                .limit(3)
                .toList();
        return new SearchResult(criteria, "BYN", candidates, List.of(
                "Ориентировочный чек, не гарантия итоговой суммы.",
                "Сохранённое недельное расписание не гарантирует праздничные часы или свободный столик.",
                "Теги описывают характеристики каталога, не гарантируют обстановку при посещении."));
    }

    private static boolean hasVerifiedCheck(Restaurant r) {
        return r.getEstimatedCheckPerGuest() != null && r.getEstimatedCheckPerGuest().signum() > 0
                && r.getCheckEstimationType() != null && r.getCheckSource() != null
                && !r.getCheckSource().isBlank() && r.getCheckVerifiedAt() != null;
    }

    private static BigDecimal estimatedTotal(Restaurant r, SearchCriteria criteria) {
        return r.getEstimatedCheckPerGuest().multiply(BigDecimal.valueOf(criteria.guests()));
    }

    private static boolean isOpen(Restaurant r, SearchCriteria criteria) {
        if (r.getHoursSource() == null || r.getHoursSource().isBlank() || r.getHoursVerifiedAt() == null) {
            return false;
        }
        var arrival = criteria.date().atTime(criteria.time());
        return r.getOpeningIntervals().stream().anyMatch(interval -> {
            // Anchor the interval to its opening day, including yesterday's overnight continuation.
            var openingDate = criteria.date();
            if (interval.getWeekday() != openingDate.getDayOfWeek()) {
                openingDate = openingDate.minusDays(1);
                if (!interval.isClosesNextDay() || interval.getWeekday() != openingDate.getDayOfWeek()) {
                    return false;
                }
            }
            LocalDateTime open = openingDate.atTime(interval.getOpensAt());
            LocalDateTime close = openingDate.plusDays(interval.isClosesNextDay() ? 1 : 0)
                    .atTime(interval.getClosesAt());
            return !arrival.isBefore(open) && arrival.isBefore(close);
        });
    }

    private static Candidate candidate(Restaurant r, SearchCriteria criteria) {
        var reasons = new ArrayList<ReasonCode>();
        if (criteria.cuisine() != null) {
            reasons.add(ReasonCode.CUISINE_MATCH);
        }
        reasons.add(ReasonCode.BUDGET_MATCH);
        reasons.add(ReasonCode.HOURS_MATCH);
        int matches = 0;
        for (var tag : criteria.preferredTags()) {
            if (r.getTags().contains(tag)) {
                matches++;
                reasons.add(ReasonCode.valueOf(tag.name() + "_TAG_MATCH"));
            }
        }
        return new Candidate(RestaurantDetails.from(r), estimatedTotal(r, criteria), matches, List.copyOf(reasons));
    }
}
