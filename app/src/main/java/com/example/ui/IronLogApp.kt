package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
        startDestination = "login",
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
        Triple("home", Icons.Filled.Home, "HOME"),
        Triple("programs", Icons.Filled.Star, "PROGRAMS"),
        Triple("progress", Icons.Filled.Timeline, "PROGRESS"),
        Triple("history", Icons.Filled.History, "HISTORY")
    )

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(containerColor = com.example.ui.theme.GlassDark, contentColor = Color.White) {
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                items.forEach { (route, icon, label) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 1.sp) },
                        selected = currentRoute == route,
                        onClick = {
                            if (currentRoute != route) {
                                bottomNavController.navigate(route) {
                                    popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.GrayMedium,
                            unselectedTextColor = com.example.ui.theme.GrayMedium
                        )
                    )
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
                HomeScreen(
                    repository = repository,
                    onStartWorkout = { templateId ->
                        coroutineScope.launch {
                            var newWorkout = com.example.model.Workout()
                            if (templateId != null) {
                                val templates = repository.getTemplates().first()
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
                    }
                )
            }
            composable("programs") {
                com.example.ui.programs.ProgramsScreen(
                    repository = repository,
                    onProgramStarted = {
                        bottomNavController.navigate("home") {
                            popUpTo(bottomNavController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
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
        }
    }
}
