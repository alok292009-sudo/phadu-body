package com.example.ui.login

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()
    
    LaunchedEffect(Unit) {
        if (auth.currentUser != null) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GYM KRTA H JI",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "STRENGTH LOG",
            color = Color(0xFFA0A0A0),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 64.dp)
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = com.example.ui.theme.GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = com.example.ui.theme.GlassBorderLight,
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = com.example.ui.theme.GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = com.example.ui.theme.GlassBorderLight,
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isLoading || email.isBlank() || password.isBlank()) return@Button
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            auth.signInWithEmailAndPassword(email, password).await()
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Login failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("SIGN IN", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
            }
            
            Button(
                onClick = {
                    if (isLoading || email.isBlank() || password.isBlank()) return@Button
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            auth.createUserWithEmailAndPassword(email, password).await()
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Signup failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.ui.theme.GlassLight,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f).border(1.dp, com.example.ui.theme.GlassBorderLight, RoundedCornerShape(16.dp))
            ) {
                Text("SIGN UP", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "--- OR ---",
            color = Color(0xFFA0A0A0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                if (isLoading) return@Button
                if (BuildConfig.WEB_CLIENT_ID == "YOUR_WEB_CLIENT_ID_HERE") {
                    errorMessage = "Google Client ID is not configured. Please add your WEB_CLIENT_ID to the Secrets panel in AI Studio or your .env file."
                    return@Button
                }
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val rawNonce = UUID.randomUUID().toString()
                        val bytes = rawNonce.toByteArray()
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(bytes)
                        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                            .setNonce(hashedNonce)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val result = credentialManager.getCredential(context = context, request = request)
                        val credential = result.credential
                        
                        if (credential is androidx.credentials.CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val idToken = googleIdCredential.idToken
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            auth.signInWithCredential(firebaseCredential).await()
                            onLoginSuccess()
                        } else {
                            errorMessage = "Received unexpected credential type."
                        }
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Google Login failed", e)
                        errorMessage = "Google login failed: ${e.message}\n(Emulators may lack a Google Account)"
                    } finally {
                        isLoading = false
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("SIGN IN WITH GOOGLE", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (isLoading) return@Button
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        auth.signInAnonymously().await()
                        onLoginSuccess()
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Anonymous auth failed", e)
                        errorMessage = "Anonymous login failed: ${e.message}\nPlease enable 'Anonymous' sign-in provider in Firebase Console."
                    } finally {
                        isLoading = false
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.example.ui.theme.GlassLight,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, com.example.ui.theme.GlassBorderLight, RoundedCornerShape(16.dp))
        ) {
            Text("CONTINUE AS GUEST", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { onLoginSuccess() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BYPASS (DEV MODE)", color = com.example.ui.theme.GrayMedium, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
