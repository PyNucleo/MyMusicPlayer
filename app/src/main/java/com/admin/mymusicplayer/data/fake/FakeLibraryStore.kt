package com.admin.mymusicplayer.data.fake

import com.admin.mymusicplayer.domain.Playlist
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FakePlaylistRecord(
    val playlist: Playlist,
    val tracks: List<Track>,
)

object FakeLibraryStore {
    private const val BASE_TIME = 1_777_000_000_000L
    private val initialRecords = seedRecords()
    private val _records = MutableStateFlow(initialRecords)
    val records: StateFlow<List<FakePlaylistRecord>> = _records.asStateFlow()
    private var nextPlaylistId = 3L

    @Synchronized
    fun reset() {
        _records.value = initialRecords
        nextPlaylistId = 3L
    }

    @Synchronized
    fun snapshot(): List<FakePlaylistRecord> = _records.value.deepCopy()

    @Synchronized
    fun restore(snapshot: List<FakePlaylistRecord>) {
        _records.value = snapshot.deepCopy()
    }

    @Synchronized
    fun create(name: String) {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Playlist name cannot be blank" }
        val id = nextPlaylistId++
        val playlist = Playlist(id, clean, createdAtEpochMs = BASE_TIME + id)
        _records.value = _records.value + FakePlaylistRecord(playlist, emptyList())
    }

    @Synchronized
    fun rename(id: Long, name: String) {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Playlist name cannot be blank" }
        _records.value = _records.value.map { record ->
            if (record.playlist.id != id) record else record.copy(
                playlist = record.playlist.copy(
                    name = clean,
                    revision = record.playlist.revision + 1,
                    modifiedAtEpochMs = record.playlist.modifiedAtEpochMs + 1,
                ),
            )
        }
    }

    @Synchronized
    fun delete(id: Long) {
        _records.value = _records.value.filterNot { it.playlist.id == id }
    }

    @Synchronized
    fun addTrack(playlistId: Long, track: Track): Boolean {
        var added = false
        _records.value = _records.value.map { record ->
            if (record.playlist.id != playlistId) return@map record
            if (record.tracks.any { it.sourceIdentity == track.sourceIdentity }) return@map record
            added = true
            record.withTracks(record.tracks + track)
        }
        return added
    }

    @Synchronized
    fun reorderTrack(playlistId: Long, identity: SourceIdentity, targetIndex: Int) {
        _records.value = _records.value.map { record ->
            if (record.playlist.id != playlistId) return@map record
            val fromIndex = record.tracks.indexOfFirst { it.sourceIdentity == identity }
            require(fromIndex >= 0) { "Track is no longer in this playlist" }
            require(targetIndex in record.tracks.indices) { "Target position is outside the playlist" }
            val reordered = record.tracks.toMutableList()
            val moved = reordered.removeAt(fromIndex)
            reordered.add(targetIndex, moved)
            record.withTracks(reordered)
        }
    }

    @Synchronized
    fun removeSelected(sourcePlaylistId: Long, selected: Set<SourceIdentity>) {
        require(selected.isNotEmpty()) { "No tracks selected" }
        _records.value = _records.value.map { record ->
            if (record.playlist.id != sourcePlaylistId) record
            else record.withTracks(record.tracks.filterNot { it.sourceIdentity in selected })
        }
    }

    @Synchronized
    fun copySelected(sourcePlaylistId: Long, targetPlaylistId: Long, selected: Set<SourceIdentity>) {
        transfer(sourcePlaylistId, targetPlaylistId, selected, removeFromSource = false)
    }

    @Synchronized
    fun moveSelected(sourcePlaylistId: Long, targetPlaylistId: Long, selected: Set<SourceIdentity>) {
        transfer(sourcePlaylistId, targetPlaylistId, selected, removeFromSource = true)
    }

    private fun transfer(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
        removeFromSource: Boolean,
    ) {
        require(sourcePlaylistId != targetPlaylistId) { "Choose another playlist" }
        require(selected.isNotEmpty()) { "No tracks selected" }
        val before = _records.value
        val source = before.singleOrNull { it.playlist.id == sourcePlaylistId }
            ?: error("Source playlist no longer exists")
        val target = before.singleOrNull { it.playlist.id == targetPlaylistId }
            ?: error("Target playlist no longer exists")
        val ordered = source.tracks.filter { it.sourceIdentity in selected }
        val existing = target.tracks.mapTo(mutableSetOf()) { it.sourceIdentity }
        val appended = ordered.filter { existing.add(it.sourceIdentity) }
        val updated = before.map { record ->
            when (record.playlist.id) {
                sourcePlaylistId -> if (removeFromSource) {
                    record.withTracks(record.tracks.filterNot { it.sourceIdentity in selected })
                } else record
                targetPlaylistId -> record.withTracks(record.tracks + appended)
                else -> record
            }
        }
        _records.value = updated
    }

    private fun FakePlaylistRecord.withTracks(updatedTracks: List<Track>) = copy(
        playlist = playlist.copy(
            revision = playlist.revision + 1,
            modifiedAtEpochMs = playlist.modifiedAtEpochMs + 1,
        ),
        tracks = updatedTracks.toList(),
    )

    private fun List<FakePlaylistRecord>.deepCopy() = map { it.copy(tracks = it.tracks.toList()) }

    private fun seedRecords(): List<FakePlaylistRecord> {
        val tracks = listOf(
            "A" to "Night Drive",
            "B" to "Blue Static",
            "C" to "Quiet Signal",
            "D" to "After Midnight",
            "E" to "Soft Current",
            "F" to "Distant Lights",
        ).mapIndexed { index, (id, title) ->
            Track(
                id = index + 1L,
                sourceType = SourceType.YOUTUBE,
                sourceMediaId = "fake-seed-$id",
                title = title,
                uploader = "Demo artist ${(index % 3) + 1}",
                durationMs = 180_000L + index * 8_000L,
            )
        }
        return listOf(
            FakePlaylistRecord(
                Playlist(1, "Road Trip", createdAtEpochMs = BASE_TIME),
                tracks.take(4),
            ),
            FakePlaylistRecord(
                Playlist(2, "Focus", createdAtEpochMs = BASE_TIME + 1),
                tracks.drop(4),
            ),
        )
    }
}
