package com.example.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Exercise
import com.example.model.LoggedExercise
import com.example.model.Workout
import com.example.model.WorkoutSet
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    repository: IronLogRepository,
    onNavigateToPlateCalc: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var availableExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var expandedExerciseIndex by remember { mutableStateOf(0) }
    
    var activeRestTimerEnd by remember { mutableStateOf<Long?>(null) }
    var activeRestTimerDuration by remember { mutableStateOf(0) }

    var showPlateCalcWeight by remember { mutableStateOf<Double?>(null) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showSubstituteDialogIndex by remember { mutableStateOf<Int?>(null) } // Legacy - keeping for now to avoid errors if referenced elsewhere, but will use subSheet
    var substituteTargetIndex by remember { mutableStateOf<Int?>(null) }
    var showSubSheet by remember { mutableStateOf(false) }

    // Bottom Sheet states
    val sheetState = rememberModalBottomSheetState()
    val subSheetState = rememberModalBottomSheetState()
    var showStepperSheet by remember { mutableStateOf(false) }
    var editingSetInfo by remember { mutableStateOf<EditingSetInfo?>(null) }

    LaunchedEffect(Unit) {
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
        launch { repository.getExercises().collect { availableExercises = it } }
    }

    if (activeWorkout == null) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextPrimaryColor)
        }
        return
    }

    val workout = activeWorkout!!
    val totalExCount = workout.loggedExercises.size
    val completedExCount = workout.loggedExercises.count { ex -> 
        ex.sets.isNotEmpty() && ex.sets.all { it.completedAt != null } 
    }
    
    val totalSets = workout.loggedExercises.sumOf { it.sets.size }
    val completedSetsCount = workout.loggedExercises.sumOf { it.sets.count { s -> s.completedAt != null } }
    val setProgress = if (totalSets > 0) completedSetsCount.toFloat() / totalSets else 0f

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .statusBarsPadding()
                    .padding(horizontal = IronSpacing.x16)
                    .padding(top = IronSpacing.x12, bottom = IronSpacing.x16)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                    Spacer(modifier = Modifier.width(IronSpacing.x8))
                    AutoResizingText(
                        text = workout.templateName ?: "Workout",
                        style = IronTypography.Title,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateToPlateCalc) {
                            Icon(Icons.Outlined.Calculate, contentDescription = "Plate Calculator", tint = TextPrimaryColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.finishWorkout(workout)
                                    onFinish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                            shape = RoundedCornerShape(IronCorner.RadiusSm),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("FINISH", style = IronTypography.Headline, fontSize = 12.sp, color = BgColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(IronSpacing.x8))
                
                LinearProgressIndicator(
                    progress = setProgress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = TextPrimaryColor,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(IronSpacing.x12))
                
                Text(
                    text = "EXERCISE ${minOf(completedExCount + 1, totalExCount)} OF $totalExCount",
                    style = IronTypography.Micro.copy(color = TextSecondaryColor)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddExerciseDialog = true },
                containerColor = TextPrimaryColor,
                contentColor = BgColor
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (workout.loggedExercises.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(IronSpacing.x24),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.FitnessCenter,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondaryColor.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(IronSpacing.x16))
                                Text(
                                    "Ready to lift?",
                                    style = IronTypography.Title,
                                    color = TextSecondaryColor
                                )
                                Spacer(modifier = Modifier.height(IronSpacing.x8))
                                Text(
                                    "Add your first exercise to begin your session",
                                    style = IronTypography.Body,
                                    color = TextTertiaryColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                itemsIndexed(workout.loggedExercises, key = { index, ex -> "${ex.exerciseId}_$index" }) { index, ex ->
                    val isActive = index == expandedExerciseIndex
                    val isCompleted = ex.sets.isNotEmpty() && ex.sets.all { it.completedAt != null }
                    
                    if (isCompleted && !isActive) {
                        // Collapsed row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x8)
                                .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                                .bouncyClick { expandedExerciseIndex = index }
                                .padding(IronSpacing.x16),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoResizingText(ex.exerciseName, style = IronTypography.Body, maxLines = 1, modifier = Modifier.weight(1f))
                            Text("✓ ${ex.sets.size} sets", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                        }
                    } else {
                        // Full card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x8)
                                .alpha(if (isActive) 1f else 0.5f)
                                .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                                .bouncyClick { expandedExerciseIndex = index }
                                .padding(IronSpacing.x20)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoResizingText(
                                        text = ex.exerciseName, 
                                        style = IronTypography.Title, 
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(onClick = { 
                                            substituteTargetIndex = index
                                            showSubSheet = true 
                                        }) {
                                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Substitute", tint = TextPrimaryColor.copy(alpha = 0.6f))
                                        }
                                        if (ex.videoUrl != null) {
                                            val uriHandler = LocalUriHandler.current
                                            IconButton(onClick = { uriHandler.openUri(ex.videoUrl!!) }) {
                                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Watch Video", tint = TextPrimaryColor)
                                            }
                                        }
                                    }
                                }

                                                                Spacer(modifier = Modifier.height(IronSpacing.x4))
                                
                                val targetGroup = ex.muscleGroup ?: "General"
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.15f), RoundedCornerShape(IronCorner.RadiusFull))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(targetGroup, style = IronTypography.Caption)
                                    }
                                    
                                    if (ex.technique != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (ex.technique.failure) TechniquePillMini("Failure")
                                            if (ex.technique.myoReps) TechniquePillMini("Myo-Reps")
                                            if (ex.technique.lengthenedPartials) TechniquePillMini("LLPs")
                                            if (ex.technique.staticStretch) TechniquePillMini("Static Stretch")
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(IronSpacing.x16))
                                
                                // Stats grid: "target sets: 3 • reps: 10"
                                val totalWorkingSets = ex.sets.count { !it.isWarmup }
                                val firstTargetReps = ex.sets.firstOrNull { !it.isWarmup }?.targetReps ?: "N/A"
                                val targetRpe = ex.sets.firstOrNull { !it.isWarmup }?.targetRpe ?: "8"
                                Text("Target sets: $totalWorkingSets • reps: $firstTargetReps • RPE: $targetRpe", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                                
                                ex.note?.let {
                                    Spacer(modifier = Modifier.height(IronSpacing.x8))
                                    Text("Note: $it", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                                }

                                Spacer(modifier = Modifier.height(IronSpacing.x24))
                                Text("LOG WORKING SETS", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                                Spacer(modifier = Modifier.height(IronSpacing.x16))
                                
                                ex.sets.forEachIndexed { setIdx, set ->
                                    val isSetCompleted = set.completedAt != null
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = IronSpacing.x16),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${setIdx + 1}", 
                                            style = IronTypography.Subheading.copy(fontWeight = FontWeight.Black), 
                                            modifier = Modifier.width(24.dp)
                                        )
                                        
                                        StepperChip(
                                            value = set.weight,
                                            unit = "KG",
                                            onValueChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(weight = newVal)
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onClick = {
                                                editingSetInfo = EditingSetInfo(index, setIdx, set.weight, "WEIGHT", "KG", 2.5)
                                                showStepperSheet = true
                                            },
                                            modifier = Modifier.weight(1.3f)
                                        )

                                        StepperChip(
                                            value = set.reps.toDouble(),
                                            unit = "REPS",
                                            onValueChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(reps = newVal.toInt())
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onClick = {
                                                editingSetInfo = EditingSetInfo(index, setIdx, set.reps.toDouble(), "REPS", "REPS", 1.0)
                                                showStepperSheet = true
                                            },
                                            modifier = Modifier.weight(1.2f),
                                            step = 1.0
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(if (isSetCompleted) TextPrimaryColor else Color.White.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusMd))
                                                .border(
                                                    1.dp,
                                                    if (isSetCompleted) Color.Transparent else Color.White.copy(alpha = 0.25f),
                                                    RoundedCornerShape(IronCorner.RadiusMd)
                                                )
                                                .bouncyClick {
                                                    val nowDone = !isSetCompleted
                                                    val updatedSets = ex.sets.toMutableList()
                                                    updatedSets[setIdx] = set.copy(completedAt = if (nowDone) System.currentTimeMillis() else null)
                                                    
                                                    if (nowDone && set.rpe == null) {
                                                        updatedSets[setIdx] = updatedSets[setIdx].copy(rpe = 8.0f)
                                                    }
                                                    
                                                    val updatedEx = ex.copy(sets = updatedSets)
                                                    val updatedList = workout.loggedExercises.toMutableList()
                                                    updatedList[index] = updatedEx
                                                    coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                                    
                                                    if (nowDone) {
                                                        val restSecs = set.restTimeSeconds ?: 90
                                                        activeRestTimerDuration = restSecs
                                                        activeRestTimerEnd = System.currentTimeMillis() + (restSecs * 1000L)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Outlined.Check, contentDescription = "Done", tint = if (isSetCompleted) BgColor else TextPrimaryColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                
                                val allSetsCompletedHere = ex.sets.isNotEmpty() && ex.sets.all { it.completedAt != null }
                                if (isActive && allSetsCompletedHere) {
                                    Spacer(modifier = Modifier.height(IronSpacing.x24))
                                    Button(
                                        onClick = {
                                            if (index < workout.loggedExercises.size - 1) {
                                                expandedExerciseIndex = index + 1
                                            } else {
                                                coroutineScope.launch {
                                                    repository.finishWorkout(workout)
                                                    onFinish()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                                        shape = RoundedCornerShape(IronCorner.RadiusSm)
                                    ) {
                                        Text(
                                            if (index < workout.loggedExercises.size - 1) "Next →" else "FINISH WORKOUT",
                                            style = IronTypography.Headline,
                                            color = BgColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rest Timer
            if (activeRestTimerEnd != null) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(IronSpacing.x16)
                ) {
                    com.example.ui.workout.RestTimerBar(
                        endTimeMillis = activeRestTimerEnd!!,
                        totalDurationSeconds = activeRestTimerDuration,
                        onDismiss = { activeRestTimerEnd = null }
                    )
                }
            }
        }
    }

    if (showSubSheet && substituteTargetIndex != null) {
        val exToSubIndex = substituteTargetIndex!!
        val currentEx = workout.loggedExercises[exToSubIndex]
        ModalBottomSheet(
            onDismissRequest = { showSubSheet = false },
            sheetState = subSheetState,
            containerColor = Color(0xFF1C1C1E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(IronSpacing.x24)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "SUBSTITUTE: ${currentEx.exerciseName}",
                    style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(IronSpacing.x24))

                if (currentEx.substitutionOpts.isNotEmpty()) {
                    Text("SUGGESTED ALTERNATIVES", style = IronTypography.Caption, color = TextTertiaryColor, modifier = Modifier.padding(bottom = IronSpacing.x12))
                    currentEx.substitutionOpts.forEach { subName ->
                        val exMatch = availableExercises.find { it.name.equals(subName, ignoreCase = true) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = IronSpacing.x4)
                                .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                                .bouncyClick {
                                    val newEx = currentEx.copy(
                                        exerciseId = exMatch?.id ?: subName,
                                        exerciseName = subName,
                                        muscleGroup = exMatch?.muscleGroup ?: currentEx.muscleGroup,
                                        isSubstitution = true
                                    )
                                    val newList = workout.loggedExercises.toMutableList()
                                    newList[exToSubIndex] = newEx
                                    coroutineScope.launch {
                                        repository.saveWorkout(workout.copy(loggedExercises = newList))
                                        showSubSheet = false
                                    }
                                }
                                .padding(IronSpacing.x16)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(IronSpacing.x12))
                                Text(subName, style = IronTypography.Body)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(IronSpacing.x24))
                }

                Text("ALL EXERCISES", style = IronTypography.Caption, color = TextTertiaryColor, modifier = Modifier.padding(bottom = IronSpacing.x12))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(availableExercises) { ex ->
                        if (!currentEx.substitutionOpts.any { it.equals(ex.name, ignoreCase = true) }) {
                            Text(
                                text = ex.name,
                                style = IronTypography.Body,
                                color = TextPrimaryColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClick {
                                        val newEx = currentEx.copy(
                                            exerciseId = ex.id,
                                            exerciseName = ex.name,
                                            muscleGroup = ex.muscleGroup,
                                            isSubstitution = true
                                        )
                                        val newList = workout.loggedExercises.toMutableList()
                                        newList[exToSubIndex] = newEx
                                        coroutineScope.launch {
                                            repository.saveWorkout(workout.copy(loggedExercises = newList))
                                            showSubSheet = false
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = IronSpacing.x8)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddExerciseDialog) {
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            containerColor = Color(0xFF1C1C1E),
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) { Text("Cancel", color = TextPrimaryColor) }
            },
            title = { Text("Add Exercise", style = IronTypography.Title, color = TextPrimaryColor) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(availableExercises) { ex ->
                        Text(
                            text = ex.name,
                            style = IronTypography.Body,
                            color = TextPrimaryColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClick {
                                    val newList = workout.loggedExercises + LoggedExercise(
                                        exerciseId = ex.id,
                                        exerciseName = ex.name,
                                        muscleGroup = ex.muscleGroup,
                                        sets = listOf(WorkoutSet(reps = 10))
                                    )
                                    coroutineScope.launch {
                                        repository.saveWorkout(workout.copy(loggedExercises = newList))
                                        showAddExerciseDialog = false
                                    }
                                }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            }
        )
    }

    if (showStepperSheet && editingSetInfo != null) {
        val info = editingSetInfo!!
        ModalBottomSheet(
            onDismissRequest = { showStepperSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1C1C1E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(IronSpacing.x24)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ADJUST ${info.type}",
                    style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp)
                )
                Spacer(modifier = Modifier.height(IronSpacing.x32))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy( IronSpacing.x32 )
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .bouncyClick {
                                val newVal = maxOf(0.0, info.currentValue - info.step)
                                editingSetInfo = info.copy(currentValue = newVal)
                                // Apply to workout state
                                val ex = workout.loggedExercises[info.exerciseIndex]
                                val updatedSets = ex.sets.toMutableList()
                                val set = updatedSets[info.setIdx]
                                updatedSets[info.setIdx] = if (info.type == "WEIGHT") set.copy(weight = newVal) else set.copy(reps = newVal.toInt())
                                val updatedEx = ex.copy(sets = updatedSets)
                                val updatedList = workout.loggedExercises.toMutableList()
                                updatedList[info.exerciseIndex] = updatedEx
                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", style = IronTypography.Heading)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayVal = if (info.currentValue % 1.0 == 0.0) "${info.currentValue.toInt()}" else String.format("%.1f", info.currentValue)
                        Text(displayVal, style = IronTypography.Display)
                        Text(info.unit, style = IronTypography.Headline.copy(color = TextSecondaryColor))
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .bouncyClick {
                                val newVal = info.currentValue + info.step
                                editingSetInfo = info.copy(currentValue = newVal)
                                // Apply to workout state
                                val ex = workout.loggedExercises[info.exerciseIndex]
                                val updatedSets = ex.sets.toMutableList()
                                val set = updatedSets[info.setIdx]
                                updatedSets[info.setIdx] = if (info.type == "WEIGHT") set.copy(weight = newVal) else set.copy(reps = newVal.toInt())
                                val updatedEx = ex.copy(sets = updatedSets)
                                val updatedList = workout.loggedExercises.toMutableList()
                                updatedList[info.exerciseIndex] = updatedEx
                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", style = IronTypography.Heading)
                    }
                }
                
                Spacer(modifier = Modifier.height( IronSpacing.x48 ))
                
                Button(
                    onClick = { showStepperSheet = false },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                    shape = RoundedCornerShape(IronCorner.RadiusSm)
                ) {
                    Text("DONE", style = IronTypography.Headline)
                }
            }
        }
    }
}

data class EditingSetInfo(
    val exerciseIndex: Int,
    val setIdx: Int,
    val currentValue: Double,
    val type: String, // "WEIGHT" or "REPS"
    val unit: String,
    val step: Double
)

@Composable
fun TechniquePillMini(label: String) {
    Box(
        modifier = Modifier
            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = IronTypography.Micro.copy(fontSize = 8.sp, color = TextSecondaryColor, letterSpacing = 0.5.sp)
        )
    }
}
@Composable
fun StepperChip(
    value: Double,
    unit: String,
    onValueChange: (Double) -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    step: Double = 2.5
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusMd))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(IronCorner.RadiusMd)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = IronCorner.RadiusMd, bottomStart = IronCorner.RadiusMd))
                .clickable { onValueChange(maxOf(0.0, value - step)) },
            contentAlignment = Alignment.Center
        ) {
            Text("-", style = IronTypography.Headline, color = TextPrimaryColor)
        }
        
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val displayVal = if (value % 1.0 == 0.0) "${value.toInt()}" else String.format("%.1f", value)
                Text(
                    text = displayVal,
                    style = IronTypography.Headline,
                    fontSize = 13.sp
                )
                Text(
                    text = unit,
                    style = IronTypography.Caption.copy(fontSize = 7.sp, color = TextSecondaryColor, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = IronCorner.RadiusMd, bottomEnd = IronCorner.RadiusMd))
                .clickable { onValueChange(value + step) },
            contentAlignment = Alignment.Center
        ) {
            Text("+", style = IronTypography.Headline, color = TextPrimaryColor)
        }
    }
}
