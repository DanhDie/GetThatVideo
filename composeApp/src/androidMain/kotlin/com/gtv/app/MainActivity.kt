package com.gtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gtv.app.ui.App

/**
 * Activity principal do aplicativo Android.
 * Carrega a mesma interface compartilhada [App] definida em `commonMain`!
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
