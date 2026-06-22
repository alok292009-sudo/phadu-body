package com.example.ui.programs

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.data.IronLogRepository
import com.example.model.ActiveProgramState
import com.example.model.Program
import com.example.model.ProgramWeek
import com.example.model.ProgramDay
import com.example.model.ProgramExercise
import com.example.model.Workout
import com.example.model.toWorkout
import com.example.model.LoggedExercise
import com.example.model.WorkoutSet
import com.example.ui.theme.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch

@Composable
fun ProgramsScreen(repository: IronLogRepository, onProgramStarted: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    var workoutsList by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var program by remember { mutableStateOf<Program?>(null) }
    var currentWeekData by remember { mutableStateOf<com.example.model.ProgramWeek?>(null) }

    var selectedWeekOneIndexed by remember(activeProgramState?.currentWeek) { 
        mutableIntStateOf(activeProgramState?.currentWeek ?: 1) 
    }

    LaunchedEffect(Unit) {
        launch { repository.getActiveProgramState().collect { activeProgramState = it } }
        launch { repository.getWorkouts().collect { workoutsList = it } }
        launch { 
            repository.getActiveProgram().collect { p ->
                program = p
            }
        }
    }

    LaunchedEffect(selectedWeekOneIndexed) {
        repository.getProgramWeek(selectedWeekOneIndexed).collect {
            currentWeekData = it
        }
    }

    if (program == null) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextPrimaryColor)
        }
        return
    }

    val state = activeProgramState ?: run {
        val calendar = java.util.Calendar.getInstance()
        val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val initialDaySlot = when (currentDayOfWeek) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }
        ActiveProgramState(
            programName = program?.programName ?: "Protocol",
            currentWeek = 1,
            currentDaySlot = initialDaySlot,
            completedWorkoutsMap = emptyMap(),
            totalWeeks = program?.program?.durationWeeks ?: 12
        )
    }

    val currentWeekKey = "week$selectedWeekOneIndexed"
    val daysList: List<com.example.model.ProgramDay> = currentWeekData?.days ?: emptyList()
    
    val schema = program?._meta?.schema
    val totalWeeksFound = program?.durationWeeks ?: state.totalWeeks.takeIf { it > 0 } ?: 12
    val weekEntries = (1..totalWeeksFound).map { "week$it" }
    
    // Default to the current day slot if we're on the current week
    var selectedDaySlot by remember(selectedWeekOneIndexed, state.currentWeek, state.currentDaySlot) { 
        mutableIntStateOf(if (selectedWeekOneIndexed == state.currentWeek) state.currentDaySlot else 0) 
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x12)
        ) {
            // Program Header
            val mainTitle = program!!.programName
            val author = program!!.program?.author ?: ""

            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(bottom = IronSpacing.x24)
            ) {
                Text(
                    text = "SYSTEM PROTOCOL",
                    style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AutoResizingText(
                    text = mainTitle,
                    style = IronTypography.Heading,
                    maxLines = 2
                )
                if (author.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BY $author".uppercase(),
                        style = IronTypography.Subheading.copy(color = TextSecondaryColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    )
                }
            }

            // Week Selector
            Text(
                "PHASE SELECTOR",
                style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                weekEntries.forEach { wKey ->
                    val weekNum = wKey.replace("week", "").toIntOrNull() ?: 1
                    val isSelected = selectedWeekOneIndexed == weekNum
                    val isCurrent = weekNum == state.currentWeek
                    val isLocked = weekNum > state.currentWeek
                    
                    Box(
                        modifier = Modifier
                            .bouncyClick { if (!isLocked) selectedWeekOneIndexed = weekNum }
                            .hairlineBorder(RoundedCornerShape(IronCorner.RadiusFull))
                            .background(
                                if (isSelected) TextPrimaryColor.copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(IronCorner.RadiusFull)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "W$weekNum",
                                style = IronTypography.Headline.copy(
                                    color = if (isSelected) TextPrimaryColor else if (isLocked) TextTertiaryColor.copy(alpha = 0.3f) else TextSecondaryColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                )
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(5.dp).background(SuccessColor, RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            // Day Selection
            val weekdayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val defaultDayLabels = mapOf(
                    "Monday" to "Upper Body",
                    "Tuesday" to "Lower Body",
                    "Wednesday" to "Recovery",
                    "Thursday" to "Pull",
                    "Friday" to "Push",
                    "Saturday" to "Legs",
                    "Sunday" to "Recovery"
                )
                weekdayOrder.forEachIndexed { i, dayName ->
                    val isSelected = selectedDaySlot == i
                    val isCompleted = state.completedWorkoutsMap.containsKey("${currentWeekKey}_$i")
                    val isLocked = (selectedWeekOneIndexed > state.currentWeek) || (selectedWeekOneIndexed == state.currentWeek && i > state.currentDaySlot)
                    val label = schema?.weekdayMap?.get(dayName) ?: defaultDayLabels[dayName] ?: dayName.substring(0, 3).uppercase()
                    
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .bouncyClick { selectedDaySlot = i }
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .then(
                                if (isSelected) Modifier.background(TextPrimaryColor, RoundedCornerShape(IronCorner.RadiusMd))
                                else Modifier
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            dayName.substring(0, 3).uppercase(), 
                            style = IronTypography.Micro.copy(
                                color = if (isSelected) BgColor else if (isLocked) TextTertiaryColor.copy(alpha = 0.5f) else TextTertiaryColor,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            label,
                            style = IronTypography.Micro.copy(
                                color = if (isSelected) BgColor.copy(alpha = 0.8f) else TextTertiaryColor,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            ),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (label.contains("Rest", ignoreCase = true) || label.contains("Recovery", ignoreCase = true)) {
                            Icon(
                                Icons.Outlined.Hotel, 
                                contentDescription = null, 
                                tint = if (isSelected) BgColor else TextTertiaryColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(10.dp)
                            )
                        } else if (isCompleted) {
                            Box(modifier = Modifier.size(5.dp).background(if (isSelected) BgColor else SuccessColor, RoundedCornerShape(3.dp)))
                        } else {
                            Box(modifier = Modifier.size(3.dp).background(if (isSelected) BgColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            AnimatedContent(
                targetState = selectedWeekOneIndexed to selectedDaySlot,
                transitionSpec = {
                    if (targetState.first > initialState.first || (targetState.first == initialState.first && targetState.second > initialState.second)) {
                        (slideInHorizontally { it } + fadeIn(tween(300))).togetherWith(slideOutHorizontally { -it } + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally { -it } + fadeIn(tween(300))).togetherWith(slideOutHorizontally { it } + fadeOut(tween(300)))
                    }.using(SizeTransform(clip = false))
                },
                label = "day_content"
            ) { (targetWeek, targetDay) ->
                val day = daysList.getOrNull(targetDay)
                if (day != null) {
                    Column {
                        // Selected Day Summary
                        val isCurrentTarget = targetWeek == state.currentWeek && targetDay == state.currentDaySlot
                        val isPast = (targetWeek < state.currentWeek) || (targetWeek == state.currentWeek && targetDay < state.currentDaySlot)
                        
                        PremiumCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.width(3.dp).height(20.dp).background(if (isCurrentTarget) TextPrimaryColor else if (isPast) SuccessColor else TextTertiaryColor, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    AutoResizingText(day.displayName.ifEmpty { day.trainingDay }.uppercase(), style = IronTypography.Title, maxLines = 1)
                                }
                                
                                if (day.isRestDay) {
                                    Icon(Icons.Outlined.Hotel, contentDescription = null, tint = TextTertiaryColor, modifier = Modifier.size(18.dp))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(IronSpacing.x20))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val statModifier = Modifier.weight(1f)
                                StatCard(label = "TYPE", value = if (day.isRestDay) "REST" else day.trainingDay.uppercase(), modifier = statModifier)
                                StatCard(label = "LOAD", value = if (day.isRestDay) "--" else "${day.exercises.size} EXS", modifier = statModifier)
                                StatCard(label = "STATUS", value = if (isPast) "DONE" else if (isCurrentTarget) "READY" else "LOCKED", modifier = statModifier)
                            }
                            
                            if (!day.isRestDay && isCurrentTarget) {
                                Spacer(modifier = Modifier.height(IronSpacing.x20))
                                Button(
                                    onClick = {
                                        var newW = day.toWorkout(currentWeekKey, targetDay)
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
                                                ex.copy(sets = ex.sets.map { s -> s.copy(reps = s.targetReps ?: 0) })
                                            }
                                        }
                                        newW = newW.copy(loggedExercises = newExs)
                                        coroutineScope.launch {
                                            try {
                                                if (activeProgramState == null) {
                                                    repository.saveActiveProgramState(state.copy(
                                                        programName = program!!.programName
                                                    ))
                                                }
                                                repository.saveWorkout(newW)
                                                onProgramStarted()
                                            } catch (e: Exception) {
                                                Log.e("ProgramsScreen", "Error starting workout", e)
                                                Toast.makeText(context, "Failed to start workout", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                                    shape = RoundedCornerShape(IronCorner.RadiusMd)
                                ) {
                                    Text("START WORKOUT", style = IronTypography.Headline, color = BgColor)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(IronSpacing.x32))

                        // Exercises
                        if (!day.isRestDay) {
                            Text(
                                "SESSION PROTOCOL",
                                style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                            )
                            
                            day.exercises.forEach { ex ->
                                PremiumCard(modifier = Modifier.padding(bottom = IronSpacing.x12)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ex.name,
                                                style = IronTypography.Headline,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${ex.muscleGroup?.uppercase() ?: "GENERAL"} • ${ex.prescription?.workingSets ?: "2"} SETS",
                                                style = IronTypography.Micro.copy(color = TextSecondaryColor)
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(
                                                text = "${ex.prescription?.repRange ?: "8-12"} REPS",
                                                style = IronTypography.Caption.copy(fontWeight = FontWeight.Black, color = TextPrimaryColor)
                                            )
                                        }
                                    }
                                    
                                    if (ex.technique != null && (ex.technique.failure || ex.technique.myoReps || ex.technique.lengthenedPartials || ex.technique.staticStretch)) {
                                        Row(
                                            modifier = Modifier.padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (ex.technique.failure) TechniquePill("Failure")
                                            if (ex.technique.myoReps) TechniquePill("Myo-Reps")
                                            if (ex.technique.lengthenedPartials) TechniquePill("LLPs")
                                            if (ex.technique.staticStretch) TechniquePill("Static Stretch")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = IronSpacing.x48),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Hotel, contentDescription = null, tint = TextTertiaryColor.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(IronSpacing.x16))
                                Text("RECOVERY MODE", style = IronTypography.Headline, color = TextSecondaryColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(day.recovery?.instructions ?: "Focus on sleep and hydration.", style = IronTypography.Footnote.copy(color = TextTertiaryColor, textAlign = TextAlign.Center))
                                
                                if (isCurrentTarget) {
                                    Spacer(modifier = Modifier.height(IronSpacing.x24))
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val totalDays = daysList.size.takeIf { it > 0 } ?: 7
                                                val nextSlot = state.currentDaySlot + 1
                                                val nextWeek = if (nextSlot >= totalDays) state.currentWeek + 1 else state.currentWeek
                                                val finalSlot = nextSlot % totalDays
                                                try {
                                                    repository.saveActiveProgramState(state.copy(
                                                        currentDaySlot = finalSlot,
                                                        currentWeek = nextWeek
                                                    ))
                                                } catch (e: Exception) {
                                                    Log.e("ProgramsScreen", "Error saving active program state", e)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                                        shape = RoundedCornerShape(IronCorner.RadiusMd)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.FastForward, contentDescription = null, tint = BgColor, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("SKIP REST DAY", style = IronTypography.Headline, color = BgColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun TechniquePill(label: String) {
    Box(
        modifier = Modifier
            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = IronTypography.Micro.copy(fontSize = 9.sp, color = TextSecondaryColor, letterSpacing = 0.5.sp)
        )
    }
}

@Composable
fun DayStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 1.5.sp))
        Spacer(modifier = Modifier.height(4.dp))
        AutoResizingText(value, style = IronTypography.Headline.copy(fontWeight = FontWeight.Black), maxLines = 1)
    }
}
