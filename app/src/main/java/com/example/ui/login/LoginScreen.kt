package com.example.ui.login

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

enum class AuthMode {
    Landing,
    EmailLogin,
    CreateAccount,
    ForgotPassword
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val auth = FirebaseAuth.getInstance()

    // Screen State
    var authMode by remember { mutableStateOf(AuthMode.Landing) }
    
    // Inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    
    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser != null) {
            onLoginSuccess()
        }
    }

    // Dynamic background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D11),
                        Color(0xFF000000)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Upper segment: Logo (Barbell) and App branding
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 28.dp)
                        .background(AccentGreen, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 20.dp)
                        .background(AccentGreen, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 20.dp)
                        .background(AccentGreen, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 28.dp)
                        .background(AccentGreen, RoundedCornerShape(2.dp))
                )
            }

            Text(
                text = "GYM KRTA H JI",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "PRECISION STRENGTH INTELLIGENCE",
                color = AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
                textAlign = TextAlign.Center
            )

            // Dynamic Form area
            AnimatedContent(
                targetState = authMode,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.95f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.95f))
                },
                label = "auth_screen_flow"
            ) { mode ->
                when (mode) {
                    AuthMode.Landing -> {
                        LandingContent(
                            onEmailLoginClick = {
                                errorMessage = null
                                infoMessage = null
                                authMode = AuthMode.EmailLogin
                            },
                            onCreateAccountClick = {
                                errorMessage = null
                                infoMessage = null
                                authMode = AuthMode.CreateAccount
                            },
                            onGoogleSignInClick = {
                                if (isLoading) return@LandingContent
                                isLoading = true
                                errorMessage = null
                                infoMessage = null

                                coroutineScope.launch {
                                    try {
                                        Log.d("LoginScreen", "=== GOOGLE SIGN-IN AUDIT START ===")
                                        Log.d("LoginScreen", "Step 1: Checking client registration context")
                                        Log.d("LoginScreen", "Application Package Name: ${context.packageName}")
                                        
                                        // Auto-fallback with direct registration client ID if BuildConfig is missing or default
                                        val webClientId = if (BuildConfig.WEB_CLIENT_ID.isNullOrBlank() || 
                                            BuildConfig.WEB_CLIENT_ID == "YOUR_WEB_CLIENT_ID_HERE" ||
                                            BuildConfig.WEB_CLIENT_ID == "null") {
                                            val fallbackId = "722107466473-vma08f91datb7nl89c3thnokutfkfhv3.apps.googleusercontent.com"
                                            Log.d("LoginScreen", "Step 2 [WARN]: WEB_CLIENT_ID is unconfigured in Secrets panel. Falling back to registered Firebase client ID: $fallbackId")
                                            fallbackId
                                        } else {
                                            Log.d("LoginScreen", "Step 2: Authenticating with Secrets Panel Client ID: ${BuildConfig.WEB_CLIENT_ID}")
                                            BuildConfig.WEB_CLIENT_ID
                                        }
                                        
                                        Log.d("LoginScreen", "Step 3: Initializing CredentialManager helper")
                                        val credentialManager = CredentialManager.create(context)
                                        
                                        Log.d("LoginScreen", "Step 4: Generating cryptographic Nonce")
                                        val rawNonce = UUID.randomUUID().toString()
                                        val bytes = rawNonce.toByteArray()
                                        val md = MessageDigest.getInstance("SHA-256")
                                        val digest = md.digest(bytes)
                                        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }
                                        Log.d("LoginScreen", "Nonce SHA-256 Hash Generated: $hashedNonce")

                                        Log.d("LoginScreen", "Step 5: Building GetGoogleIdOption")
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(webClientId)
                                            .setNonce(hashedNonce)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        Log.d("LoginScreen", "Step 6: Executing Credential Request with user choices...")
                                        val result = credentialManager.getCredential(context = context, request = request)
                                        val credential = result.credential
                                        Log.d("LoginScreen", "Step 7: Callback returned credential of type: ${credential.type}")
                                        
                                        if (credential is androidx.credentials.CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            Log.d("LoginScreen", "Step 8: Extraction of GoogleIdTokenCredential from Bundle...")
                                            val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            val idToken = googleIdCredential.idToken
                                            Log.d("LoginScreen", "Google ID Token acquired. Length: ${idToken.length}")
                                            
                                            Log.d("LoginScreen", "Step 9: Attaining credentials via GoogleAuthProvider")
                                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                                            
                                            Log.d("LoginScreen", "Step 10: Authenticating on Firebase Authentication servers...")
                                            val authResult = auth.signInWithCredential(firebaseCredential).await()
                                            Log.d("LoginScreen", "Firebase Auth success! User UID: ${authResult.user?.uid}, Email: ${authResult.user?.email}")
                                            
                                            Toast.makeText(context, "Google sign-in successful! 🎯", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = "Unsupported credential format received: ${credential.type}"
                                            Log.e("LoginScreen", "Google Sign-In mismatch - Type: ${credential.type}")
                                        }
                                        Log.d("LoginScreen", "=== GOOGLE SIGN-IN AUDIT COMPLETED ===")
                                    } catch (ex: GetCredentialCancellationException) {
                                        Log.w("LoginScreen", "Google Sign-In cancelled by user action")
                                        errorMessage = "Sign-in cancelled by user."
                                    } catch (ex: Exception) {
                                        val name = ex.javaClass.simpleName
                                        Log.e("LoginScreen", "CredentialManager encountered fatal error. Type: $name", ex)
                                        
                                        if (name == "GetCredentialNetworkException") {
                                            errorMessage = "Network connection failed. Verify internet connection and try again."
                                        } else if (ex is GetCredentialException) {
                                            errorMessage = "Authentication issue (${ex.type}): ${ex.message}\n(Diagnostic code: ${ex.javaClass.name})"
                                        } else {
                                            val debugMessage = ex.message ?: "Unknown Exception"
                                            errorMessage = "Google Sign-In failed: $debugMessage\n(Verify Firebase Console is configured with the correct SHA-1 fingerprint for package \"${context.packageName}\")"
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            onGuestClick = {
                                if (isLoading) return@LandingContent
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                coroutineScope.launch {
                                    try {
                                        auth.signInAnonymously().await()
                                        Toast.makeText(context, "Logged in as Guest! 👤", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    } catch (ex: Exception) {
                                        errorMessage = "Guest entry failed: ${ex.message}. Ensure anonymous login is enabled in Firebase."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }

                    AuthMode.EmailLogin -> {
                        EmailLoginContent(
                            email = email,
                            onEmailChange = { email = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isPasswordVisible = isPasswordVisible,
                            onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                            onForgotPasswordClick = {
                                errorMessage = null
                                infoMessage = null
                                authMode = AuthMode.ForgotPassword
                            },
                            onBackClick = {
                                authMode = AuthMode.Landing
                            },
                            onSignInClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter both Email and Password."
                                    return@EmailLoginContent
                                }
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                focusManager.clearFocus()
                                
                                coroutineScope.launch {
                                    try {
                                        auth.signInWithEmailAndPassword(email.trim(), password).await()
                                        Toast.makeText(context, "Welcome back! 💪", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    } catch (ex: Exception) {
                                        errorMessage = "Login failed: ${ex.localizedMessage ?: ex.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }

                    AuthMode.CreateAccount -> {
                        CreateAccountContent(
                            email = email,
                            onEmailChange = { email = it },
                            password = password,
                            onPasswordChange = { password = it },
                            confirmPassword = confirmPassword,
                            onConfirmPasswordChange = { confirmPassword = it },
                            displayName = displayNameInput,
                            onDisplayNameChange = { displayNameInput = it },
                            isPasswordVisible = isPasswordVisible,
                            onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                            onBackClick = {
                                authMode = AuthMode.Landing
                            },
                            onSignUpClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please fill in email and password."
                                    return@CreateAccountContent
                                }
                                if (password != confirmPassword) {
                                    errorMessage = "Passwords do not match."
                                    return@CreateAccountContent
                                }
                                if (password.length < 6) {
                                    errorMessage = "Password must be at least 6 characters."
                                    return@CreateAccountContent
                                }
                                
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                focusManager.clearFocus()

                                coroutineScope.launch {
                                    try {
                                        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                                        val user = result.user
                                        
                                        // Set display name if provided
                                        if (user != null && displayNameInput.isNotBlank()) {
                                            val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                                                displayName = displayNameInput.trim()
                                            }
                                            user.updateProfile(profileUpdates).await()
                                        }
                                        
                                        // Attempt Email Verification trigger
                                        try {
                                            user?.sendEmailVerification()?.await()
                                            infoMessage = "Account created! A verification link was sent to your email."
                                            Toast.makeText(context, "Account created! Verification email sent.", Toast.LENGTH_LONG).show()
                                        } catch (exMail: Exception) {
                                            Log.e("LoginScreen", "Failed to send verification email", exMail)
                                            infoMessage = "Account successfully registered! Proceeding to Home."
                                        }
                                        
                                        onLoginSuccess()
                                    } catch (ex: Exception) {
                                        errorMessage = "Registration failed: ${ex.localizedMessage ?: ex.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }

                    AuthMode.ForgotPassword -> {
                        ForgotPasswordContent(
                            email = email,
                            onEmailChange = { email = it },
                            onBackClick = {
                                authMode = AuthMode.EmailLogin
                            },
                            onSendResetClick = {
                                if (email.isBlank()) {
                                    errorMessage = "Please enter your registered email address."
                                    return@ForgotPasswordContent
                                }
                                isLoading = true
                                errorMessage = null
                                infoMessage = null
                                focusManager.clearFocus()

                                coroutineScope.launch {
                                    try {
                                        auth.sendPasswordResetEmail(email.trim()).await()
                                        infoMessage = "Password reset instructions sent! Please check your email inbox."
                                        Toast.makeText(context, "Reset link dispatched!", Toast.LENGTH_SHORT).show()
                                    } catch (ex: Exception) {
                                        errorMessage = "Error sending reset email: ${ex.localizedMessage ?: ex.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            isLoading = isLoading
                        )
                    }
                }
            }

            // Error Message Display
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF453A)),
                    border = BorderStroke(1.dp, Color(0x66FF453A))
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF453A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Info / Success Message Display
            if (infoMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x2234C759)),
                    border = BorderStroke(1.dp, Color(0x5534C759))
                ) {
                    Text(
                        text = infoMessage!!,
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            // Footer - Legal Terms and conditions
            Text(
                text = "By continuing, you agree to our Terms of Service & Privacy Policy.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
fun LandingContent(
    onEmailLoginClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onGuestClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary: Google Sign In
        Button(
            onClick = onGoogleSignInClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Minimal Google G Logo representation
                Text(
                    text = "G ",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    text = "Continue with Google",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Secondary: Email login
        OutlinedButton(
            onClick = onEmailLoginClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.2.dp, Color.White),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(
                text = "Continue with Email",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        // Tertiary: Create account
        Button(
            onClick = onCreateAccountClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GlassDark,
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, GlassBorderDark),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(
                text = "Create Account",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Guest Entry Accessor
        Text(
            text = "Continue as Guest",
            color = AccentGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .clickable(enabled = !isLoading) { onGuestClick() }
                .padding(8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailLoginContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignInClick: () -> Unit,
    onBackClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Welcome Back",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Email address field
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle, enabled = !isLoading) {
                    val icon = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    Icon(icon, contentDescription = "Toggle password visibility", tint = GrayMedium)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSignInClick() }
            ),
            enabled = !isLoading
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Forgot Password?",
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onForgotPasswordClick() }
                    .padding(4.dp)
            )
        }

        // SIGN IN Button
        Button(
            onClick = onSignInClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "SIGN IN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    onBackClick: () -> Unit,
    onSignUpClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Create Account",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Display Name (Optional)
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Email address field
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password (Min 6 chars)", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle, enabled = !isLoading) {
                    val icon = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    Icon(icon, contentDescription = "Toggle password visibility", tint = GrayMedium)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Password field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSignUpClick() }
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(28.dp))

        // SIGN UP Button
        Button(
            onClick = onSignUpClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "REGISTER ACCOUNT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordContent(
    email: String,
    onEmailChange: (String) -> Unit,
    onSendResetClick: () -> Unit,
    onBackClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Reset Password",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = "Enter your registered email address below, and we will send you instructions to safely reset your password.",
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 24.dp, start = 4.dp, end = 4.dp),
            textAlign = TextAlign.Start
        )

        // Email address field
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email Address", color = GrayMedium) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSendResetClick() }
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Reset dispatch Button
        Button(
            onClick = onSendResetClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "SEND RESET LINK",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
