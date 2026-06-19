package com.airshare.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.airshare.app.R
import com.airshare.app.ui.MainActivity
import com.airshare.app.util.Prefs
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface

sealed class ServerState {
    data object Idle : ServerState()
    data object Starting : ServerState()
    data class Running(val ip: String, val port: Int) : ServerState()
    data class Error(val message: String) : ServerState()
}

class ServerService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: EmbeddedServer<*, *>? = null

    private val _state = MutableStateFlow<ServerState>(ServerState.Idle)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _ipPermission = MutableStateFlow<String?>(null)
    val ipPermission: StateFlow<String?> = _ipPermission.asStateFlow()

    private val _fileChanged = MutableStateFlow(false)
    val fileChanged: StateFlow<Boolean> = _fileChanged.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ServerService = this@ServerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val uri = intent.getStringExtra(EXTRA_URI) ?: run {
                    _state.value = ServerState.Error("No folder URI")
                    return START_NOT_STICKY
                }
                startServer(Uri.parse(uri))
            }
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    fun startServer(folderUri: Uri) {
        if (engine != null) stopServer()
        _state.value = ServerState.Starting
        updateNotification()

        scope.launch {
            try {
                val ip = getLocalIp() ?: run {
                    _state.value = ServerState.Error("No network")
                    updateNotification()
                    return@launch
                }

                val password = Prefs.getPassword(this@ServerService)

                engine = embeddedServer(CIO, port = ServerConfig.PORT, host = "0.0.0.0") {
                    ktorModule(this@ServerService, folderUri, password) {
                        _fileChanged.value = true
                    }
                }.apply {
                    start(wait = false)
                }

                kotlinx.coroutines.delay(2000)

                try {
                    val url = java.net.URL("http://127.0.0.1:${ServerConfig.PORT}/api/ping")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code != 200) throw Exception("Server responded with $code")
                } catch (check: Exception) {
                    Timber.e(check, "Server health check failed")
                    engine?.stop(500, 1000)
                    engine = null
                    _state.value = ServerState.Error("Server failed to bind: ${check.message}")
                    updateNotification()
                    return@launch
                }

                _state.value = ServerState.Running(ip, ServerConfig.PORT)
                updateNotification()
                Timber.i("Server started on $ip:${ServerConfig.PORT}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start server")
                _state.value = ServerState.Error(e.message ?: "Unknown error")
                updateNotification()
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                engine?.stop(500, 1000)
            } catch (e: Exception) {
                Timber.e(e, "Error stopping server")
            }
            engine = null
            _state.value = ServerState.Idle
            updateNotification()
        }
    }

    fun notifyFileChanged() {
        _fileChanged.value = true
    }

    private fun getLocalIp(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (iface.isLoopback || !iface.isUp) return@forEach
                iface.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting IP")
        }
        return null
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, "Server", NotificationManager.IMPORTANCE_LOW).apply {
                description = "File server status"
            }
        )
    }

    private fun updateNotification() {
        val (title, text) = when (val s = _state.value) {
            is ServerState.Running -> "AirShare" to "Serving on ${s.ip}:${s.port}"
            is ServerState.Starting -> "AirShare" to "Starting…"
            is ServerState.Error -> "AirShare" to "Error: ${s.message}"
            is ServerState.Idle -> "AirShare" to "Stopped"
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SERVER)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        scope.launch { engine?.stop(500, 1000) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.airshare.app.START"
        const val ACTION_STOP = "com.airshare.app.STOP"
        const val EXTRA_URI = "folder_uri"
        const val CHANNEL_SERVER = "server_channel"
        const val NOTIFICATION_ID = 1
    }
}
