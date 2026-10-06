package by.ilya.restaurantbot.conversation;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Owns state and the only transcript write path. Delivery runs outside a DB transaction. */
@Service
@ConditionalOnProperty(name = "restaurant-bot.ai.enabled", havingValue = "true")
public class ConversationService {
    private final ConversationStore store;
    private final ChatMemory memory;
    private final SpringAiSearchAdapter adapter;
    private final Clock clock;
    private final SelectionService selections;
    private final Object[] locks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public ConversationService(ConversationStore store, ChatMemory memory, SpringAiSearchAdapter adapter, Clock clock,
                               SelectionService selections) {
        this.store = store;
        this.memory = memory;
        this.adapter = adapter;
        this.clock = clock;
        this.selections = selections;
    }

    public AiSearchReply handle(long chatId, String text, Function<AiSearchReply, Boolean> send) {
        // Bounded process-local locks serialize turns including /new; no per-chat storage in memory.
        synchronized (locks[Math.floorMod(Long.hashCode(chatId), locks.length)]) {
            var state = store.load(chatId);
            if (text != null && "/new".equals(text.trim())) {
                store.reset(state);
                selections.afterReset(chatId);
                var reply = new AiSearchReply(AiSearchReply.Status.OK, null, "Начат новый разговор. Укажите критерии посещения.",
                        0, 0, false, List.of());
                send.apply(reply);
                return reply; // Reset confirmation never seeds the new empty transcript.
            }
            var criteria = new by.ilya.restaurantbot.search.SearchRequest[]{state.currentCriteria()};
            var ambiguous = text == null ? List.<String>of() : CriteriaAmbiguity.fields(text);
            var reply = adapter.search(text, memory.get(state.conversationId()), supplied -> {
                criteria[0] = CriteriaMerge.merge(state.currentCriteria(), CriteriaAmbiguity.confirmedOnly(supplied, ambiguous), clock);
                return criteria[0];
            }, ambiguous);
            if (reply.status() == AiSearchReply.Status.NEED_CLARIFICATION && reply.missingFields().isEmpty()) {
                var fields = new java.util.ArrayList<>(CriteriaMerge.missing(criteria[0]));
                for (String field : ambiguous) if (!fields.contains(field)) fields.add(field);
                reply = new AiSearchReply(reply.status(), null, SpringAiSearchAdapter.clarification(fields),
                        reply.modelCalls(), reply.toolExecutions(), false, List.copyOf(fields));
            }
            if (reply.status() == AiSearchReply.Status.OK || reply.status() == AiSearchReply.Status.NO_RESULTS
                    || reply.status() == AiSearchReply.Status.NEED_CLARIFICATION) {
                store.saveCriteria(state, criteria[0]);
            }
            if (Boolean.TRUE.equals(send.apply(reply))) {
                String deliveredText = reply.text();
                if (reply.searchResult() != null && (reply.status() == AiSearchReply.Status.OK
                        || reply.status() == AiSearchReply.Status.NO_RESULTS)) {
                    // The factual renderer sends every candidate in exactly this order, including fallback.
                    var shownIds = reply.searchResult().candidates().stream().map(c -> c.restaurant().id()).toList();
                    try {
                        selections.replace(chatId, shownIds);
                    } catch (RuntimeException failure) {
                        var clarification = new AiSearchReply(AiSearchReply.Status.NEED_CLARIFICATION, null,
                                "Не удалось сохранить подборку. Для следующего вопроса укажите название ресторана.",
                                reply.modelCalls(), reply.toolExecutions(), false, List.of());
                        send.apply(clarification);
                        reply = clarification;
                    }
                }
                if (text != null && !text.isBlank() && text.length() <= 2000) store.remember(state, text, deliveredText);
            }
            return reply;
        }
    }
}
