package com.example.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Exercise
import com.example.model.LoggedExercise
import com.example.model.Workout
import com.example.model.WorkoutSet
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    repository: IronLogRepository,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var availableExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var prs by remember { mutableStateOf<Map<String, com.example.model.PersonalRecord>>(emptyMap()) }

    LaunchedEffect(Unit) {
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
        launch { repository.getExercises().collect { availableExercises = it } }
        launch { repository.getPersonalRecords().collect { recs -> prs = recs.associateBy { it.exerciseId } } }
    }

    if (activeWorkout == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF1C1C22), Color.Black),
                center = Offset(500f, -200f),
                radius = 2500f
            )
        ),
        topBar = {
            TopAppBar(
                title = { Text((activeWorkout!!.templateName ?: "FREE WORKOUT").uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            repository.finishWorkout(activeWorkout!!)
                            onFinish()
                        }
                    }) {
                        Text("FINISH", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddExerciseDialog = true },
                containerColor = Color.White,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(activeWorkout!!.loggedExercises, key = { it.exerciseId }) { exercise ->
                val index = activeWorkout!!.loggedExercises.indexOf(exercise)
                val pr = prs[exercise.exerciseId]
                LoggedExerciseCard(
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(300)),
                    loggedExercise = exercise,
                    pr = pr,
                    onUpdate = { updatedExercise ->
                        val updatedList = activeWorkout!!.loggedExercises.toMutableList()
                        updatedList[index] = updatedExercise
                        coroutineScope.launch {
                            repository.saveWorkout(activeWorkout!!.copy(loggedExercises = updatedList))
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showAddExerciseDialog) {
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = { Text("ADD EXERCISE", fontWeight = FontWeight.Black) },
            containerColor = Color.Black,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFA0A0A0),
            shape = RoundedCornerShape(0.dp),
            text = {
                LazyColumn {
                    itemsIndexed(availableExercises) { _, ex ->
                        Text(
                            text = ex.name.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newList = activeWorkout!!.loggedExercises + LoggedExercise(
                                        exerciseId = ex.id,
                                        exerciseName = ex.name,
                                        sets = listOf(WorkoutSet())
                                    )
                                    coroutineScope.launch {
                                        repository.saveWorkout(activeWorkout!!.copy(loggedExercises = newList))
                                        showAddExerciseDialog = false
                                    }
                                }
                                .padding(vertical = 16.dp)
                        )
                        HorizontalDivider(color = Color(0xFF333333))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) { Text("CANCEL", color = Color.White) }
            }
        )
    }
}

@Composable
fun LoggedExerciseCard(
    modifier: Modifier = Modifier,
    loggedExercise: LoggedExercise,
    pr: com.example.model.PersonalRecord? = null,
    onUpdate: (LoggedExercise) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = loggedExercise.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
                if (loggedExercise.videoUrl != null) {
                    IconButton(onClick = { uriHandler.openUri(loggedExercise.videoUrl) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Watch Video", tint = Color.Red)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SET", modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = com.example.ui.theme.GrayMedium)
                Text("KG", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = com.example.ui.theme.GrayMedium)
                Text("REPS", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = com.example.ui.theme.GrayMedium)
                Text("DONE", modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = com.example.ui.theme.GrayMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))

            loggedExercise.sets.forEachIndexed { setIndex, set ->
                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(0.5f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${setIndex + 1}",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            if (pr != null && pr.bestWeight != null && set.weight > pr.bestWeight.value) {
                                Box(
                                    modifier = Modifier
                                        .background(com.example.ui.theme.ErrorColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("PR", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        
                        StepperControl(
                            value = set.weight,
                            step = 2.5,
                            onValueChange = { newVal ->
                                val newSets = loggedExercise.sets.toMutableList()
                                newSets[setIndex] = set.copy(weight = newVal)
                                onUpdate(loggedExercise.copy(sets = newSets))
                            },
                            modifier = Modifier.weight(1.5f)
                        )
                        
                        StepperControl(
                            value = set.reps.toDouble(),
                            step = 1.0,
                            onValueChange = { newVal ->
                                val newSets = loggedExercise.sets.toMutableList()
                                newSets[setIndex] = set.copy(reps = newVal.toInt())
                                onUpdate(loggedExercise.copy(sets = newSets))
                            },
                            modifier = Modifier.weight(1.5f)
                        )
                        
                        val isDone = set.completedAt != null
                        val boxColor by animateColorAsState(if (isDone) Color.White else Color.Transparent, animationSpec = tween(300))
                        val borderColor by animateColorAsState(if (isDone) Color.Transparent else com.example.ui.theme.GlassBorderLight, animationSpec = tween(300))
                        
                        Box(
                            modifier = Modifier
                                .weight(0.5f)
                                .height(36.dp)
                                .padding(horizontal = 4.dp)
                                .background(
                                    color = boxColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    borderColor,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    val newSets = loggedExercise.sets.toMutableList()
                                    newSets[setIndex] = set.copy(
                                        completedAt = if (set.completedAt == null) System.currentTimeMillis() else null
                                    )
                                    onUpdate(loggedExercise.copy(sets = newSets))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.Black)
                            }
                        }
                    }
                    
                    // RPE Slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RPE: ${set.rpe ?: "-"}", 
                            color = com.example.ui.theme.GrayMedium, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Slider(
                            value = set.rpe ?: 8f,
                            onValueChange = { newVal ->
                                val rounded = (Math.round(newVal * 2) / 2.0).toFloat()
                                val newSets = loggedExercise.sets.toMutableList()
                                newSets[setIndex] = set.copy(rpe = rounded)
                                onUpdate(loggedExercise.copy(sets = newSets))
                            },
                            valueRange = 1f..10f,
                            steps = 17,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = com.example.ui.theme.GlassBorderLight
                            )
                        )
                    }
                }
            }
            
            Button(
                onClick = {
                    val lastSet = loggedExercise.sets.lastOrNull() ?: WorkoutSet()
                    val newSets = loggedExercise.sets + lastSet.copy(completedAt = null, isWarmup = false)
                    onUpdate(loggedExercise.copy(sets = newSets))
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.GlassLight, contentColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("+ ADD SET", fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun StepperControl(
    value: Double,
    step: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(36.dp)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(com.example.ui.theme.GlassLight, RoundedCornerShape(12.dp))
                .clickable { onValueChange((value - step).coerceAtLeast(0.0)) },
            contentAlignment = Alignment.Center
        ) {
            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        
        Text(
            text = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(com.example.ui.theme.GlassLight, RoundedCornerShape(12.dp))
                .clickable { onValueChange(value + step) },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}
