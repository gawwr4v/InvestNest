package com.gourav.investnest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gourav.investnest.ui.theme.InvestNestTheme
import dagger.hilt.android.AndroidEntryPoint

// this annotation is required for hilt to inject dependencies into this activity
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // enables drawing content under system bars for a modern full screen look
        enableEdgeToEdge()
        
        // setcontent defines the ui of our activity using jetpack compose
        setContent {
            // applying the custom app theme for consistent colors and fonts
            InvestNestTheme {
                // the main entry point for the entire application ui and navigation
                InvestNestApp()
            }
        }
    }
}
