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

    NavHost(
        navController = navController,
        startDestination = "main",
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

@Composable
fun ProtectedRoute(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    // If the user navigates past the login screen (e.g. bypass), let them stay on main.
    // The repository defaults to 'local_test_user'.
    content()
}

@Composable
fun MainScreenWrapper(
    repository: IronLogRepository,
    rootNavController: NavHostController
) {
    val bottomNavController = rememberNavController()
    
    val items = listOf(
        Triple("home", Icons.Outlined.Home, "HOME"),
        Triple("programs", Icons.Outlined.Star, "PROGRAMS"),
        Triple("progress", Icons.Outlined.Timeline, "PROGRESS"),
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
                    onStartWorkout = { templateId ->
                        coroutineScope.launch {
                            var newWorkout = com.example.model.Workout()
                            if (templateId != null) {
                                val templates = repository.getTemplates().first { it.isNotEmpty() }
                                val template = templates.find { it.id == templateId }
                                if (template != null) {
                                    val loggedExercises = template.exercises.map { tex ->
                                        com.example.model.LoggedExercise(
                                            exerciseId = tex.exerciseId,
                                            exerciseName = tex.exerciseName,
                                            videoUrl = tex.videoUrl,
                                            sets = List(tex.targetSets) { com.example.model.WorkoutSet(reps = tex.targetReps) }
                                        )
                                    }
                                    newWorkout = newWorkout.copy(
                                        templateId = template.id,
                                        templateName = template.name,
                                        loggedExercises = loggedExercises
                                    )
                                }
                            }
                            repository.saveWorkout(newWorkout)
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
