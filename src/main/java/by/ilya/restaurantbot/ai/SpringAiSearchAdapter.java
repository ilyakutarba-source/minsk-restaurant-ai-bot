package by.ilya.restaurantbot.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import by.ilya.restaurantbot.catalog.MenuService;
import by.ilya.restaurantbot.catalog.RestaurantService;
import by.ilya.restaurantbot.conversation.ReferenceResolver;
import by.ilya.restaurantbot.search.RestaurantSearchService;
import by.ilya.restaurantbot.search.SearchCriteria;
import by.ilya.restaurantbot.search.SearchRequest;
import by.ilya.restaurantbot.search.SearchResult;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import static by.ilya.restaurantbot.ai.AiSearchReply.Status;

/** Bounded product turn with safe history, server-owned criteria and reference scope. */
public final class SpringAiSearchAdapter {
    static final String INITIAL_PROMPT = """
            Ты ресторанный помощник по собственному каталогу Минска. Для фактического поиска
            выбери searchRestaurants, ровно один запрос для одного посещения.
            Не придумывай рестораны и не вычисляй самостоятельно соответствие бюджету,
            часам или кухне: это делает Java. Не объединяй
            несколько поисков. При неизвестных или неоднозначных критериях передай null;
            не угадывай время, валюту или общий бюджет. Поддержаны только BYN и 1–6 гостей.
            При нескольких посещениях, другой валюте или неподдерживаемом запросе не вызывай tool.
            Бронирование и другие города не поддерживаются. При явно указанном городе кроме Минска
            не вызывай ни один tool; не подменяй город Минском. Это относится и к menu/details.
            Для unsupported запроса верни без tool только один код: UNSUPPORTED_BOOKING,
            UNSUPPORTED_CITY или UNSUPPORTED_CURRENCY. Не добавляй факты или свободный текст.
            Верни только новые значения из текущей реплики. Не копируй прежние критерии:
            Java сама сохраняет и объединяет их. Отсутствующее поле, кухня и пожелания — null.
            preferredTags заданные пользователем заменяют весь набор; [] только при явном снятии пожеланий.
            При неоднозначной дате, времени, валюте или бюджете не вызывай tool, запроси уточнение.
            Слово «спокойно» соответствует QUIET, «уютно» — COZY, «с друзьями» — FRIENDS.
            Вопрос о меню конкретного ресторана → только getRestaurantMenu.
            «Есть паста у первого?» → reference.ordinal=1, dishType=PASTA.
            Фильтр maxItemPriceByn — цена одной позиции, не общий бюджет посещения.
            Вопрос об адресе, собственных часах или контактах → только getRestaurantDetails.
            «До скольки третий?» → reference.ordinal=3, focus=HOURS; адрес → focus=ALL.
            Для обоих tools ровно один selector: ordinal 1–3, last=true или точное name.
            Не передавай restaurantId и не вычисляй ID/порядок из памяти: это делает Java.
            Не используй search для menu/details, не копируй факты из памяти в ответ.
            На одну реплику допустим только один из трёх tools; не вызывай несколько.
            """;
    private final ChatClient client;
    private final RestaurantSearchService service;
    private final Clock clock;
    private final boolean explanationEnabled;
    private final ReferenceResolver resolver;
    private final MenuService menus;
    private final RestaurantService catalog;
    private final SearchFactualRenderer renderer = new SearchFactualRenderer();
    private final BeanOutputConverter<ExplanationPlan> converter = new BeanOutputConverter<>(ExplanationPlan.class);

    public SpringAiSearchAdapter(ChatModel model, RestaurantSearchService service, Clock clock, boolean explanationEnabled) {
        this(model, service, clock, explanationEnabled, null, null, null);
    }

    public SpringAiSearchAdapter(ChatModel model, RestaurantSearchService service, Clock clock, boolean explanationEnabled,
                                 ReferenceResolver resolver, MenuService menus, RestaurantService catalog) {
        // Fresh client without global callbacks, memory or advisors; our model has no default tools.
        this.client = ChatClient.create(model);
        this.service = service;
        this.clock = clock;
        this.explanationEnabled = explanationEnabled;
        this.resolver = resolver;
        this.menus = menus;
        this.catalog = catalog;
    }

