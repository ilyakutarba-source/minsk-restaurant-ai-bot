package by.ilya.restaurantbot.conversation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import by.ilya.restaurantbot.ai.AiSearchReply;
import by.ilya.restaurantbot.ai.SpringAiSearchAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationFailureTest {
    @ParameterizedTest
    @ValueSource(strings = {"load", "memory", "criteria", "remember", "reset"})
    void databaseFailuresProduceSafeTemporaryResponseWithoutRetryOrInventedFacts(String phase) {
        var store = mock(ConversationStore.class);
        var memory = mock(ChatMemory.class);
        var adapter = mock(SpringAiSearchAdapter.class);
        var selections = mock(SelectionService.class);
        var state = new ConversationState(1, 0, CriteriaMerge.empty(), 0, Clock.systemUTC().instant());
        var failure = new DataAccessResourceFailureException("SQL password private details");
        when(store.load(1)).thenReturn(state);
        when(memory.get(state.conversationId())).thenReturn(List.of());
        when(adapter.turn(eq(1L), anyString(), anyList(), any(), anyList())).thenReturn(
                new AiSearchReply(AiSearchReply.Status.NEED_CLARIFICATION, null, "Уточните гостей", 1, 0, false, List.of("guests")));
        switch (phase) {
            case "load" -> when(store.load(1)).thenThrow(failure);
            case "memory" -> when(memory.get(state.conversationId())).thenThrow(failure);
            case "criteria" -> doThrow(failure).when(store).saveCriteria(any(), any());
            case "remember" -> doThrow(failure).when(store).remember(any(), anyString(), anyString());
            case "reset" -> doThrow(failure).when(store).reset(any());
        }
        var sent = new ArrayList<AiSearchReply>();
        var service = new ConversationService(store, memory, adapter, Clock.systemUTC(), selections);
        var reply = service.handle(1, phase.equals("reset") ? "/new" : "Запрос", value -> { sent.add(value); return true; });
        assertThat(reply.status()).isEqualTo(AiSearchReply.Status.TEMPORARILY_UNAVAILABLE);
        assertThat(reply.text()).contains("временно недоступен").doesNotContain("SQL", "password", "private");
        assertThat(sent.getLast()).isEqualTo(reply);
        assertThat(sent).hasSize(phase.equals("remember") ? 2 : 1);
        verify(store).load(1);
        verifyNoInteractions(selections);
        if (phase.equals("load") || phase.equals("memory") || phase.equals("reset")) verifyNoInteractions(adapter);
        else verify(adapter).turn(eq(1L), anyString(), anyList(), any(), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/help", "too-long"})
    void helpAndInputRejectionWorkEvenWhenDatabaseIsUnavailable(String command) {
        var store = mock(ConversationStore.class);
        var memory = mock(ChatMemory.class);
        var adapter = mock(SpringAiSearchAdapter.class);
        var selections = mock(SelectionService.class);
        var service = new ConversationService(store, memory, adapter, Clock.systemUTC(), selections);
        var reply = service.handle(1, command.equals("too-long") ? "я".repeat(2001) : command, sent -> true);
        assertThat(reply.modelCalls()).isZero();
        assertThat(reply.toolExecutions()).isZero();
        assertThat(reply.text()).containsAnyOf("Минск", "2000");
        verifyNoInteractions(store, memory, adapter, selections);
    }
}
