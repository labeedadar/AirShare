package com.airshare.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airshare.app.R
import com.airshare.app.server.ServerService
import com.airshare.app.server.ServerState
import com.airshare.app.util.FileDocumentInfo
import com.airshare.app.util.FileUtils
import com.airshare.app.util.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application
) : AndroidViewModel(app) {

    private val _files = MutableStateFlow<List<FileDocumentInfo>>(emptyList())
    val files: StateFlow<List<FileDocumentInfo>> = _files.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Idle)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private var serverService: ServerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ServerService.LocalBinder
            serverService = binder.getService()
            bound = true

            viewModelScope.launch {
                serverService?.state?.collect { _serverState.value = it }
            }
            viewModelScope.launch {
                serverService?.fileChanged?.collect { changed ->
                    if (changed) {
                        _selectedUri.value?.let { loadFiles(it) }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverService = null
            bound = false
        }
    }

    init {
        viewModelScope.launch {
            val uri = Prefs.getFolderUri(app)
            _selectedUri.value = uri
            if (uri != null) loadFiles(uri)
        }
    }

    fun bindService() {
        Intent(app, ServerService::class.java).also {
            app.bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        if (bound) {
            app.unbindService(connection)
            bound = false
        }
    }

    fun selectFolder(uri: Uri) {
        val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        app.contentResolver.takePersistableUriPermission(uri, flag)
        viewModelScope.launch {
            Prefs.setFolderUri(app, uri)
            _selectedUri.value = uri
            loadFiles(uri)
        }
    }

    fun loadFiles(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val list = try {
                FileUtils.queryFiles(app, uri)
            } catch (e: Exception) {
                emptyList()
            }
            _files.value = list
            _isLoading.value = false
        }
    }

    fun startServer() {
        val uri = _selectedUri.value
        if (uri == null) {
            Toast.makeText(app, R.string.no_folder_selected, Toast.LENGTH_SHORT).show()
            return
        }
        Intent(app, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_URI, uri.toString())
        }.also { app.startForegroundService(it) }
    }

    fun stopServer() {
        Intent(app, ServerService::class.java).apply {
            action = ServerService.ACTION_STOP
        }.also { app.startService(it) }
    }

    fun deleteFiles(files: List<FileDocumentInfo>) {
        val uri = _selectedUri.value ?: return
        viewModelScope.launch {
            var deleted = 0
            files.forEach { file ->
                try {
                    if (app.contentResolver.delete(file.uri, null, null) > 0) deleted++
                } catch (_: Exception) {}
            }
            Toast.makeText(app, app.getString(R.string.files_deleted, deleted), Toast.LENGTH_SHORT).show()
            loadFiles(uri)
        }
    }

    fun pasteClipboard() {
        val uri = _selectedUri.value
        if (uri == null) {
            Toast.makeText(app, R.string.no_folder_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()
        if (text.isNullOrEmpty()) {
            Toast.makeText(app, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
            val result = android.provider.DocumentsContract.createDocument(
                app.contentResolver, childrenUri, "text/plain", "paste.txt"
            )
            if (result != null) {
                app.contentResolver.openOutputStream(result)?.use { it.write(text.toByteArray()) }
                Toast.makeText(app, app.getString(R.string.text_pasted, "paste.txt"), Toast.LENGTH_SHORT).show()
                loadFiles(uri)
            }
        }
    }

    override fun onCleared() {
        unbindService()
        super.onCleared()
    }
}