    public AiSearchReply search(String userText) {
        return search(userText, List.of(), UnaryOperator.identity());
    }

    public AiSearchReply search(String userText, List<Message> history, UnaryOperator<SearchRequest> prepare) {
        return search(userText, history, prepare, List.of());
    }

    public AiSearchReply search(String userText, List<Message> history, UnaryOperator<SearchRequest> prepare, List<String> ambiguous) {
        return turn(null, userText, history, prepare, ambiguous);
    }

    public AiSearchReply turn(Long chatId, String userText, List<Message> history,
                              UnaryOperator<SearchRequest> prepare, List<String> ambiguous) {
        if (userText == null || userText.isBlank() || userText.length() > 2000) {
            return new AiSearchReply(Status.INVALID_INPUT, null, "Напишите непустой запрос длиной до 2000 символов.",
                    0, 0, false, List.of());
        }
        var unsupported = UnsupportedRequests.reason(userText);
        if (unsupported != null) return new AiSearchReply(Status.INVALID_INPUT, null, unsupported, 0, 0, false, List.of());
        var tool = new SearchRestaurantsTool(service, clock, prepare, ambiguous);
        var menuTool = new RestaurantFollowUpTool(RestaurantFollowUpResult.Kind.MENU, chatId, resolver, menus, catalog);
        var detailsTool = new RestaurantFollowUpTool(RestaurantFollowUpResult.Kind.DETAILS, chatId, resolver, menus, catalog);
        var tools = List.of(tool, detailsTool, menuTool);
        int modelCalls = 0;
        ChatResponse first;
        try {
            modelCalls++; // Failed attempts count; neither call is retried here.
            first = client.prompt().system(INITIAL_PROMPT + "\nСегодня в Минске: "
                    + LocalDate.now(clock.withZone(SearchCriteria.MINSK)))
                    .messages(boundedContext(history))
                    .user(userText)
                    .options(OpenAiChatOptions.builder().temperature(0.0).maxTokens(350).N(1)
                        .internalToolExecutionEnabled(false).parallelToolCalls(false)
                        .toolCallbacks(tools).build())
                    .call().chatResponse();
        } catch (RuntimeException unavailable) {
            return controlled(Status.TEMPORARILY_UNAVAILABLE, modelCalls, 0, List.of());
        }
        if (usable(first, "STOP") && first.getResult().getOutput().getToolCalls().isEmpty()) {
            var scope = UnsupportedRequests.modelReason(first.getResult().getOutput().getText());
            if (scope != null) return new AiSearchReply(Status.INVALID_INPUT, null, scope, modelCalls, 0, false, List.of());
        }
        if (!usable(first, "TOOL_CALLS")) {
            return controlled(Status.NEED_CLARIFICATION, modelCalls, 0, List.of());
        }
        var calls = first.getResult().getOutput().getToolCalls();
        // Atomic batch precheck: do not run even the first request in an invalid batch.
        if (calls.size() != 1 || tools.stream().noneMatch(t -> t.getToolDefinition().name().equals(calls.getFirst().name()))
                || !"function".equals(calls.getFirst().type())
                || calls.getFirst().id() == null || calls.getFirst().id().isBlank()) {
            return controlled(Status.INVALID_INPUT, modelCalls, 0, List.of());
        }
        if (!SearchRestaurantsTool.NAME.equals(calls.getFirst().name())) {
            var selected = RestaurantFollowUpTool.MENU.equals(calls.getFirst().name()) ? menuTool : detailsTool;
            selected.call(calls.getFirst().arguments());
            return new AiSearchReply(selected.status(), null,
                    new RestaurantFollowUpRenderer().render(selected.status(), selected.result()),
                    modelCalls, selected.executions(), false, List.of(), selected.result());
        }
        tool.call(calls.getFirst().arguments());
        if (tool.status() != Status.OK && tool.status() != Status.NO_RESULTS) {
            return controlled(tool.status(), modelCalls, tool.executions(), tool.missing());
        }
        var result = tool.result();
        ExplanationPlan plan = null;
        if (tool.status() == Status.OK && explanationEnabled) {
            try {
                modelCalls++;
                var second = client.prompt().system("""
                        Choose an explanation plan only from the supplied positions and their allowedReasonCodes.
                        Return one item per position, 1–2 distinct reasons, phrasing NEUTRAL/WARM/COMPACT.
                        No factual text, no names, no extra fields, no tools. Do not reorder positions.
                        """)
                        .user(explanationProjection(result))
                        .options(OpenAiChatOptions.builder().temperature(0.0).maxTokens(350).N(1)
                            .internalToolExecutionEnabled(false).parallelToolCalls(false)
                            .toolCallbacks(List.of()).toolNames(Set.of()).tools(List.of()).toolChoice("none")
                            .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, converter.getJsonSchema()))
                            .build())
                        .call().chatResponse();
                if (usable(second, "STOP") && second.getResult().getOutput().getToolCalls().isEmpty()) {
                    var json = second.getResult().getOutput().getText();
                    if (json == null || json.length() > 2000) throw new IllegalArgumentException();
                    var parsed = AiJson.mapper().readValue(json, ExplanationPlan.class);
                    if (parsed != null && parsed.isValidFor(result)) plan = parsed;
                }
            } catch (Exception invalidOrFailed) {
                // Keep the trusted result and render deterministic reasons. No repair/search/retry.
            }
        }
        return new AiSearchReply(tool.status(), result, renderer.render(result, plan), modelCalls,
                tool.executions(), tool.status() == Status.OK && plan == null, List.of());
    }

    private static List<Message> boundedContext(List<Message> history) {
        int start = history.size();
        int remaining = 8000;
        while (start > 0 && remaining >= history.get(start - 1).getText().length()) {
            remaining -= history.get(--start).getText().length();
        }
        return history.subList(start, history.size());
    }

    private static boolean usable(ChatResponse response, String finishReason) {
        if (response == null || response.getResults().size() != 1 || response.getResult().getOutput() == null) return false;
        var output = response.getResult().getOutput();
        var refusal = output.getMetadata().get("refusal");
        return (refusal == null || refusal.toString().isBlank())
                && finishReason.equals(response.getResult().getMetadata().getFinishReason());
    }

    private static String explanationProjection(SearchResult result) throws Exception {
        var candidates = IntStream.range(0, result.candidates().size()).mapToObj(i -> Map.of(
                "position", i + 1,
                "tags", result.candidates().get(i).restaurant().tags().stream().sorted().toList(),
                "allowedReasonCodes", result.candidates().get(i).allowedReasonCodes())).toList();
        return AiJson.mapper().writeValueAsString(Map.of("candidates", candidates,
                "preferredTags", result.normalizedCriteria().preferredTags().stream().sorted().toList()));
    }

    private static AiSearchReply controlled(Status status, int calls, int executions, List<String> missing) {
        String text = switch (status) {
            case NEED_CLARIFICATION -> clarification(missing);
            case INVALID_INPUT -> "Не удалось принять критерии. Нужен один поиск по поддерживаемым значениям.";
            default -> "Поиск временно недоступен. Попробуйте позже.";
        };
        return new AiSearchReply(status, null, text, calls, executions, false, missing);
    }

    public static String clarification(List<String> missing) {
        if (missing.isEmpty()) return "Работаю только с каталогом Минска, бюджетом в BYN, без бронирования. "
                + "Уточните критерии: общий бюджет, дату или точное время HH:mm. Примеры: /help.";
        return "Уточните: " + String.join(", ", missing.stream().map(field -> switch (field) {
            case "guests" -> "число гостей (1–6)";
            case "totalBudgetByn" -> "общий бюджет на всех гостей в BYN";
            case "date" -> "дату посещения";
            case "time" -> "точное время HH:mm в Минске";
            default -> throw new IllegalArgumentException("Unsupported criteria field");
        }).toList()) + ".";
    }
}
