package com.example.ui.programs

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.IronLogRepository
import com.example.model.ActiveProgramState
import com.example.model.Program
import com.example.model.ProgramDay
import com.example.model.Workout
import com.example.model.toWorkout
import com.example.model.LoggedExercise
import com.example.model.WorkoutSet
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProgramsScreen(repository: IronLogRepository, onProgramStarted: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var program by remember { mutableStateOf<Program?>(null) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    var workoutsList by remember { mutableStateOf<List<Workout>>(emptyList()) }

    LaunchedEffect(Unit) {
        launch { repository.getActiveProgramState().collect { activeProgramState = it } }
        launch { repository.getWorkouts().collect { workoutsList = it } }
        
        // Ensure static program is loaded
        try {
            val json = context.assets.open("jeff_nippard.json").bufferedReader().use { it.readText() }
            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(Program::class.java)
            val rawProgram = adapter.fromJson(json)
            program = com.example.model.ProgramValidator.validateAndSanitize(rawProgram)
        } catch (e: Exception) {}
    }

    if (program == null || activeProgramState == null) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextPrimaryColor)
        }
        return
    }

    val state = activeProgramState!!
    val currentWeekIdx = state.currentWeekIndex
    val weekKey = "week${currentWeekIdx + 1}"
    val daysList = program!!.weeks[weekKey]?.days ?: emptyList()
    var selectedDayIndex by remember { mutableIntStateOf(state.currentDayIndex) }

    Scaffold(containerColor = BgColor) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x12)
        ) {
            // Week Pill
            Box(
                modifier = Modifier
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusFull))
                    .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x8)
            ) {
                Text("Week ${currentWeekIdx + 1} of 12", style = IronTypography.Callout)
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            // Day pills horizontal scroll
            val dayAbbrs = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(IronSpacing.x12)
            ) {
                daysList.forEachIndexed { i, day ->
                    val isSelected = selectedDayIndex == i
                    val isCompleted = workoutsList.any { it.status == "completed" && it.templateId == "${weekKey}_$i" }
                    val abbr = dayAbbrs.getOrNull(i) ?: "D${i+1}"
                    val typeAbbr = if (day.isRestDay) "RST" else day.dayName.take(3).uppercase()
                    
                    Column(
                        modifier = Modifier
                            .width(44.dp)
                            .bouncyClick { selectedDayIndex = i }
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .then(
                                if (isSelected) Modifier.background(SurfaceRaisedColor, RoundedCornerShape(IronCorner.RadiusMd))
                                else Modifier
                            )
                            .then(
                                if (isSelected) Modifier.border(1.dp, BorderStrongColor, RoundedCornerShape(IronCorner.RadiusMd))
                                else Modifier
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(abbr, style = IronTypography.Caption.copy(color = if (isSelected) TextPrimaryColor else TextSecondaryColor))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(typeAbbr, style = IronTypography.Footnote.copy(color = if (isCompleted) SuccessColor else TextTertiaryColor))
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x32))

            val day = daysList.getOrNull(selectedDayIndex)
            if (day != null) {
                // Selected Day Summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                        .padding(IronSpacing.x20)
                ) {
                    Text(day.dayName, style = IronTypography.Title2)
                    Spacer(modifier = Modifier.height(IronSpacing.x12))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${day.exercises.size} Exercises", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                        Text(if (day.isRestDay) "0 mins" else "60 mins est.", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                        Text(if (day.exercises.isEmpty()) "Recovery" else "Hypertrophy", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    }
                    
                    Spacer(modifier = Modifier.height(IronSpacing.x20))
                    
                    if (!day.isRestDay) {
                        Button(
                            onClick = {
                                var newW = day.toWorkout(weekKey, selectedDayIndex)
                                val completedWorkouts = workoutsList.filter { it.status == "completed" }.sortedByDescending { it.date }
                                val newExs = newW.loggedExercises.map { ex: LoggedExercise ->
                                    val lastEx = completedWorkouts.mapNotNull { w: Workout -> w.loggedExercises.find { it.exerciseId == ex.exerciseId } }.firstOrNull()
                                    if (lastEx != null) {
                                        val newSets = ex.sets.map { set: WorkoutSet ->
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
                                        ex.copy(sets = ex.sets.map { s: WorkoutSet -> s.copy(reps = s.targetReps ?: 0) })
                                    }
                                }
                                newW = newW.copy(loggedExercises = newExs)
                                coroutineScope.launch {
                                    repository.saveWorkout(newW)
                                    onProgramStarted()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                            shape = RoundedCornerShape(IronCorner.RadiusSm)
                        ) {
                            Text("Start Workout", style = IronTypography.Headline, color = BgColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(IronSpacing.x32))

                // Exercises
                var lastMuscleGroup: String? = null
                day.exercises.forEach { ex ->
                    val muscleGroup = ex.muscleGroup?.uppercase() ?: "GENERAL"
                    if (muscleGroup != lastMuscleGroup) {
                        Text(
                            text = muscleGroup,
                            style = IronTypography.Caption.copy(color = TextTertiaryColor),
                            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                        )
                        lastMuscleGroup = muscleGroup
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = IronSpacing.x12)
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .padding(IronSpacing.x20)
                    ) {
                        Column {
                            Text(ex.name, style = IronTypography.Body)
                            Spacer(modifier = Modifier.height(IronSpacing.x4))
                            Text(
                                "${ex.workingSets ?: "3"} sets • ${ex.reps ?: ex.repRange ?: "10"} reps",
                                style = IronTypography.Footnote.copy(color = TextSecondaryColor)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
