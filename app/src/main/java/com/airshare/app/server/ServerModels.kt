package com.airshare.app.server

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.airshare.app.util.FileUtils
import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    val name: String,
    val size: Long,
    val formattedSize: String,
    val lastModified: String,
    val mimeType: String,
    val downloadUrl: String
)

@Serializable
data class FileListResponse(val files: List<FileInfo>)

@Serializable
data class ErrorResponse(val error: String)

object ServerConfig {
    const val PORT = 8080
    const val BUFFER_SIZE = 1024 * 1024 // 1 MB
}
