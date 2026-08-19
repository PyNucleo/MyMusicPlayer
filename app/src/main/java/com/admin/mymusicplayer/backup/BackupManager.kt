package com.admin.mymusicplayer.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import com.admin.mymusicplayer.data.repository.LibraryRepository
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BackupManager(
    context: Context,
    private val store: RoomBackupStore,
    private val libraryRepository: LibraryRepository,
    private val diagnostics: DiagnosticLogger? = null,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
) {
    private val applicationContext = context.applicationContext
    private val resolver: ContentResolver = applicationContext.contentResolver
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _automaticLocation = MutableStateFlow(preferences.getString(AUTOMATIC_TREE_KEY, null))
    val automaticLocation: StateFlow<String?> = _automaticLocation.asStateFlow()
    private var started = false

    @OptIn(FlowPreview::class)
    fun startAutomaticBackups() {
        if (started) return
        started = true
        scope.launch {
            libraryRepository.playlists.drop(1).debounce(AUTOMATIC_DEBOUNCE_MS).collect {
                runCatching { createAutomaticBackup() }
            }
        }
    }

    suspend fun writeManualBackup(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val document = store.export()
        writeDocument(uri, document)
        diagnostics?.log("BACKUP_CREATED", "manual")
        store.validate(document).summary
    }

    suspend fun inspectRestore(uri: Uri): ValidatedBackup = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= MAX_BACKUP_BYTES) { "Backup is larger than 50 MB" }
            }
            output.toByteArray()
        } ?: error("Could not open the selected backup")
        val document = gson.fromJson(bytes.toString(Charsets.UTF_8), PortableBackup::class.java)
            ?: error("Backup JSON is empty")
        store.validate(document)
    }

    suspend fun restore(validated: ValidatedBackup) = withContext(Dispatchers.IO) {
        runCatching { store.restore(validated) }
            .onSuccess { diagnostics?.log("RESTORE_SUCCESS", "${validated.summary.playlistCount} playlists") }
            .onFailure { diagnostics?.log("RESTORE_FAILED", error = it) }
            .getOrThrow()
    }

    fun setAutomaticLocation(uri: Uri) {
        preferences.edit { putString(AUTOMATIC_TREE_KEY, uri.toString()) }
        _automaticLocation.value = uri.toString()
        scope.launch { runCatching { createAutomaticBackup() } }
    }

    private suspend fun createAutomaticBackup() {
        val tree = _automaticLocation.value?.let(Uri::parse) ?: return
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val name = "my-music-player-${formatter.format(Date())}.json"
        val documentUri = DocumentsContract.createDocument(resolver, root, JSON_MIME, name)
            ?: error("Could not create automatic backup")
        writeDocument(documentUri, store.export())
        diagnostics?.log("BACKUP_CREATED", "automatic")
        pruneRollingBackups(tree, rootId)
    }

    private fun writeDocument(uri: Uri, document: PortableBackup) {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            gson.toJson(document, writer)
        } ?: error("Could not write the selected backup")
    }

    private fun pruneRollingBackups(tree: Uri, rootId: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val backups = mutableListOf<DocumentRecord>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                if (name.startsWith("my-music-player-") && name.endsWith(".json")) {
                    backups += DocumentRecord(cursor.getString(idColumn), cursor.getLong(modifiedColumn))
                }
            }
        }
        backups.sortedByDescending { it.modifiedAt }.drop(ROLLING_BACKUP_COUNT).forEach { record ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, record.documentId)
            DocumentsContract.deleteDocument(resolver, uri)
        }
    }

    private data class DocumentRecord(val documentId: String, val modifiedAt: Long)

    private companion object {
        const val JSON_MIME = "application/json"
        const val PREFERENCES = "backup_preferences"
        const val AUTOMATIC_TREE_KEY = "automatic_tree_uri"
        const val ROLLING_BACKUP_COUNT = 10
        const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        const val AUTOMATIC_DEBOUNCE_MS = 2_000L
    }
}
