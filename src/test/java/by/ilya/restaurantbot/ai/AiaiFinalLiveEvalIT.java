package by.ilya.restaurantbot.ai;

import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Explicit -Dtest=AiaiFinalLiveEvalIT only; ordinary test/verify never select *IT. */
@SpringBootTest(properties="restaurant-bot.ai.enabled=true")
@ActiveProfiles("test")
@Import(FinalAiEvalSupport.EvalClock.class)
class AiaiFinalLiveEvalIT extends FinalAiEvalSupport {
    @Autowired OpenAiChatModel model;
    @Autowired DataSource datasource;
    @Test void oneOptInRunOfAllDocumentedCasesAgainstPostgresql() throws Exception {
        try(var connection=datasource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        var rows=new ArrayList<Outcome>();
        String selected=System.getProperty("eval.cases","1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17");
        for(String number:selected.split(",")) {
            var row=evaluate(Integer.parseInt(number.strip()),model);rows.add(row);
            System.out.printf("Eval %d: %s; classification=%s; tool=%s; executions=%d; calls=%d; critical=%s%n",
                row.number(),row.result(),row.actual(),row.tool(),row.executions(),row.calls(),row.critical());
        }
        long main=rows.stream().filter(r -> r.number()<=12 && r.result().equals("PASS")).count();
        long adversarial=rows.stream().filter(r -> r.number()>12 && r.result().equals("PASS")).count();
        long critical=rows.stream().filter(r -> r.critical().equals("FAIL")).count();
        String run=System.getProperty("eval.run","initial");
        assertThat(run).matches("[a-zA-Z0-9-]+");
        Path evidence=Path.of("probes","task-12-evidence");Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("live-eval-"+run+".json"),AiJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(
            Map.of("provider","AIAI.BY","model","gpt-4.1-mini","temperature",0,"maxTokens",350,"n",1,
                "clock",clock.instant()+" Europe/Minsk","catalogRevision","Flyway V1-V9: 10 restaurants / 60 items",
                "accountQuotas","UNKNOWN / PARTIAL","cases",rows)));
        System.out.printf("Live summary: main=%d/12; adversarial=%d/5; critical failures=%d%n",main,adversarial,critical);
        assertThat(critical).isZero();
        if(rows.size()==17) {assertThat(main).isGreaterThanOrEqualTo(11);assertThat(adversarial).isEqualTo(5);}
        else assertThat(rows).allSatisfy(r -> assertThat(r.result()).isEqualTo("PASS"));
    }
}
