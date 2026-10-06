package by.ilya.restaurantbot.conversation;

/** Current delivered position only; restaurant facts are always reread from the catalog. */
public record SelectionItem(long chatId, int position, long selectionVersion, long restaurantId) {
}
