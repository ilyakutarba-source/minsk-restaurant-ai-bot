package by.ilya.restaurantbot.ai;

import by.ilya.restaurantbot.catalog.Cuisine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/** Explicit -Dtest=AiaiLiveSmokeIT only: one production turn, at most two paid calls. */
@SpringBootTest(properties = "restaurant-bot.ai.enabled=true")
@ActiveProfiles("test")
class AiaiLiveSmokeIT {
    @Autowired SpringAiSearchAdapter adapter;

    @Test
    void fullSearchAndNativeExplanationWithProductionConfiguration() {
        var reply = adapter.search("Сегодня в 21:00 нас двое, общий бюджет 150 BYN, хочется итальянскую кухню.");
        assertThat(reply.status()).isEqualTo(AiSearchReply.Status.OK);
        assertThat(reply.modelCalls()).isEqualTo(2);
        assertThat(reply.toolExecutions()).isEqualTo(1);
        assertThat(reply.explanationFallback()).isFalse();
        assertThat(reply.searchResult().normalizedCriteria().guests()).isEqualTo(2);
        assertThat(reply.searchResult().normalizedCriteria().totalBudgetByn()).isEqualByComparingTo("150");
        assertThat(reply.searchResult().normalizedCriteria().cuisine()).isEqualTo(Cuisine.ITALIAN);
        assertThat(reply.searchResult().normalizedCriteria().time().toString()).isEqualTo("21:00");
        assertThat(reply.searchResult().candidates()).hasSize(1);
        assertThat(reply.text()).contains("Pizza Tempo", "65.40 BYN");
        System.out.println("AIAI production smoke PASS: gpt-4.1-mini; 2 model calls; 1 tool; native explanation valid.");
    }
}
