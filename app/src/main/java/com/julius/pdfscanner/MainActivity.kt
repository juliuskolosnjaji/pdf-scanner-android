package com.julius.pdfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.julius.pdfscanner.navigation.NavGraph
import com.julius.pdfscanner.ui.theme.PdfScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfScannerTheme {
                NavGraph()
            }
        }
    }
}
