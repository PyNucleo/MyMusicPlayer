package com.admin.mymusicplayer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueTransformationsTest {
    @Test
    fun startingSnapshot_preservesPlaylistOrderAndSelectsRequestedEntry() {
        val playlist = listOf("A", "B", "C", "D")
        val snapshot = QueueTransformations.startSnapshot(playlist, "C", { it })

        assertThat(snapshot.entries).containsExactlyElementsIn(playlist).inOrder()
        assertThat(snapshot.current).isEqualTo("C")
        assertThat(playlist).containsExactly("A", "B", "C", "D").inOrder()
    }

    @Test
    fun playNow_makesEntryCurrentWithoutDuplicatingIt() {
        val original = QueueSnapshot(listOf("A", "B", "C", "D"), 1)
        val result = QueueTransformations.playNow(original, "D", { it })

        assertThat(result.current).isEqualTo("D")
        assertThat(result.entries).containsExactly("A", "D", "B", "C").inOrder()
        assertThat(original.entries).containsExactly("A", "B", "C", "D").inOrder()
    }

    @Test
    fun playNext_movesExistingUpcomingEntryDirectlyAfterCurrent() {
        val original = QueueSnapshot(listOf("A", "B", "C", "D"), 1)
        val result = QueueTransformations.playNext(original, "D", { it })

        assertThat(result.entries).containsExactly("A", "B", "D", "C").inOrder()
        assertThat(result.current).isEqualTo("B")
    }

    @Test
    fun addReorderRemoveAndClear_affectOnlyUpcoming() {
        val original = QueueSnapshot(listOf("A", "B", "C"), 1)
        val added = QueueTransformations.addToQueue(original, "D", { it })
        val reordered = QueueTransformations.reorderUpcoming(added, 3, 2)
        val removed = QueueTransformations.removeUpcoming(reordered, 3)
        val cleared = QueueTransformations.clearUpcoming(removed)

        assertThat(added.entries).containsExactly("A", "B", "C", "D").inOrder()
        assertThat(reordered.entries).containsExactly("A", "B", "D", "C").inOrder()
        assertThat(removed.entries).containsExactly("A", "B", "D").inOrder()
        assertThat(cleared.entries).containsExactly("A", "B").inOrder()
        assertThat(cleared.current).isEqualTo("B")
    }

    @Test
    fun repeatedNextPreviousStateChanges_doNotDuplicateQueueEntries() {
        var currentIndex = 0
        val queue = (0 until 100).toList()
        repeat(1_000) { iteration ->
            currentIndex = if (iteration % 3 == 0) {
                (currentIndex - 1).coerceAtLeast(0)
            } else {
                (currentIndex + 1).coerceAtMost(queue.lastIndex)
            }
        }

        assertThat(queue.distinct()).hasSize(queue.size)
        assertThat(currentIndex).isIn(0..queue.lastIndex)
    }
}

