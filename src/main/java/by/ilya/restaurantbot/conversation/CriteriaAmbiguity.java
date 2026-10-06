package by.ilya.restaurantbot.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import by.ilya.restaurantbot.search.SearchRequest;

/** Conservative guards for common ambiguous wording; the model must also abstain from guessing. */
final class CriteriaAmbiguity {
    private CriteriaAmbiguity() { }

    static List<String> fields(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        var fields = new ArrayList<String>();
        if (value.matches("(?s).*(на человека|с человека|каждому|per person|доллар|евро|usd|eur|rub|₽|рубл|недорого|неясный бюджет).*" )
                || value.matches("\\s*\\d+(?:[.,]\\d+)?\\s*[?]?\\s*")
                || (value.contains("бюджет") && !value.matches("(?s).*(byn|белорусск).*"))) {
            fields.add("totalBudgetByn");
        }
        if (value.matches("(?s).*(вечером|утром|днём|днем|после обеда|попозже|в семь|в восемь|к девяти|в девять|около \\d|примерно в \\d).*" )
                || value.matches("(?s).*(?:^|\\s)в [1-9](?![0-9:]).*")) fields.add("time");
        if (value.matches("(?s).*(на выходных|в выходные|на следующей неделе|когда-нибудь|в пятницу|в субботу|в воскресенье).*" )
                || value.matches("(?s).*\\b\\d{1,2}/\\d{1,2}(?:\\b).*")) fields.add("date");
        return List.copyOf(fields);
    }

    static SearchRequest confirmedOnly(SearchRequest supplied, List<String> ambiguous) {
        return new SearchRequest(supplied.guests(),
                ambiguous.contains("totalBudgetByn") ? null : supplied.totalBudgetByn(),
                ambiguous.contains("date") ? null : supplied.date(),
                ambiguous.contains("time") ? null : supplied.time(), supplied.cuisine(), supplied.preferredTags());
    }
}
