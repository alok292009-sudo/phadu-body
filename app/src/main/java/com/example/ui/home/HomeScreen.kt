package com.example.ui.home

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.IronLogRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    repository: IronLogRepository,
    onStartWorkout: (Workout) -> Unit,
    onResumeWorkout: () -> Unit,
    onProfileClick: () -> Unit,
    onNavigateToTab: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    var workoutsList by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var program by remember { mutableStateOf<Program?>(null) }
    var isLoadingProgram by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.seedInitialExercises()
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
        launch { repository.getActiveProgramState().collect { activeProgramState = it } }
        launch { repository.getWorkouts().collect { workoutsList = it } }
    }

    LaunchedEffect(Unit) {
        isLoadingProgram = true
        try {
            val json = context.assets.open("jeff_nippard.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(Program::class.java)
            val rawProgram = adapter.fromJson(json)
            program = ProgramValidator.validateAndSanitize(rawProgram)
        } catch (e: Exception) {
        }
        isLoadingProgram = false
    }

    if (activeProgramState == null) {
        // Quick bootstrap if no active program
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            if (program != null) {
                Button(
                    onClick = {
                        val state = ActiveProgramState(
                            programKey = "jeff_nippard.json",
                            programName = program!!.programName,
                            currentWeekIndex = 0,
                            currentDayIndex = 0,
                            completedWorkoutsMap = emptyMap(),
                            freeNavigationEnabled = true,
                            workoutsCompletedThisWeek = 0,
                            totalWorkoutsThisWeek = program!!.weeks["week1"]?.days?.count { !it.isRestDay } ?: 0
                        )
                        coroutineScope.launch { repository.saveActiveProgramState(state) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor)
                ) {
                    Text("START PROGRAM", style = IronTypography.Headline)
                }
            } else {
                CircularProgressIndicator(color = TextPrimaryColor)
            }
        }
        return
    }

    Scaffold(
        containerColor = BgColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val p = program
            if (p != null) {
                DashboardClean(
                    program = p,
                    activeProgramState = activeProgramState!!,
                    activeWorkout = activeWorkout,
                    workoutsList = workoutsList,
                    onResumeWorkout = onResumeWorkout,
                    onStartWorkout = onStartWorkout,
                    onProfileClick = onProfileClick
                )
            }
        }
    }
}

