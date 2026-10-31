package com.gtv.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

private var managedBackendProcess: Process? = null

actual fun getPlatformName(): String {
    val osName = System.getProperty("os.name") ?: "Desktop"
    return "Desktop ($osName)"
}

actual fun getDefaultApiBaseUrl(): String = "http://127.0.0.1:5000"

actual fun getDefaultDownloadDirectory(): String {
    val userHome = System.getProperty("user.home") ?: "."
    val downloadsDir = File(userHome, "Downloads")
    return if (downloadsDir.exists()) {
        downloadsDir.absolutePath
    } else {
        File(userHome).absolutePath
    }
}

actual suspend fun pickDirectoryDialog(currentDirectory: String): String? = withContext(Dispatchers.IO) {
    var selectedPath: String? = null
    val startDir = File(currentDirectory).takeIf { it.exists() }
        ?: File(getDefaultDownloadDirectory())

    val runnable = Runnable {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }
        val chooser = JFileChooser(startDir).apply {
            dialogTitle = "Selecione a pasta onde o vídeo ou música será salvo"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            selectedPath = chooser.selectedFile.absolutePath
        }
    }

    if (SwingUtilities.isEventDispatchThread()) {
        runnable.run()
    } else {
        SwingUtilities.invokeAndWait(runnable)
    }

    selectedPath
}

actual fun openDirectoryInSystem(path: String): Boolean {
    return try {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(dir)
            true
        } else {
            ProcessBuilder("explorer.exe", dir.absolutePath).start()
            true
        }
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

/**
 * No Desktop JVM, verifica se a API Python (`backend/main.py`) está rodando em [baseUrl].
 * Se não estiver, localiza `backend/main.py` no projeto e inicia o servidor automaticamente!
 */
actual suspend fun ensureLocalBackendRunning(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
    if (isHealthOk(baseUrl)) {
        return@withContext true
    }

    // Procura o arquivo backend/main.py a partir do diretório de execução
    val candidates = listOf(
        File("backend/main.py"),
        File("../backend/main.py"),
        File(System.getProperty("user.dir", "."), "backend/main.py")
    )
    val scriptFile = candidates.firstOrNull { it.exists() } ?: return@withContext false

    try {
        if (managedBackendProcess == null || managedBackendProcess?.isAlive == false) {
            val process = ProcessBuilder("python", scriptFile.absolutePath)
                .directory(scriptFile.parentFile)
                .redirectErrorStream(true)
                .start()

            managedBackendProcess = process
            Runtime.getRuntime().addShutdownHook(Thread {
                if (process.isAlive) {
                    process.destroy()
                }
            })
        }

        // Aguarda até ~4 segundos para o servidor Flask subir na porta 5000
        repeat(8) {
            delay(500)
            if (isHealthOk(baseUrl)) {
                return@withContext true
            }
        }
    } catch (_: Exception) {
    }

    isHealthOk(baseUrl)
}

private fun isHealthOk(baseUrl: String): Boolean {
    return try {
        val cleanBase = baseUrl.trimEnd('/')
        val conn = (URI("$cleanBase/health").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1500
            readTimeout = 1500
        }
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (_: Exception) {
        false
    }
}
