package by.ilya.restaurantbot.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(FinalAiEvalSupport.EvalClock.class)
class FinalAiOfflineEvalTest extends FinalAiEvalSupport {
    @ParameterizedTest(name="Documented offline eval case {0}")
    @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17})
    void documentedCasesUseProductionSdkParserAndFinalSeededServices(int number) throws Exception {
        if(number==3) {
            var supplied=AiJson.mapper().readValue("{\"time\":\"21:00\"}",by.ilya.restaurantbot.search.SearchRequest.class);
            var merged=by.ilya.restaurantbot.conversation.CriteriaMerge.merge(by.ilya.restaurantbot.conversation.CriteriaMerge.empty(),supplied,clock);
            assertThat(merged.guests()).isNull();
        }
        try(var fixture=new ProviderFixture(fixtures(number));var http=new AiConfiguration().aiHttpClient()) {
            var outcome=evaluate(number,AiConfiguration.createModel(http,"offline-fixture",fixture.origin()));
            assertThat(outcome.criticalIssue()).isEmpty();assertThat(outcome.qualityIssue()).isEmpty();
            assertThat(outcome.result()).isEqualTo("PASS");
            assertThat(fixture.requests()).hasSize(outcome.calls());
        }
    }
}
