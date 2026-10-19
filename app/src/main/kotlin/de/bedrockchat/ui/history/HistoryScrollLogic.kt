package de.bedrockchat.ui.history

import de.bedrockchat.data.local.ConversationEntity

/**
 * Entscheidet, ob der RecyclerView nach einem Listen-Update an Position 0 scrollen soll.
 *
 * Scrollt genau dann, wenn der erste Eintrag der neuen Liste ein anderer ist als der
 * bisherige erste Eintrag (oder die Liste vorher leer war). Zwei Fälle werden abgedeckt:
 *
 * 1. **Erster Load** (previousFirstId = null, neue Liste nicht leer):
 *    Stellt sicher, dass die neueste Konversation (ORDER BY updatedAt DESC) sichtbar ist.
 *
 * 2. **Room-Flow-Update** nach saveMessages:
 *    Neue Konversationen werden per DiffUtil an Position 0 eingefügt. Der
 *    LinearLayoutManager hält dabei die Scroll-Position auf dem bisherigen ersten
 *    sichtbaren Item – die neuen Einträge landen unsichtbar oberhalb des
 *    sichtbaren Bereichs. Scroll-to-Top verhindert das.
 *
 * Kein Scrollen bei reinen Inhalts-Updates (z. B. messageCount-Änderung am
 * ersten Eintrag) oder Löschen eines nicht-ersten Eintrags.
 */
internal fun shouldScrollToTop(
    previousFirstId: Long?,
    newList: List<ConversationEntity>
): Boolean {
    val newFirstId = newList.firstOrNull()?.id ?: return false
    return newFirstId != previousFirstId
}
