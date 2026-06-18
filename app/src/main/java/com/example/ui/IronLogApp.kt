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

@Composable
fun IronLogApp(repository: IronLogRepository) {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }
    var isInitialized by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf("login") }

    LaunchedEffect(Unit) {
        Log.d("IronLogApp", "=== APP STARTUP SEQUENCE INITIATED ===")
        try {
            // Step 1: Initialize / access Firebase App references
            Log.d("IronLogApp", "Step 1: Referencing Firebase Application runtime setup...")
            val app = auth.app
            
            // Step 2: Verify Firebase initialization
            Log.d("IronLogApp", "Step 2: Verifying Firebase application instance active: name=${app.name}")
            if (app.name.isEmpty()) {
                throw IllegalStateException("Firebase instance has dry namespace execution!")
            }
            
            // Step 3: Verify Auth initialization and config
            Log.d("IronLogApp", "Step 3: Verifying FirebaseAuth provider bounds...")
            if (auth.app != app) {
                throw IllegalStateException("Firebase Authentication is disassociated from active Firebase App!")
            }
            
            // Step 4: Check current user session status
            val currentUser = auth.currentUser
            Log.d("IronLogApp", "Step 4: Resolved session status. Auth user UID: ${currentUser?.uid ?: "Null (Anonymous/Logged-out)"}")
            
            if (currentUser != null) {
                // Step 5: Load user profile with a 1.2s safety boundary to avoid suspending indefinitely on offline listener state
                Log.d("IronLogApp", "Step 5: Loading user profile metadata from local fallback or cache channel...")
                try {
                    withTimeoutOrNull(1200L) {
                        repository.getUserProfile().firstOrNull()
                    }
                    Log.d("IronLogApp", "User profile payload retrieved or falling back safely")
                } catch (e: Exception) {
                    Log.w("IronLogApp", "User profile retrieval warning during startup sequence: ${e.message}")
                }
                
                // Step 6: Load active program telemetry state with a 1.2s timeout
                Log.d("IronLogApp", "Step 6: Loading workout programs state vectors...")
                try {
                    withTimeoutOrNull(1200L) {
                        repository.getActiveProgramState().firstOrNull()
                    }
                    Log.d("IronLogApp", "Program state vectors retrieved or falling back safely")
                } catch (e: Exception) {
                    Log.w("IronLogApp", "Program state retrieval warning during startup sequence: ${e.message}")
                }
                
                // Step 7: Decide output routing and execute navigate destination Choice
                Log.d("IronLogApp", "Step 7: Executing startup navigation choice -> main dashboard screen")
                startDestination = "main"
            } else {
                Log.d("IronLogApp", "Step 5: Skipping profile metadata (Signed Out)")
                Log.d("IronLogApp", "Step 6: Skipping program state (Signed Out)")
                Log.d("IronLogApp", "Step 7: Executing startup navigation choice -> landing credentials screen")
                startDestination = "login"
            }
        } catch (e: Exception) {
            Log.e("IronLogApp", "CRITICAL: Firebase Auth startup sequence interrupted: ${e.localizedMessage}", e)
            startDestination = "login" // Fallback safety route
        } finally {
            Log.d("IronLogApp", "=== APP STARTUP SEQUENCE COMPLETED ===")
            isInitialized = true
        }
    }

    if (!isInitialized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
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

@Composable
fun ProtectedRoute(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    
    if (currentUser == null) {
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
