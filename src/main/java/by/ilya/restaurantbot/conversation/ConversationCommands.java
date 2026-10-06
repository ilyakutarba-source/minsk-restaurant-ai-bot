package by.ilya.restaurantbot.conversation;

/** Java-only help; commands do not enter model history or change visit criteria. */
final class ConversationCommands {
    static final String START = """
            Помогу выбрать ресторан из сохранённого каталога Минска. Пишите обычным русским языком.
            Например: «Сегодня в 21:00 нас двое, бюджет 150 BYN, хочется итальянскую кухню».
            После поиска: «Покажи меню второго», «До скольки первый?».
            Меню сохранено частично. Бронирование не поддерживается.
            /help — примеры, /new — новый разговор.
            """.strip();
    static final String HELP = """
            Поиск: «Сегодня в 21:00 нас двое, бюджет 150 BYN, хочется итальянскую кухню».
            После подборки можно спросить:
            «Покажи меню второго»
            «Есть паста у первого?»
            «До скольки третий?»
            «А если нас четверо?»
            Номер должен быть в последней показанной подборке.
            Поддержаны Минск, общий бюджет в BYN, 1–6 гостей, сегодня и следующие 6 дней.
            Меню частичное; наличие столика и блюд не проверяется. Бронирование не поддерживается.
            /new — очистить разговор, критерии и подборку; /start — знакомство.
            """.strip();

    private ConversationCommands() { }

    static String command(String text) {
        if (text == null || !text.strip().startsWith("/")) return null;
        return text.strip().split("\\s+", 2)[0].split("@", 2)[0];
    }
}
