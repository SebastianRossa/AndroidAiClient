package de.bedrockchat.ui.history

import de.bedrockchat.data.local.ConversationEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für [shouldScrollToTop].
 *
 * Gewünschtes Verhalten: Der RecyclerView soll immer dann an Position 0 scrollen,
 * wenn ein neuer Eintrag an den Anfang der Liste eingefügt wurde – damit die
 * neueste Konversation (ORDER BY updatedAt DESC) direkt unter der Toolbar sichtbar ist.
 */
class HistoryScrollLogicTest {

    private fun conv(id: Long) =
        ConversationEntity(id = id, title = "Chat $id", createdAt = id * 100, updatedAt = id * 200, messageCount = 1)

    // ── Scroll SOLL stattfinden ────────────────────────────────────────────────

    @Test
    fun `initial load – adapter was empty – should scroll to top`() {
        // previousFirstId = null, weil der Adapter noch keine Elemente hatte
        assertTrue(shouldScrollToTop(previousFirstId = null, newList = listOf(conv(1), conv(2))))
    }

    @Test
    fun `single item initial load – should scroll`() {
        assertTrue(shouldScrollToTop(previousFirstId = null, newList = listOf(conv(5))))
    }

    @Test
    fun `new item prepended – first id changed – should scroll`() {
        // War: [conv(2), conv(3)] → Jetzt: [conv(1), conv(2), conv(3)]
        assertTrue(shouldScrollToTop(previousFirstId = 2L, newList = listOf(conv(1), conv(2), conv(3))))
    }

    @Test
    fun `multiple new items prepended at once – should scroll`() {
        assertTrue(shouldScrollToTop(previousFirstId = 5L, newList = listOf(conv(1), conv(2), conv(5))))
    }

    @Test
    fun `completely replaced list with new first item – should scroll`() {
        assertTrue(shouldScrollToTop(previousFirstId = 10L, newList = listOf(conv(1))))
    }

    // ── Scroll soll NICHT stattfinden ─────────────────────────────────────────

    @Test
    fun `first item unchanged – no scroll`() {
        assertFalse(shouldScrollToTop(previousFirstId = 1L, newList = listOf(conv(1), conv(2))))
    }

    @Test
    fun `same single item – no scroll`() {
        assertFalse(shouldScrollToTop(previousFirstId = 5L, newList = listOf(conv(5))))
    }

    @Test
    fun `only middle item deleted – first unchanged – no scroll`() {
        // War: [conv(1), conv(2), conv(3)] → Jetzt: [conv(1), conv(3)]
        assertFalse(shouldScrollToTop(previousFirstId = 1L, newList = listOf(conv(1), conv(3))))
    }

    @Test
    fun `empty new list – no scroll`() {
        assertFalse(shouldScrollToTop(previousFirstId = null, newList = emptyList()))
    }

    @Test
    fun `empty new list even with previous first id – no scroll`() {
        assertFalse(shouldScrollToTop(previousFirstId = 1L, newList = emptyList()))
    }

    @Test
    fun `content update on first item (same id) – no scroll`() {
        // messageCount oder updatedAt hat sich geändert, aber id ist gleich
        val updated = ConversationEntity(id = 1L, title = "Chat 1", createdAt = 100, updatedAt = 999, messageCount = 5)
        assertFalse(shouldScrollToTop(previousFirstId = 1L, newList = listOf(updated, conv(2))))
    }
}
