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

class AppContainer(context: Context) {
    val database: MusicDatabase = Room.databaseBuilder(
        context.applicationContext,
        MusicDatabase::class.java,
        MusicDatabase.FILE_NAME,
    ).build()

    val libraryRepository: LibraryRepository = RoomLibraryRepository(database)
    val sessionRepository: SessionRepository = RoomSessionRepository(database)
    val diagnostics = DiagnosticLogger(context.applicationContext)
    private val newPipeRuntime = NewPipeRuntime()
    val searchProvider: SearchProvider = NewPipeSearchProvider(newPipeRuntime, diagnostics)
    val playlistImporter: PlaylistImporter = NewPipePlaylistImporter(newPipeRuntime, diagnostics)
    val audioResolver: AudioResolver = NewPipeAudioResolver(newPipeRuntime, diagnostics)
    val playbackClient: PlaybackClient by lazy { PlaybackClient(context.applicationContext, sessionRepository) }
    val backupManager = BackupManager(
        context.applicationContext,
        RoomBackupStore(database),
        libraryRepository,
        diagnostics,
    )
}