@Composable
fun DashboardClean(
    program: Program,
    activeProgramState: ActiveProgramState,
    activeWorkout: Workout?,
    workoutsList: List<Workout>,
    onResumeWorkout: () -> Unit,
    onStartWorkout: (Workout) -> Unit,
    onProfileClick: () -> Unit
) {
    val completedMap = activeProgramState.completedWorkoutsMap

    val weekKey = "week${activeProgramState.currentWeekIndex + 1}"
    val daysList = program.weeks[weekKey]?.days ?: emptyList()
    
    // Auto find the correct day to show
    var targetDayIndex = activeProgramState.currentDayIndex
    if (activeProgramState.freeNavigationEnabled && daysList.isNotEmpty()) {
        val firstIncomplete = daysList.indices.firstOrNull { idx ->
            !daysList[idx].isRestDay && completedMap["${weekKey}_$idx"] != true
        }
        if (firstIncomplete != null) {
            targetDayIndex = firstIncomplete
        }
    }
    val selectedDay = daysList.getOrNull(targetDayIndex)

    val currentStreak = remember(workoutsList) {
        val completed = workoutsList.filter { it.status == "completed" }
        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val uniqueDays = completed.map { sdfDay.format(Date(it.date)) }.toSet()
        val tempCalendar = Calendar.getInstance()
        var streak = 0
        val todayStr = sdfDay.format(tempCalendar.time)
        tempCalendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdfDay.format(tempCalendar.time)
        if (uniqueDays.contains(todayStr)) {
            streak = 1
            tempCalendar.time = Date()
            tempCalendar.add(Calendar.DAY_OF_YEAR, -1)
            while (uniqueDays.contains(sdfDay.format(tempCalendar.time))) {
                streak++
                tempCalendar.add(Calendar.DAY_OF_YEAR, -1)
            }
        } else if (uniqueDays.contains(yesterdayStr)) {
            streak = 1
            tempCalendar.add(Calendar.DAY_OF_YEAR, -1)
            while (uniqueDays.contains(sdfDay.format(tempCalendar.time))) {
                streak++
                tempCalendar.add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            streak = if (uniqueDays.isNotEmpty()) 1 else 0
        }
        streak
    }

    val weeklyVolume = remember(workoutsList) {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        workoutsList.filter { it.status == "completed" && it.date >= sevenDaysAgo }.sumOf { it.totalVolume }
    }
    
    val totalWorkouts = workoutsList.count { it.status == "completed" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(IronSpacing.x16)
    ) {
        // Top row: App wordmark and Profile avatar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = IronSpacing.x8, bottom = IronSpacing.x24),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "IRON LOG",
                style = IronTypography.Title2
            )
            Box(
                modifier = Modifier
                    .size(IronSpacing.x32)
                    .clip(RoundedCornerShape(IronCorner.RadiusFull))
                    .background(Color(0xFF333333))
                    .bouncyClick { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Warm Up button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                .bouncyClick { /* Warm Up action */ },
            contentAlignment = Alignment.Center
        ) {
            Text("Warm Up", style = IronTypography.Headline)
        }

        Spacer(modifier = Modifier.height(IronSpacing.x24))

        // Hero card (Current Day Info)
        if (selectedDay != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                    .padding(IronSpacing.x24)
            ) {
                Text(
                    text = selectedDay.dayName,
                    style = IronTypography.LargeTitle
                )
                
                Spacer(modifier = Modifier.height(IronSpacing.x12))
                
                // Muscle tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IronSpacing.x8)
                ) {
                    val muscleSet = selectedDay.exercises.mapNotNull { it.muscleGroup }.distinct()
                    val musclesToShow = if (muscleSet.isNotEmpty()) muscleSet else listOf("Full Body")
                    
                    musclesToShow.take(3).forEach { m ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFFFFFFF).copy(alpha=0.15f), RoundedCornerShape(IronCorner.RadiusFull))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(m, style = IronTypography.Caption)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(IronSpacing.x16))
                
                val duration = if (selectedDay.isRestDay) 0 else 60
                Text(
                    text = "${selectedDay.exercises.size} Exercises • $duration mins est.",
                    style = IronTypography.Footnote.copy(color = TextSecondaryColor)
                )

                Spacer(modifier = Modifier.height(IronSpacing.x24))

                if (activeWorkout != null) {
                    Button(
                        onClick = onResumeWorkout,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                        shape = RoundedCornerShape(IronCorner.RadiusSm)
                    ) {
                        Text("Resume Workout", style = IronTypography.Headline, color = BgColor)
                    }
                } else if (selectedDay.isRestDay) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(52.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Rest Day", style = IronTypography.Headline, color = TextPrimaryColor)
                    }
                } else {
                    Button(
                        onClick = {
                            var newW = selectedDay.toWorkout(weekKey, targetDayIndex)
                            // Prefill sets logic kept minimal
                            val completedWorkouts = workoutsList.filter { it.status == "completed" }.sortedByDescending { it.date }
                            val newExs = newW.loggedExercises.map { ex ->
                                val lastEx = completedWorkouts.mapNotNull { w -> w.loggedExercises.find { it.exerciseId == ex.exerciseId } }.firstOrNull()
                                if (lastEx != null) {
                                    val newSets = ex.sets.map { set ->
                                        val pastSet = lastEx.sets.find { it.isWarmup == set.isWarmup && it.setNumber == set.setNumber }
                                            ?: lastEx.sets.lastOrNull { it.isWarmup == set.isWarmup }
                                        if (pastSet != null) {
                                            set.copy(weight = pastSet.weight, reps = pastSet.reps)
                                        } else {
                                            set.copy(reps = set.targetReps ?: 0)
                                        }
                                    }
                                    ex.copy(sets = newSets)
                                } else {
                                    ex.copy(sets = ex.sets.map { it.copy(reps = it.targetReps ?: 0) })
                                }
                            }
                            newW = newW.copy(loggedExercises = newExs)
                            onStartWorkout(newW)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                        shape = RoundedCornerShape(IronCorner.RadiusSm)
                    ) {
                        Text("Start Workout", style = IronTypography.Headline, color = BgColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(IronSpacing.x32))

        // Stats row (horizontal scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(IronSpacing.x12)
        ) {
            listOf(
                Pair("$currentStreak", "STREAK"),
                Pair("${weeklyVolume.toInt()}kg", "WEEK VOL"),
                Pair("$totalWorkouts", "WORKOUTS")
            ).forEach { (value, label) ->
                Column(
                    modifier = Modifier
                        .widthIn(min = 100.dp)
                        .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                        .padding(IronSpacing.x16),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(value, style = IronTypography.Title1)
                    Text(label, style = IronTypography.Caption.copy(color = TextSecondaryColor))
                }
            }
        }

        Spacer(modifier = Modifier.height(IronSpacing.x32))

        // Recent workout card
        val lastWorkout = workoutsList.filter { it.status == "completed" }.maxByOrNull { it.date }
        if (lastWorkout != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                    .padding(IronSpacing.x20),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Recent Workout", style = IronTypography.Headline)
                    Spacer(modifier = Modifier.height(IronSpacing.x4))
                    Text(sdf.format(Date(lastWorkout.date)), style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                }
                Text("${lastWorkout.totalVolume.toInt()} kg", style = IronTypography.Title2)
            }
        }
    }
}
