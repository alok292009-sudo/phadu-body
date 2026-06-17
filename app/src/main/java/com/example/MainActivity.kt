package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.FirebaseIronLogRepository
import com.example.ui.IronLogApp
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.widget.Toast

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val repo = remember { com.example.data.SharedPrefsIronLogRepository(context) }
                IronLogApp(repository = repo)
            }
        }
    }
}
