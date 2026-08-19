package com.admin.mymusicplayer.domain

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class ShufflePlannerTest {
    @Test
    fun cycle_hasExactCoverage_forRequiredSizes() {
        listOf(0, 1, 2, 20, 100, 1_000).forEach { size ->
            val input = (0 until size).toList()
            val original = input.toList()
            val result = ShufflePlanner.planCycle(input, { it }, random = Random(size))

            assertThat(result).hasSize(size)
            assertThat(result.toSet()).containsExactlyElementsIn(input.toSet())
            assertThat(result.distinct()).hasSize(size)
            assertThat(input).containsExactlyElementsIn(original).inOrder()
        }
    }

    @Test
    fun nextCycle_neverRepeatsPreviousBoundary_whenMoreThanOneItem() {
        val input = (0 until 100).toList()
        repeat(250) { seed ->
            val first = ShufflePlanner.planCycle(input, { it }, random = Random(seed))
            val second = ShufflePlanner.planCycle(
                input,
                { it },
                previousLastIdentity = first.last(),
                random = Random(seed + 10_000),
            )
            assertThat(second.first()).isNotEqualTo(first.last())
        }
    }

    @Test
    fun midCycle_preservesConsumedAndCurrent_andShufflesOnlyUnconsumed() {
        val input = listOf("A", "B", "C", "D", "E", "F", "G")
        val result = ShufflePlanner.enableMidCycle(input, 2, { it }, Random(44))

        assertThat(result.take(3)).containsExactly("A", "B", "C").inOrder()
        assertThat(result.drop(3)).containsExactly("D", "E", "F", "G")
        assertThat(input).containsExactly("A", "B", "C", "D", "E", "F", "G").inOrder()
    }

    @Test
    fun explicitTap_makesSelectionCurrent_andShufflesEveryOtherEntryOnce() {
        val input = listOf("A", "B", "C", "D", "E")
        val result = ShufflePlanner.planFromExplicitSelection(input, "C", { it }, Random(7))

        assertThat(result.first()).isEqualTo("C")
        assertThat(result.drop(1)).containsExactly("A", "B", "D", "E")
        assertThat(result.distinct()).hasSize(input.size)
        assertThat(input).containsExactly("A", "B", "C", "D", "E").inOrder()
    }

    @Test
    fun duplicateQueueEntryIdentity_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ShufflePlanner.planCycle(listOf(1, 1), { it }, random = Random(0))
        }
    }

    @Test
    fun singletonBoundary_remainsPlayable() {
        assertThat(
            ShufflePlanner.planCycle(
                listOf("A"),
                { it },
                previousLastIdentity = "A",
                random = Random(0),
            ),
        ).containsExactly("A")
    }
}
