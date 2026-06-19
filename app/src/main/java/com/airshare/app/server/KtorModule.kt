package com.airshare.app.server

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.airshare.app.util.FileUtils
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.OutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Application.ktorModule(
    context: Context,
    treeUri: Uri,
    password: String?,
    onFileChanged: () -> Unit
) {
    val appContext = context.applicationContext

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; prettyPrint = false })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            Timber.e(cause, "Unhandled error")
            call.respondText("Internal error", status = HttpStatusCode.InternalServerError)
        }
    }

    if (!password.isNullOrEmpty()) {
        install(Authentication) {
            basic("auth") {
                realm = "AirShare"
                validate { cred ->
                    if (cred.password == password) UserIdPrincipal(cred.name) else null
                }
            }
        }
    }

    routing {
        staticResources("/", "assets") {
            default("index.html")
        }

        if (!password.isNullOrEmpty()) {
            authenticate("auth") { registerRoutes(appContext, treeUri, onFileChanged) }
        } else {
            registerRoutes(appContext, treeUri, onFileChanged)
        }
    }
}

private fun io.ktor.server.routing.Routing.registerRoutes(
    context: Context,
    treeUri: Uri,
    onFileChanged: () -> Unit
) {
    get("/api/ping") { call.respondText("pong") }

    get("/api/files") {
        try {
            val files = FileUtils.queryFiles(context, treeUri).map { info ->
                val date = Date(info.lastModified)
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
                FileInfo(
                    name = info.name,
                    size = info.size,
                    formattedSize = FileUtils.formatSize(info.size),
                    lastModified = fmt.format(date),
                    mimeType = info.mimeType,
                    downloadUrl = "/api/download/${URLEncoder.encode(info.name, "UTF-8").replace("+", "%20")}"
                )
            }
            call.respond(FileListResponse(files))
        } catch (e: Exception) {
            Timber.e(e, "Error listing files")
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Error"))
        }
    }

    get("/api/download/{name}") {
        val encoded = call.parameters["name"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("Missing filename")
        )
        val fileName = java.net.URLDecoder.decode(encoded, "UTF-8")
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(fileName), null
        )
        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
        val childDocId = cursor.getString(0)
        val mime = cursor.getString(1) ?: "application/octet-stream"
        val size = cursor.getLong(2)
        cursor.close()

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
        val stream = context.contentResolver.openInputStream(fileUri)
            ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Cannot open"))

        try {
            call.respond(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.parse(mime)
                override val contentLength = size.takeIf { it > 0 }
                override val headers: Headers = headersOf(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
                )

                override suspend fun writeTo(channel: ByteWriteChannel) {
                    withContext(Dispatchers.IO) {
                        stream.use { input ->
                            val buf = ByteArray(ServerConfig.BUFFER_SIZE)
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                channel.writeFully(buf, 0, n)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "Download error: $fileName")
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Download failed"))
        } finally {
            stream.close()
        }
    }

    put("/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@put call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("Missing filename")
        )
        val (name, error) = handleUpload(context, treeUri, fileName, { call.receiveChannel() }, onFileChanged)
        if (name != null) call.respond(HttpStatusCode.Created, "Uploaded: $name")
        else call.respond(HttpStatusCode.InternalServerError, ErrorResponse(error ?: "Failed"))
    }

    post("/api/upload") {
        var count = 0
        val names = mutableListOf<String>()
        try {
            val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val originalName = part.originalFileName ?: "upload"
                    val (name, error) = handleUpload(context, treeUri, originalName, { part.provider() }, onFileChanged)
                    if (name != null) { names.add(name); count++ }
                    else Timber.e("Upload failed: $originalName - $error")
                }
                part.dispose()
            }
            if (count > 0) call.respondText("Uploaded: ${names.joinToString(", ")}")
            else call.respond(HttpStatusCode.BadRequest, ErrorResponse("No files uploaded"))
        } catch (e: Exception) {
            Timber.e(e, "Upload error")
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Upload failed: ${e.message}"))
        }
    }

    delete("/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@delete call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("Missing filename")
        )
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(fileName), null
        )
        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
        val childDocId = cursor.getString(0)
        cursor.close()

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
        if (context.contentResolver.delete(fileUri, null, null) > 0) {
            onFileChanged()
            call.respondText("Deleted: $fileName")
        } else {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Delete failed"))
        }
    }

    get("/api/zip") {
        serveZip(call, context, treeUri, null)
    }

    post("/api/zip") {
        val params = call.receiveParameters().getAll("f")
        serveZip(call, context, treeUri, params)
    }

    put("/api/clipboard") {
        val body = call.receiveText()
        if (body.isBlank()) return@put call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("Empty body")
        )
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        var attempt = 0
        var fileName = "paste.txt"
        var created = false
        while (!created && attempt < 100) {
            val test = if (attempt == 0) fileName else "paste_${attempt + 1}.txt"
            val result = DocumentsContract.createDocument(
                context.contentResolver, childrenUri, "text/plain", test
            )
            if (result != null) {
                context.contentResolver.openOutputStream(result)?.use { it.write(body.toByteArray()) }
                fileName = test
                created = true
            } else attempt++
        }
        if (created) {
            onFileChanged()
            call.respondText("Saved as $fileName")
        } else {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create file"))
        }
    }
}

