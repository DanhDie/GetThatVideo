package com.gtv.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gtv.app.ui.App

/**
 * Ponto de entrada Desktop (JVM) do aplicativo Kotlin Multiplatform.
 * Para executar pelo terminal: `./gradlew :composeApp:run`
 */
fun main() = application {
    val windowState = rememberWindowState(width = 960.dp, height = 860.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "GetThatVideo - YouTube Video & MP3 Downloader (Kotlin Multiplatform)"
    ) {
        App()
    }
}
