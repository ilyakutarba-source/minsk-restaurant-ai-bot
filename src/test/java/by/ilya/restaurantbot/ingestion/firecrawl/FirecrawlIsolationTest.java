package by.ilya.restaurantbot.ingestion.firecrawl;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import by.ilya.restaurantbot.catalog.MenuItemRepository;
import by.ilya.restaurantbot.catalog.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FirecrawlIsolationTest {
    @MockitoSpyBean RestaurantRepository restaurants;
    @MockitoSpyBean MenuItemRepository items;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;

    @Test
    void fullCandidatePipelineDoesNotInvokeRepositoriesOrJoinRuntimeContext() throws Exception {
        var before = snapshot();
        clearInvocations(restaurants, items);
        var fixture = FirecrawlCandidateTest.fixture("complete");
        FirecrawlPoc.run(List.of(new FirecrawlPoc.Target("Synthetic fixture", FirecrawlCandidateTest.SOURCE)),
                directory.resolve("candidates.json"), source -> fixture,
                Clock.fixed(FirecrawlCandidateTest.FETCHED, ZoneOffset.UTC));
        verifyNoInteractions(restaurants, items);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(context.getBeansOfType(FirecrawlClient.class)).isEmpty();
        assertThat(context.getBeansOfType(FirecrawlCandidateExtractor.class)).isEmpty();
    }

    private List<List<java.util.Map<String, Object>>> snapshot() {
        return List.of(jdbc.queryForList("select * from restaurants order by id"),
                jdbc.queryForList("select * from menu_items order by id"),
                jdbc.queryForList("select * from opening_intervals order by restaurant_id, weekday, opens_at"));
    }
}
