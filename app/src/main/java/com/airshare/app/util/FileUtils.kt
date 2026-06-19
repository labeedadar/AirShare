package com.airshare.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

data class FileDocumentInfo(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val uri: Uri
)

object FileUtils {

    fun queryFiles(context: Context, treeUri: Uri): List<FileDocumentInfo> {
        val results = mutableListOf<FileDocumentInfo>()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        ) ?: return results

        cursor.use {
            val docIdIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (it.moveToNext()) {
                val mimeType = it.getString(mimeIdx) ?: ""
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                val childDocId = it.getString(docIdIdx) ?: continue
                results.add(
                    FileDocumentInfo(
                        documentId = childDocId,
                        name = it.getString(nameIdx) ?: "Unknown",
                        mimeType = mimeType,
                        size = it.getLong(sizeIdx),
                        lastModified = it.getLong(modifiedIdx),
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    )
                )
            }
        }
        return results
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return cursor.getString(idx) ?: "file"
                }
            }
        }
        return uri.lastPathSegment ?: "file"
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val groups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.lastIndex)
        return String.format("%.1f %s", bytes / Math.pow(1024.0, groups.toDouble()), units[groups])
    }
}
