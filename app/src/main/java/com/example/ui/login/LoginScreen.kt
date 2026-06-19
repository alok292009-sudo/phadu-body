package com.example.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.auth.LocalAuthProvider
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val authContext = LocalAuthProvider.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(IronSpacing.x24).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("IRON LOG", style = IronTypography.LargeTitle)
            Spacer(modifier = Modifier.height(IronSpacing.x8))
            Text("Precision Strength Intelligence", style = IronTypography.Caption.copy(color = TextSecondaryColor))
            
            Spacer(modifier = Modifier.height(IronSpacing.x48))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", style = IronTypography.Caption.copy(color = TextSecondaryColor)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextPrimaryColor,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimaryColor,
                    unfocusedTextColor = TextPrimaryColor,
                    cursorColor = TextPrimaryColor
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(IronSpacing.x16))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", style = IronTypography.Caption.copy(color = TextSecondaryColor)) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextPrimaryColor,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimaryColor,
                    unfocusedTextColor = TextPrimaryColor,
                    cursorColor = TextPrimaryColor
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(IronSpacing.x16))
                Text(errorMessage!!, style = IronTypography.Footnote.copy(color = DestructiveColor))
            }

            Spacer(modifier = Modifier.height(IronSpacing.x32))

            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(IronCorner.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            authContext.firebaseAuth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener { e -> 
                                    errorMessage = e.localizedMessage
                                    isLoading = false
                                }
                        } catch(e: Exception) {
                            errorMessage = e.localizedMessage
                            isLoading = false
                        }
                    }
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = BgColor, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign In", style = IronTypography.Headline)
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x16))
            
            TextButton(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@TextButton
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            authContext.firebaseAuth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener { e -> 
                                    errorMessage = e.localizedMessage
                                    isLoading = false
                                }
                        } catch(e: Exception) {
                            errorMessage = e.localizedMessage
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Create Account", style = IronTypography.Body.copy(color = TextPrimaryColor))
            }
        }
    }
}
