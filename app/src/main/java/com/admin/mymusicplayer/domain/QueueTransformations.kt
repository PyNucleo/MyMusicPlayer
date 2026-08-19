package com.admin.mymusicplayer.domain

data class QueueSnapshot<T>(
    val entries: List<T>,
    val currentIndex: Int,
) {
    init {
        require(entries.isEmpty() && currentIndex == 0 || currentIndex in entries.indices) {
            "currentIndex must identify an entry, or be zero for an empty queue"
        }
    }

    val current: T?
        get() = entries.getOrNull(currentIndex)

    val upcoming: List<T>
        get() = if (entries.isEmpty()) emptyList() else entries.drop(currentIndex + 1)
}

object QueueTransformations {
    fun <T, K> startSnapshot(
        entries: List<T>,
        selectedIdentity: K,
        identity: (T) -> K,
    ): QueueSnapshot<T> {
        requireUnique(entries, identity)
        val selectedIndex = entries.indexOfFirst { identity(it) == selectedIdentity }
        require(selectedIndex >= 0) { "selected item is not in the snapshot" }
        return QueueSnapshot(entries.toList(), selectedIndex)
    }

    fun <T, K> playNow(
        snapshot: QueueSnapshot<T>,
        entry: T,
        identity: (T) -> K,
    ): QueueSnapshot<T> {
        if (snapshot.entries.isEmpty()) return QueueSnapshot(listOf(entry), 0)
        val targetIdentity = identity(entry)
        val retained = snapshot.entries.filterNot { identity(it) == targetIdentity }.toMutableList()
        val insertionIndex = snapshot.currentIndex.coerceAtMost(retained.size)
        retained.add(insertionIndex, entry)
        return QueueSnapshot(retained.toList(), insertionIndex)
    }

    fun <T, K> playNext(
        snapshot: QueueSnapshot<T>,
        entry: T,
        identity: (T) -> K,
    ): QueueSnapshot<T> {
        if (snapshot.entries.isEmpty()) return QueueSnapshot(listOf(entry), 0)
        val targetIdentity = identity(entry)
        val currentIdentity = identity(snapshot.entries[snapshot.currentIndex])
        if (targetIdentity == currentIdentity) return snapshot.copy(entries = snapshot.entries.toList())
        val retained = snapshot.entries.filterNot { identity(it) == targetIdentity }.toMutableList()
        val currentIndex = retained.indexOfFirst { identity(it) == currentIdentity }
        retained.add(currentIndex + 1, entry)
        return QueueSnapshot(retained.toList(), currentIndex)
    }

    fun <T, K> addToQueue(
        snapshot: QueueSnapshot<T>,
        entry: T,
        identity: (T) -> K,
    ): QueueSnapshot<T> {
        if (snapshot.entries.any { identity(it) == identity(entry) }) {
            return snapshot.copy(entries = snapshot.entries.toList())
        }
        return snapshot.copy(entries = snapshot.entries + entry)
    }

    fun <T> reorderUpcoming(
        snapshot: QueueSnapshot<T>,
        fromIndex: Int,
        toIndex: Int,
    ): QueueSnapshot<T> {
        require(fromIndex > snapshot.currentIndex && fromIndex in snapshot.entries.indices) {
            "only upcoming items may be reordered"
        }
        require(toIndex > snapshot.currentIndex && toIndex in snapshot.entries.indices) {
            "only upcoming items may be reordered"
        }
        if (fromIndex == toIndex) return snapshot.copy(entries = snapshot.entries.toList())
        val result = snapshot.entries.toMutableList()
        val moved = result.removeAt(fromIndex)
        result.add(toIndex, moved)
        return snapshot.copy(entries = result.toList())
    }

    fun <T> removeUpcoming(snapshot: QueueSnapshot<T>, index: Int): QueueSnapshot<T> {
        require(index > snapshot.currentIndex && index in snapshot.entries.indices) {
            "only upcoming items may be removed"
        }
        return snapshot.copy(entries = snapshot.entries.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun <T> clearUpcoming(snapshot: QueueSnapshot<T>): QueueSnapshot<T> = if (snapshot.entries.isEmpty()) {
        snapshot
    } else {
        snapshot.copy(entries = snapshot.entries.take(snapshot.currentIndex + 1))
    }

    private fun <T, K> requireUnique(entries: List<T>, identity: (T) -> K) {
        require(entries.map(identity).toSet().size == entries.size) {
            "queue entry identities must be unique"
        }
    }
}