private suspend fun handleUpload(
    context: Context,
    treeUri: Uri,
    originalName: String,
    channelProvider: suspend () -> ByteReadChannel,
    onFileChanged: () -> Unit
): Pair<String?, String?> {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

    val sanitized = originalName.replace(Regex("[\\\\/<>:\"|?*\\s]+"), "_")
    val baseName = sanitized.substringBeforeLast('.', sanitized)
    val ext = sanitized.substringAfterLast('.', "")

    var name = if (ext.isNotEmpty()) "$baseName.$ext" else baseName
    var attempt = 0
    while (attempt < 100) {
        val test = if (attempt == 0) name else "${baseName}_${attempt + 1}.$ext"
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(test), null
        )
        val exists = cursor?.use { it.moveToFirst() } == true
        if (!exists) { name = test; break }
        attempt++
    }

    val fileUri = DocumentsContract.createDocument(
        context.contentResolver, childrenUri, "application/octet-stream", name
    ) ?: return null to "Failed to create file"

    return try {
        context.contentResolver.openOutputStream(fileUri)?.use { output ->
            val channel = channelProvider()
            val buf = ByteArray(ServerConfig.BUFFER_SIZE)
            try {
                while (true) {
                    channel.readFully(buf)
                    output.write(buf)
                }
            } catch (_: Exception) { }
        } ?: return null to "Cannot open output stream"
        onFileChanged()
        name to null
    } catch (e: Exception) {
        context.contentResolver.delete(fileUri, null, null)
        Timber.e(e, "Upload error: $name")
        null to e.message
    }
}

private suspend fun serveZip(
    call: io.ktor.server.routing.RoutingCall,
    context: Context,
    treeUri: Uri,
    requestedNames: List<String>?
) {
    val allFiles = FileUtils.queryFiles(context, treeUri)
    val files = if (requestedNames.isNullOrEmpty()) allFiles
    else allFiles.filter { it.name in requestedNames }

    if (files.isEmpty()) {
        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No files"))
        return
    }

    val zipName = "airshare_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip"
    call.respond(object : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Zip
        override val headers: Headers = headersOf(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, zipName).toString()
        )

        override suspend fun writeTo(channel: ByteWriteChannel) {
            withContext(Dispatchers.IO) {
                val zipOut = ZipOutputStream(ByteWriteChannelOutputStream(channel))
                zipOut.use { zip ->
                    val buf = ByteArray(ServerConfig.BUFFER_SIZE)
                    for (file in files) {
                        zip.putNextEntry(ZipEntry(file.name))
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                zip.write(buf, 0, n)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    })
}

private class ByteWriteChannelOutputStream(private val channel: ByteWriteChannel) : OutputStream() {
    private val buf = ByteArray(8192)
    private var pos = 0

    override fun write(b: Int) {
        buf[pos++] = b.toByte()
        if (pos == buf.size) flush()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var written = 0
        while (written < len) {
            val chunk = minOf(len - written, buf.size - pos)
            System.arraycopy(b, off + written, buf, pos, chunk)
            pos += chunk
            written += chunk
            if (pos == buf.size) flush()
        }
    }

    override fun flush() {
        if (pos > 0) {
            runBlocking { channel.writeFully(buf, 0, pos) }
            pos = 0
        }
    }

    override fun close() {
        flush()
    }
}
