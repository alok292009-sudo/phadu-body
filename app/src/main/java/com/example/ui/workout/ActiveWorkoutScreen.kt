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
import com.example.ui.components.*
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
    var lastKnownWorkout by remember { mutableStateOf<Workout?>(null) }
    var availableExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var expandedExerciseIndex by remember { mutableStateOf(0) }
    
    var activeRestTimerEnd by remember { mutableStateOf<Long?>(null) }
    var activeRestTimerDuration by remember { mutableStateOf(0) }

    var showPlateCalcWeight by remember { mutableStateOf<Double?>(null) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showSubstituteDialogIndex by remember { mutableStateOf<Int?>(null) }
    var substituteTargetIndex by remember { mutableStateOf<Int?>(null) }
    var showSubSheet by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    // Bottom Sheet states
    val sheetState = rememberModalBottomSheetState()
    val subSheetState = rememberModalBottomSheetState()
    var showStepperSheet by remember { mutableStateOf(false) }
    var editingSetInfo by remember { mutableStateOf<EditingSetInfo?>(null) }

    LaunchedEffect(Unit) {
        launch { 
            repository.getActiveWorkout().collect { 
                if (it != null) {
                    lastKnownWorkout = it
                }
                activeWorkout = it 
            } 
        }
        launch { repository.getExercises().collect { availableExercises = it } }
    }

    val workoutToRender = activeWorkout ?: lastKnownWorkout
    if (workoutToRender == null) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TextPrimaryColor)
        }
        return
    }

    val workout = workoutToRender
    val totalExCount = workout.loggedExercises.size
    val completedExCount = workout.loggedExercises.count { ex -> 
        ex.sets.isNotEmpty() && ex.sets.all { it.completedAt != null } 
    }
    
    val totalSets = workout.loggedExercises.sumOf { it.sets.size }
    val completedSetsCount = workout.loggedExercises.sumOf { it.sets.count { s -> s.completedAt != null } }
    val setProgress = if (totalSets > 0) completedSetsCount.toFloat() / totalSets else 0f

    if (showSummaryDialog) {
        WorkoutSummaryFullScreen(
            workout = workout,
            completedExercises = completedExCount,
            onDone = {
                showSummaryDialog = false
                onFinish()
            }
        )
        return
    }

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
                                    showSummaryDialog = true
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
                    
                    Box(
                        modifier = Modifier
                            .staggeredEntry(index = index, baseDelayMs = 40, translationYOffsetDp = 16f, springSpec = IronAnimations.springStandard())
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = IronAnimations.springGentle())
                    ) {
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
                                    Text(
                                        text = ex.exerciseName, 
                                        style = IronTypography.Title, 
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

                                val warmupSets = ex.sets.mapIndexed { idx, set -> Pair(idx, set) }.filter { it.second.isWarmup }
                                val workingSets = ex.sets.mapIndexed { idx, set -> Pair(idx, set) }.filter { !it.second.isWarmup }

                                if (warmupSets.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(IronSpacing.x24))
                                    Text("WARM-UP", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                                    Spacer(modifier = Modifier.height(IronSpacing.x16))
                                    
                                    val firstWorkingWeight = workingSets.map { it.second.weight }.firstOrNull { it > 0.0 } ?: 0.0
                                    
                                    warmupSets.forEach { (setIdx, set) ->
                                        val isSetCompleted = set.completedAt != null
                                        val autoWeight = if (set.percentOfWorking != null && firstWorkingWeight > 0.0) {
                                            Math.round((firstWorkingWeight * set.percentOfWorking / 100.0) / 2.5) * 2.5
                                        } else 0.0
                                        val hasManualOverride = set.weight > 0.0
                                        val effectiveWeight = if (hasManualOverride) set.weight else autoWeight
                                        val displayWeightStr = if (effectiveWeight > 0.0) if (effectiveWeight % 1.0 == 0.0) effectiveWeight.toInt().toString() else effectiveWeight.toString() else "—"

                                        SetRow(
                                            set = set,
                                            setIdx = setIdx,
                                            isWarmup = true,
                                            effectiveWeight = effectiveWeight,
                                            displayWeightStr = displayWeightStr,
                                            onWeightClick = {
                                                val initVal = if (effectiveWeight > 0) effectiveWeight else (set.targetWeight ?: 20.0)
                                                editingSetInfo = EditingSetInfo(index, setIdx, initVal, "WEIGHT", "KG", 2.5)
                                                showStepperSheet = true
                                            },
                                            onWeightChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(weight = newVal)
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onRepsClick = {
                                                val initVal = if (set.reps > 0) set.reps.toDouble() else (set.targetReps?.toDouble() ?: 8.0)
                                                editingSetInfo = EditingSetInfo(index, setIdx, initVal, "REPS", "REPS", 1.0)
                                                showStepperSheet = true
                                            },
                                            onRepsChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(reps = newVal.toInt())
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onDoneToggle = {
                                                val nowDone = set.completedAt == null
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(completedAt = if (nowDone) System.currentTimeMillis() else null)
                                                
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            }
                                        )
                                    }
                                    
                                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = IronSpacing.x16))
                                }

                                if (workingSets.isNotEmpty()) {
                                    if (warmupSets.isEmpty()) {
                                        Spacer(modifier = Modifier.height(IronSpacing.x24))
                                    }
                                    Text("WORKING SETS", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                                    Spacer(modifier = Modifier.height(IronSpacing.x16))
                                                               workingSets.forEach { (setIdx, set) ->
                                        val displayWeightStr = null
                                        val effectiveWeight = set.weight
                                        
                                        SetRow(
                                            set = set,
                                            setIdx = setIdx,
                                            isWarmup = false,
                                            effectiveWeight = effectiveWeight,
                                            displayWeightStr = displayWeightStr,
                                            onWeightClick = {
                                                val initVal = if (set.weight > 0) set.weight else (set.targetWeight ?: 20.0)
                                                editingSetInfo = EditingSetInfo(index, setIdx, initVal, "WEIGHT", "KG", 2.5)
                                                showStepperSheet = true
                                            },
                                            onWeightChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(weight = newVal)
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onRepsClick = {
                                                val initVal = if (set.reps > 0) set.reps.toDouble() else (set.targetReps?.toDouble() ?: 8.0)
                                                editingSetInfo = EditingSetInfo(index, setIdx, initVal, "REPS", "REPS", 1.0)
                                                showStepperSheet = true
                                            },
                                            onRepsChange = { newVal ->
                                                val updatedSets = ex.sets.toMutableList()
                                                updatedSets[setIdx] = set.copy(reps = newVal.toInt())
                                                val updatedEx = ex.copy(sets = updatedSets)
                                                val updatedList = workout.loggedExercises.toMutableList()
                                                updatedList[index] = updatedEx
                                                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                                            },
                                            onDoneToggle = {
                                                val nowDone = set.completedAt == null
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
                                            }
                                        )
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
                                                    showSummaryDialog = true
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
            } // Close itemsIndexed
        } // Close LazyColumn

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
        com.example.ui.components.ScrollPickerSheet(
            initialValue = info.currentValue,
            type = info.type,
            onDismiss = { showStepperSheet = false },
            onDone = { newVal ->
                val ex = workout.loggedExercises[info.exerciseIndex]
                val updatedSets = ex.sets.toMutableList()
                val set = updatedSets[info.setIdx]
                val isFirstWorkingSet = !set.isWarmup && info.setIdx == ex.sets.indexOfFirst { !it.isWarmup }
                
                if (info.type == "WEIGHT") {
                    updatedSets[info.setIdx] = set.copy(weight = newVal)
                    if (isFirstWorkingSet) {
                        for (i in updatedSets.indices) {
                            val currSet = updatedSets[i]
                            if (currSet.isWarmup && currSet.percentOfWorking != null) {
                                val calculated = Math.round((newVal * currSet.percentOfWorking) / 2.5) * 2.5
                                updatedSets[i] = currSet.copy(weight = calculated)
                            }
                        }
                    }
                } else {
                    updatedSets[info.setIdx] = set.copy(reps = newVal.toInt())
                }
                
                val updatedEx = ex.copy(sets = updatedSets)
                val updatedList = workout.loggedExercises.toMutableList()
                updatedList[info.exerciseIndex] = updatedEx
                coroutineScope.launch { repository.saveWorkout(workout.copy(loggedExercises = updatedList)) }
                showStepperSheet = false
            }
        )
    }

    val workoutResult = remember(showSummaryDialog) { 
        workoutToRender // Capture the latest visible state
    }
}

@Composable
fun WorkoutSummaryFullScreen(
    workout: Workout,
    completedExercises: Int,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Celebration,
            contentDescription = null,
            tint = SuccessColor,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "SESSION COMPLETE",
            style = IronTypography.Display.copy(fontSize = 32.sp),
            color = TextPrimaryColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            workout.templateName ?: "Workout",
            style = IronTypography.Subheading.copy(color = TextSecondaryColor),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TOTAL VOLUME", style = IronTypography.Footnote, color = TextTertiaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${workout.totalVolume.toInt()}kg",
                    style = IronTypography.Display.copy(fontSize = 32.sp),
                    color = TextPrimaryColor
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("COMPLETED EXERCISES", style = IronTypography.Footnote, color = TextTertiaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "$completedExercises",
                    style = IronTypography.Display.copy(fontSize = 32.sp),
                    color = TextPrimaryColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(IronCorner.RadiusMd)
        ) {
            Text("FINISH", style = IronTypography.Headline)
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

