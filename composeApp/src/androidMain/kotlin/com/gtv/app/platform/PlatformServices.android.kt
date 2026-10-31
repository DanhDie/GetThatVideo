package com.gtv.app.platform

import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

actual fun getPlatformName(): String = "Android ${Build.VERSION.RELEASE}"

/**
 * No emulador Android, `10.0.2.2` aponta automaticamente para o `127.0.0.1` do computador host!
 */
actual fun getDefaultApiBaseUrl(): String = "http://10.0.2.2:5000"

actual fun getDefaultDownloadDirectory(): String {
    return try {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    } catch (_: Exception) {
        "/storage/emulated/0/Download"
    }
}

actual suspend fun pickDirectoryDialog(currentDirectory: String): String? {
    return currentDirectory.ifBlank { getDefaultDownloadDirectory() }
}

actual fun openDirectoryInSystem(path: String): Boolean {
    return try {
        val dir = File(path)
        dir.exists() || dir.mkdirs()
    } catch (_: Exception) {
        false
    }
}

actual suspend fun performHttpRequest(
    url: String,
    method: String,
    jsonBody: String?,
    timeoutMs: Int
): HttpResponseData = withContext(Dispatchers.IO) {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = method.uppercase()
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        setRequestProperty("Accept", "application/json")
        if (jsonBody != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
    }

    try {
        if (jsonBody != null) {
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        HttpResponseData(statusCode = status, body = responseText)
    } finally {
        connection.disconnect()
    }
}

actual suspend fun ensureLocalBackendRunning(baseUrl: String): Boolean = true
