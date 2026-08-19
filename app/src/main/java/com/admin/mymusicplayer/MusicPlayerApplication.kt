package com.admin.mymusicplayer

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class ShareRequest(val id: Long, val text: String)

class MusicPlayerApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val nextShareId = AtomicLong()
    private val _shareRequests = MutableStateFlow<ShareRequest?>(null)
    val shareRequests: StateFlow<ShareRequest?> = _shareRequests.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.backupManager.startAutomaticBackups()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.sessionRepository.restoreOrResetTransient() }
                .onSuccess {
                    container.diagnostics.log("SESSION_RESTORE_SUCCESS", if (it == null) "empty" else "restored")
                }
                .onFailure {
                    container.diagnostics.log("SESSION_RESTORE_FAILED", error = it)
                    runCatching { container.sessionRepository.clearTransientSession() }
                }
        }
    }

    fun acceptSharedText(text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isNotBlank()) _shareRequests.value = ShareRequest(nextShareId.incrementAndGet(), clean)
    }

    fun consumeShareRequest(id: Long) {
        if (_shareRequests.value?.id == id) _shareRequests.value = null
    }
}
