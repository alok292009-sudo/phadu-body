package com.example.ui.error

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.ui.theme.*

data class ErrorHandlerContext(
    val reportError: (Throwable) -> Unit,
    val clearError: () -> Unit
)

val LocalErrorHandler = staticCompositionLocalOf<ErrorHandlerContext> {
    ErrorHandlerContext(
        reportError = { e -> Log.e("LocalErrorHandler", "Unhandled error reported", e) },
        clearError = {}
    )
}

@Composable
fun ErrorBoundary(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var caughtError by remember { mutableStateOf<Throwable?>(null) }
    
    val errorHandlerContext = remember {
        ErrorHandlerContext(
            reportError = { caughtError = it },
            clearError = { caughtError = null }
        )
    }
    
    CompositionLocalProvider(LocalErrorHandler provides errorHandlerContext) {
        if (caughtError != null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D11))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ CRITICAL SECTOR ERROR TRIGGERED",
                        color = Color(0xFFFF453A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "State or Render Exception Gracefully Intercepted",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Exception Type: ${caughtError?.javaClass?.simpleName ?: "Exception"}",
                                color = Color(0xFFFF453A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = caughtError?.message ?: "Unknown/Unresolved State Exception",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Stack Trace Snippet:",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = caughtError?.stackTrace?.take(10)?.joinToString("\n") { it.toString() } ?: "",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { caughtError = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14), contentColor = Color.Black)
                        ) {
                            Text("RETRY STATE / RELOAD", fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                caughtError = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("DISMISS")
                        }
                    }
                }
            }
        } else {
            content()
        }
    }
}
