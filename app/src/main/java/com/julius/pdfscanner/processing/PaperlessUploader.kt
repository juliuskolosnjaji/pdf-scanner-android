package com.julius.pdfscanner.processing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PaperlessUploader {

    suspend fun upload(file: File, serverUrl: String, apiToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundary = "----PdfScannerBoundary${System.currentTimeMillis()}"
                val url = URL("${serverUrl.trimEnd('/')}/api/documents/post_document/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Token $apiToken")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    doOutput = true
                    connectTimeout = 30_000
                    readTimeout = 120_000
                }

                conn.outputStream.buffered().use { out ->
                    fun field(name: String, value: String) {
                        out.write("--$boundary\r\n".toByteArray())
                        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                        out.write(value.toByteArray())
                        out.write("\r\n".toByteArray())
                    }

                    // document file part
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n".toByteArray())
                    out.write("Content-Type: application/pdf\r\n\r\n".toByteArray())
                    file.inputStream().use { it.copyTo(out) }
                    out.write("\r\n".toByteArray())

                    field("title", file.nameWithoutExtension)

                    out.write("--$boundary--\r\n".toByteArray())
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                    throw Exception("Server returned HTTP $code: $body")
                }
            }
        }
}
