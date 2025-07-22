// app/src/main/java/com/example/kaptus/MainActivity.kt

package com.example.kaptus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.kaptus.ui.MainScreen
import com.example.kaptus.ui.theme.KaptusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaptusTheme {
                // The Scaffold from here has been removed.
                // MainScreen now provides the single, correct Scaffold for the app.
                MainScreen()
            }
        }
    }
}