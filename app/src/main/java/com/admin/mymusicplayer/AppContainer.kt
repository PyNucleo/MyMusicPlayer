package com.admin.mymusicplayer

import android.content.Context
import androidx.room.Room
import com.admin.mymusicplayer.backup.BackupManager
import com.admin.mymusicplayer.backup.RoomBackupStore
import com.admin.mymusicplayer.data.database.MusicDatabase
import com.admin.mymusicplayer.data.repository.LibraryRepository
import com.admin.mymusicplayer.data.repository.RoomLibraryRepository
import com.admin.mymusicplayer.data.repository.RoomSessionRepository
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.playback.PlaybackClient
import com.admin.mymusicplayer.importer.PlaylistImporter
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.search.SearchProvider
import com.admin.mymusicplayer.source.newpipe.NewPipeAudioResolver
import com.admin.mymusicplayer.source.newpipe.NewPipeRuntime
import com.admin.mymusicplayer.source.newpipe.NewPipeSearchProvider
import com.admin.mymusicplayer.source.newpipe.NewPipePlaylistImporter
import com.admin.mymusicplayer.downloads.DownloadRepository
import com.admin.mymusicplayer.downloads.OfflineAudioStore
import com.admin.mymusicplayer.downloads.OfflineFirstAudioResolver
import java.io.File

class AppContainer(context: Context) {
    val database: MusicDatabase = Room.databaseBuilder(
        context.applicationContext,
        MusicDatabase::class.java,
        MusicDatabase.FILE_NAME,
    ).addMigrations(MusicDatabase.MIGRATION_1_2).build()

    val libraryRepository: LibraryRepository = RoomLibraryRepository(database)
    val sessionRepository: SessionRepository = RoomSessionRepository(database)
    val diagnostics = DiagnosticLogger(context.applicationContext)
    private val newPipeRuntime = NewPipeRuntime()
    val searchProvider: SearchProvider = NewPipeSearchProvider(newPipeRuntime, diagnostics)
    val playlistImporter: PlaylistImporter = NewPipePlaylistImporter(newPipeRuntime, diagnostics)
    private val networkAudioResolver: AudioResolver = NewPipeAudioResolver(newPipeRuntime, diagnostics)
    val offlineAudioStore = OfflineAudioStore(File(context.filesDir, "offline-audio"))
    val downloads = DownloadRepository(offlineAudioStore, networkAudioResolver, diagnostics = diagnostics)
    val audioResolver: AudioResolver = OfflineFirstAudioResolver(offlineAudioStore, networkAudioResolver)
    val playbackClient: PlaybackClient by lazy { PlaybackClient(context.applicationContext, sessionRepository) }
    val backupManager = BackupManager(
        context.applicationContext,
        RoomBackupStore(database),
        libraryRepository,
        diagnostics,
    )
}
