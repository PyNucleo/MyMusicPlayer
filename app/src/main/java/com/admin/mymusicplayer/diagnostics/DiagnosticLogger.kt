package com.admin.mymusicplayer.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import com.admin.mymusicplayer.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DiagnosticEvent(
    val timestampEpochMs: Long = 0,
    val type: String? = null,
    val message: String? = null,
    val errorClass: String? = null,
)

private data class DiagnosticBundle(
    val formatVersion: Int,
    val createdAtEpochMs: Long,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val gitCommit: String,
    val databaseVersion: Int,
    val media3Version: String,
    val newPipeVersion: String,
    val androidSdk: Int,
    val device: String,
    val events: List<DiagnosticEvent>,
)

class DiagnosticLogger(context: Context) {
    private val applicationContext = context.applicationContext
    private val file = File(applicationContext.filesDir, "diagnostics/events.ndjson")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val compactGson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(type: String, message: String? = null, error: Throwable? = null) {
        scope.launch {
            mutex.withLock {
                val event = DiagnosticEvent(
                    timestampEpochMs = System.currentTimeMillis(),
                    type = type.take(MAX_TYPE_LENGTH),
                    message = (message ?: error?.message)?.replace(Regex("[\\r\\n]+"), " ")
                        ?.take(MAX_MESSAGE_LENGTH),
                    errorClass = error?.javaClass?.simpleName?.take(MAX_TYPE_LENGTH),
                )
                val events = (readEventsUnlocked() + event).takeLast(MAX_EVENTS)
                file.parentFile?.mkdirs()
                file.bufferedWriter(Charsets.UTF_8).use { writer ->
                    events.forEach { writer.appendLine(compactGson.toJson(it)) }
                }
            }
        }
    }

    suspend fun lastError(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readEventsUnlocked().lastOrNull { it.errorClass != null || it.type?.endsWith("FAILED") == true }
                ?.let { event -> "${event.type}: ${event.message ?: event.errorClass.orEmpty()}" }
        }
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val events = mutex.withLock { readEventsUnlocked() }
        val bundle = DiagnosticBundle(
            formatVersion = 1,
            createdAtEpochMs = System.currentTimeMillis(),
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            gitCommit = BuildConfig.GIT_COMMIT,
            databaseVersion = BuildConfig.DATABASE_VERSION,
            media3Version = BuildConfig.MEDIA3_VERSION,
            newPipeVersion = BuildConfig.NEWPIPE_VERSION,
            androidSdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            events = events,
        )
        applicationContext.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter(Charsets.UTF_8)?.use { gson.toJson(bundle, it) }
            ?: error("Could not write diagnostic bundle")
    }

    private fun readEventsUnlocked(): List<DiagnosticEvent> {
        if (!file.isFile) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching { compactGson.fromJson(line, DiagnosticEvent::class.java) }.getOrNull()
            }.take(MAX_EVENTS).toList()
        }
    }

    private companion object {
        const val MAX_EVENTS = 500
        const val MAX_MESSAGE_LENGTH = 500
        const val MAX_TYPE_LENGTH = 80
    }
}
