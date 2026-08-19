package com.admin.mymusicplayer.domain

import kotlin.random.Random

object ShufflePlanner {
    fun <T, K> planCycle(
        eligible: List<T>,
        identity: (T) -> K,
        previousLastIdentity: K? = null,
        random: Random = Random.Default,
    ): List<T> {
        requireUnique(eligible, identity)
        val planned = fisherYatesCopy(eligible, random).toMutableList()
        if (planned.size > 1 && previousLastIdentity != null && identity(planned.first()) == previousLastIdentity) {
            val swapIndex = random.nextInt(1, planned.size)
            val first = planned[0]
            planned[0] = planned[swapIndex]
            planned[swapIndex] = first
        }
        return planned
    }

    fun <T, K> enableMidCycle(
        currentOrder: List<T>,
        currentIdentity: K,
        consumedIdentities: Set<K>,
        identity: (T) -> K,
        random: Random = Random.Default,
    ): List<T> {
        if (currentOrder.isEmpty()) return emptyList()
        requireUnique(currentOrder, identity)
        val knownIdentities = currentOrder.map(identity).toSet()
        require(currentIdentity in knownIdentities) { "current item is outside the queue" }
        require(consumedIdentities.all { it in knownIdentities }) { "consumed item is outside the queue" }
        val consumedIncludingCurrent = consumedIdentities + currentIdentity
        val consumedAndCurrent = currentOrder.filter { item ->
            val itemIdentity = identity(item)
            itemIdentity in consumedIncludingCurrent && itemIdentity != currentIdentity
        } + currentOrder.single { identity(it) == currentIdentity }
        val unconsumed = currentOrder.filterNot { identity(it) in consumedIncludingCurrent }
        return consumedAndCurrent + planCycle(unconsumed, identity, random = random)
    }

    fun <T, K> planFromExplicitSelection(
        eligible: List<T>,
        selectedIdentity: K,
        identity: (T) -> K,
        random: Random = Random.Default,
    ): List<T> {
        requireUnique(eligible, identity)
        val selected = eligible.singleOrNull { identity(it) == selectedIdentity }
            ?: throw IllegalArgumentException("selected item is not eligible")
        return listOf(selected) + planCycle(
            eligible = eligible.filterNot { identity(it) == selectedIdentity },
            identity = identity,
            random = random,
        )
    }

    fun <T, K> reshuffle(
        eligible: List<T>,
        identity: (T) -> K,
        currentIdentity: K? = null,
        random: Random = Random.Default,
    ): List<T> {
        if (currentIdentity == null) return planCycle(eligible, identity, random = random)
        return planFromExplicitSelection(eligible, currentIdentity, identity, random)
    }

    private fun <T, K> requireUnique(items: List<T>, identity: (T) -> K) {
        require(items.map(identity).toSet().size == items.size) {
            "eligible queue entry identities must be unique"
        }
    }

    private fun <T> fisherYatesCopy(input: List<T>, random: Random): List<T> {
        val result = input.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = value
        }
        return result.toList()
    }
}
