package by.ilya.restaurantbot.search;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfiguration {
    @Bean
    public Clock clock() {
        return Clock.system(SearchCriteria.MINSK);
    }
}
