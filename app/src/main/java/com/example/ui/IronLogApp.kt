package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import com.example.ui.theme.bounceClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.IronLogRepository
import com.example.ui.home.HomeScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.login.LoginScreen
import com.example.ui.progress.ProgressScreen
import com.example.ui.workout.ActiveWorkoutScreen
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign

@Composable
fun IronLogApp(repository: IronLogRepository) {
    com.example.ui.error.ErrorBoundary {
        val navController = rememberNavController()
        val authContext = com.example.ui.auth.LocalAuthProvider.current
        val auth = authContext.firebaseAuth
        var isInitialized by remember { mutableStateOf(false) }
        var startDestination by remember { mutableStateOf("login") }
        var currentPhase by remember { mutableStateOf("Splash Screen") }
        var initErrorLog by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(authContext.isAuthResolved) {
            if (!authContext.isAuthResolved) return@LaunchedEffect
            Log.d("IronLogApp", "=== APP STARTUP SEQUENCE INITIATED ===")
            try {
                // Step 1: Splash Screen
                currentPhase = "Splash Screen"
                Log.d("IronLogApp", "[Startup Phase 1] Splash Screen starting")
                kotlinx.coroutines.delay(600L) // Visual duration

                // Step 2: Initialize Firebase
                currentPhase = "Initialize Firebase"
                Log.d("IronLogApp", "[Startup Phase 2] Initializing Firebase Core...")
                val app = auth.app

                // Step 3: Initialize Authentication
                currentPhase = "Initialize Authentication"
                Log.d("IronLogApp", "[Startup Phase 3] Initializing Firebase Authentication...")
                if (auth.app != app) {
                    throw IllegalStateException("Firebase Auth disassociated from active Firebase App!")
                }

                // Step 4: Verify Firebase Configuration
                currentPhase = "Verify Firebase Configuration"
                Log.d("IronLogApp", "[Startup Phase 4] Verifying Configuration...")
                if (app.options.applicationId.isEmpty()) {
                    throw IllegalStateException("Firebase Application ID is unconfigured or null!")
                }

                // Step 5: Load User
                currentPhase = "Load User"
                Log.d("IronLogApp", "[Startup Phase 5] Resolving Current Authenticated Session...")
                val currentUser = authContext.currentUser
                Log.d("IronLogApp", "Auth Session User: ${currentUser?.uid ?: "None (Guest/Logged out)"}")

                if (currentUser != null) {
                    // Step 6: Load Program State
                    currentPhase = "Load Program State"
                    Log.d("IronLogApp", "[Startup Phase 6] Retrieving user active program state with safety boundaries...")
                    try {
                        withTimeoutOrNull(1500L) {
                            repository.getActiveProgramState().firstOrNull()
                        }
                    } catch (e: Exception) {
                        Log.w("IronLogApp", "Active Program load timeout or warning: ${e.message}")
                    }

                    // Step 7: Load Local Cache
                    currentPhase = "Load Local Cache"
                    Log.d("IronLogApp", "[Startup Phase 7] Caching and seeding local exercise database...")
                    try {
                        withTimeoutOrNull(1500L) {
                            repository.getUserProfile().firstOrNull()
                        }
                        repository.seedInitialExercises()
                    } catch (e: Exception) {
                        Log.w("IronLogApp", "Local cache loading encountered warnings: ${e.message}")
                    }

                    // Step 8: Navigate
                    currentPhase = "Navigate"
                    Log.d("IronLogApp", "[Startup Phase 8] Custom dashboard routing initiated for verified user")
                    startDestination = "main"
                } else {
                    currentPhase = "Navigate"
                    Log.d("IronLogApp", "[Startup Phase 8] Logging out or Guest mode. Routing to login screen")
                    startDestination = "login"
                }
                
                isInitialized = true
            } catch (e: Exception) {
                Log.e("IronLogApp", "CRITICAL Crash safety boundary caught startup error", e)
                initErrorLog = e.localizedMessage ?: "Unknown initialization failure"
                // Ensure no full crash: screen is not frozen, bypass available.
            }
        }

    if (!isInitialized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D0D11), Color.Black)
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Large Barbell branding
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Box(modifier = Modifier.width(8.dp).height(28.dp).background(Color(0xFF39FF14), RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.width(5.dp).height(20.dp).background(Color(0xFF39FF14), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(36.dp).height(4.dp).background(Color.White))
                    Box(modifier = Modifier.width(5.dp).height(20.dp).background(Color(0xFF39FF14), RoundedCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(modifier = Modifier.width(8.dp).height(28.dp).background(Color(0xFF39FF14), RoundedCornerShape(2.dp)))
                }
                Text(
                    text = "GYM KRTA H JI",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PRECISION STRENGTH INTELLIGENCE",
                    color = Color(0xFF39FF14),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                CircularProgressIndicator(
                    color = Color(0xFF39FF14),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = currentPhase.uppercase(),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                initErrorLog?.let { err ->
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF3B30)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("INIT DEGRADATION ENCOUNTERED", color = Color(0xFFFF453A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(err, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            startDestination = "login"
                            isInitialized = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("BYPASS & PROCEED OFFLINE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
        ) {
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("main") {
                ProtectedRoute(navController = navController) {
                    MainScreenWrapper(repository, navController)
                }
            }
            composable("active_workout") {
                ProtectedRoute(navController = navController) {
                    ActiveWorkoutScreen(
                        repository = repository,
                        onFinish = {
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
  }
}

@Composable
fun ProtectedRoute(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val authContext = com.example.ui.auth.LocalAuthProvider.current
    
    if (!authContext.isAuthResolved) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF39FF14), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RESOLVING IDENTITY STATE...",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    } else if (authContext.currentUser == null) {
        LaunchedEffect(Unit) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        content()
    }
}

@Composable
fun MainScreenWrapper(
    repository: IronLogRepository,
    rootNavController: NavHostController
) {
    val bottomNavController = rememberNavController()
    
    val items = listOf(
        Triple("home", Icons.Outlined.Home, "HOME"),
        Triple("programs", Icons.Outlined.Star, "PROGRAM"),
        Triple("progress", Icons.Outlined.Timeline, "PROGRESS"),
        Triple("prs", Icons.Filled.Star, "PRS"),
        Triple("history", Icons.Outlined.History, "HISTORY"),
        Triple("profile", Icons.Outlined.Person, "PROFILE")
    )

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            // Elegant Backlit Frosted Glass Floating Tab Bar (iOS 27 Liquid Glass style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xBF000000), // backlit translucent carbon
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.16f), // faint edge highlight catching light
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
                    
                    items.forEach { (route, icon, label) ->
                        val isSelected = currentRoute == route
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    if (currentRoute != route) {
                                        bottomNavController.navigate(route) {
                                            popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val coroutineScope = rememberCoroutineScope()
        NavHost(
            navController = bottomNavController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                val auth = FirebaseAuth.getInstance()
                HomeScreen(
                    repository = repository,
                    onStartWorkout = { generatedWorkout ->
                        coroutineScope.launch {
                            repository.saveWorkout(generatedWorkout)
                            rootNavController.navigate("active_workout")
                        }
                    },
                    onResumeWorkout = {
                        rootNavController.navigate("active_workout")
                    },
                    onProfileClick = {
                        bottomNavController.navigate("profile") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTab = { route ->
                        bottomNavController.navigate(route) {
                            popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("programs") {
                com.example.ui.programs.ProgramsScreen(
                    repository = repository,
                    onProgramStarted = {
                        bottomNavController.navigate("home") {
                            popUpTo(bottomNavController.graph.startDestinationId) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                )
            }
            composable("progress") {
                ProgressScreen(repository)
            }
            composable("prs") {
                com.example.ui.prs.PRsScreen(repository = repository)
            }
            composable("history") {
                HistoryScreen(repository)
            }
            composable("profile") {
                com.example.ui.profile.ProfileScreen(
                    repository = repository,
                    onSignOutClick = {
                        coroutineScope.launch {
                            repository.signOut()
                            rootNavController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    onLoginClick = {
                        rootNavController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
