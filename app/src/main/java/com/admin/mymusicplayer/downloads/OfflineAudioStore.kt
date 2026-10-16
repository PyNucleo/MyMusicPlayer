package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.resolver.PlayableAudio
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Each committed metadata file points only to a complete, app-private audio file. */
class OfflineAudioStore(private val directory: File) {
    private val gson = Gson()

    data class SavedAudio(val track: Track, val bytes: Long, val mimeType: String?)

    fun readAll(): List<SavedAudio> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" }
        .mapNotNull { metadata ->
            runCatching {
                val saved = gson.fromJson(metadata.readText(), SavedAudio::class.java)
                // Reconstruct to validate even when Gson bypasses Kotlin constructors.
                val track = saved.track.copy()
                require(metadata.nameWithoutExtension == key(track.sourceIdentity))
                require(saved.bytes > 0 && audioFile(track.sourceIdentity).length() == saved.bytes)
                saved.copy(track = track)
            }.getOrNull()
        }

    @Synchronized
    fun find(track: Track): PlayableAudio? {
        val identity = track.sourceIdentity
        return runCatching {
            val saved = gson.fromJson(metadataFile(identity).readText(), SavedAudio::class.java)
            val audio = audioFile(identity)
            require(saved.track.sourceIdentity == identity && saved.bytes > 0 && audio.length() == saved.bytes)
            PlayableAudio(audio.toURI().toString(), saved.mimeType)
        }.getOrNull()
    }

    fun partialFile(identity: SourceIdentity): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot open download storage")
        return File(directory, "${key(identity)}.part")
    }

    @Synchronized
    fun recordPending(track: Track) {
        partialFile(track.sourceIdentity) // Ensure the private directory exists before writing metadata.
        val pending = pendingFile(track.sourceIdentity)
        if (readPending().any { it.sourceIdentity == track.sourceIdentity }) return
        val temporary = File("${pending.path}.tmp")
        try {
            temporary.outputStream().use { output ->
                output.write(gson.toJson(track).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (pending.exists() && !pending.delete()) throw IOException("Cannot save download progress")
            if (!temporary.renameTo(pending)) throw IOException("Cannot save download progress")
        } finally {
            temporary.delete()
        }
    }

    fun readPending(): List<Track> = directory.listFiles().orEmpty()
        .filter { it.extension == "pending" }
        .mapNotNull { pending ->
            runCatching {
                val track = gson.fromJson(pending.readText(), Track::class.java).copy()
                require(pending.nameWithoutExtension == key(track.sourceIdentity))
                track.takeIf { find(track) == null }
            }.getOrNull()
        }

    @Synchronized
    fun commit(track: Track, partial: File, mimeType: String?): SavedAudio {
        require(partial == partialFile(track.sourceIdentity) && partial.length() > 0)
        val identity = track.sourceIdentity
        val saved = SavedAudio(track, partial.length(), mimeType)
        val metadata = File(directory, "${key(identity)}.json.tmp")
        try {
            // Permit retry after a missing/corrupted file without replacing a healthy download.
            if (find(track) != null) throw IOException("This audio is already downloaded")
            listOf(metadataFile(identity), audioFile(identity)).forEach {
                if (it.exists() && !it.delete()) throw IOException("Cannot replace incomplete download")
            }
            metadata.outputStream().use { output ->
                output.write(gson.toJson(saved).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!partial.renameTo(audioFile(identity))) throw IOException("Cannot save downloaded audio")
            if (!metadata.renameTo(metadataFile(identity))) {
                audioFile(identity).delete()
                throw IOException("Cannot save download metadata")
            }
            pendingFile(identity).delete()
            File("${partial.path}.resume").delete()
        } finally {
            metadata.delete()
        }
        return saved
    }

    @Synchronized
    fun remove(identity: SourceIdentity) {
        // Hide the entry before removing bytes; never expose partial media as an offline download.
        val metadata = metadataFile(identity)
        if (metadata.exists() && !metadata.delete()) throw IOException("Cannot remove download")
        val audio = audioFile(identity)
        if (audio.exists() && !audio.delete()) throw IOException("Cannot remove downloaded audio")
        File(directory, "${key(identity)}.part").delete()
        File(directory, "${key(identity)}.part.resume").delete()
        pendingFile(identity).delete()
    }

    fun cleanInterruptedFiles() {
        val pendingKeys = readPending().mapTo(mutableSetOf()) { key(it.sourceIdentity) }
        directory.listFiles().orEmpty().filter {
            it.name.endsWith(".tmp") ||
                (it.extension == "part" && it.nameWithoutExtension !in pendingKeys) ||
                (it.name.endsWith(".part.resume") && it.name.removeSuffix(".part.resume") !in pendingKeys) ||
                (it.extension == "pending" && it.nameWithoutExtension !in pendingKeys) ||
                (it.extension == "audio" && !File(directory, "${it.nameWithoutExtension}.json").isFile)
        }.forEach { it.delete() }
    }

    private fun audioFile(identity: SourceIdentity) = File(directory, "${key(identity)}.audio")
    private fun metadataFile(identity: SourceIdentity) = File(directory, "${key(identity)}.json")
    private fun pendingFile(identity: SourceIdentity) = File(directory, "${key(identity)}.pending")

    private fun key(identity: SourceIdentity): String = MessageDigest.getInstance("SHA-256")
        .digest("${identity.sourceType.name}:${identity.sourceMediaId}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
