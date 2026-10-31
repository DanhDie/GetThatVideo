package com.gtv.app.platform

actual fun getPlatformName(): String = "iOS"

actual fun getDefaultApiBaseUrl(): String = "http://127.0.0.1:5000"

actual fun getDefaultDownloadDirectory(): String = "/Users/Shared/Downloads"

actual suspend fun pickDirectoryDialog(currentDirectory: String): String? = currentDirectory

actual fun openDirectoryInSystem(path: String): Boolean = true

actual suspend fun performHttpRequest(
    url: String,
    method: String,
    jsonBody: String?,
    timeoutMs: Int
): HttpResponseData {
    return HttpResponseData(statusCode = 501, body = "{\"error\": \"Configure NSURLSession on iOS\"}")
}

actual suspend fun ensureLocalBackendRunning(baseUrl: String): Boolean = false
