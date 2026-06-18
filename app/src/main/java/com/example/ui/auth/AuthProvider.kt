package com.example.ui.auth

import androidx.compose.runtime.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.util.Log

data class AuthContext(
    val firebaseApp: FirebaseApp,
    val firebaseAuth: FirebaseAuth,
    val currentUser: FirebaseUser?,
    val isAuthResolved: Boolean,
    val webClientId: String
)

val LocalAuthProvider = staticCompositionLocalOf<AuthContext> {
    error("No AuthProvider found in parent hierarchy!")
}

@Composable
fun AuthProvider(
    webClientId: String = com.example.BuildConfig.WEB_CLIENT_ID,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val firebaseApp = remember { 
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)!!
            } else {
                FirebaseApp.getInstance()
            }
        } catch (e: Exception) {
            Log.e("AuthProvider", "Failed to retrieve/init FirebaseApp", e)
            throw e
        }
    }
    
    val firebaseAuth = remember(firebaseApp) {
        FirebaseAuth.getInstance(firebaseApp)
    }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(firebaseAuth.currentUser) }
    var isAuthResolved by remember { mutableStateOf(false) }

    // Thread-safe listener ensuring we capture early auth resolutions and prevent race conditions
    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            isAuthResolved = true
            Log.d("AuthProvider", "Auth state changed. Resolved User: ${currentUser?.uid ?: "None"}")
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    val authContext = remember(firebaseApp, firebaseAuth, currentUser, isAuthResolved, webClientId) {
        AuthContext(
            firebaseApp = firebaseApp,
            firebaseAuth = firebaseAuth,
            currentUser = currentUser,
            isAuthResolved = isAuthResolved,
            webClientId = webClientId
        )
    }

    CompositionLocalProvider(LocalAuthProvider provides authContext) {
        content()
    }
}
