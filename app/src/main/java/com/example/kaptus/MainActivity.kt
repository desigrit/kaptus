// app/src/main/java/com/example/kaptus/MainActivity.kt

package com.example.kaptus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kaptus.ui.MainScreen
import com.example.kaptus.ui.theme.KaptusTheme

class MainActivity : ComponentActivity() {
    private var pendingSrtUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSrtUri = intent.srtUri()
        enableEdgeToEdge()
        setContent {
            KaptusTheme {
                MainScreen(
                    initialSrtUri = pendingSrtUri,
                    onInitialSrtConsumed = { pendingSrtUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSrtUri = intent.srtUri()
    }

    private fun Intent?.srtUri(): Uri? =
        this?.data?.takeIf { action == Intent.ACTION_VIEW }
}
