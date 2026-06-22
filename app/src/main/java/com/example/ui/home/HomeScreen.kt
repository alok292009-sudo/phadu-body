package com.example.ui.home

import android.util.Log
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import com.example.data.IronLogRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.theme.*

sealed class InitializeState {
    object Idle : InitializeState()
    object Processing : InitializeState()
    object Success : InitializeState()
    data class Error(val message: String) : InitializeState()
}

@Composable
fun HomeScreen(
    repository: IronLogRepository,
    onStartWorkout: (Workout) -> Unit,
    onResumeWorkout: () -> Unit,
    onProfileClick: () -> Unit,
    onNavigateToTab: (String) -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    var isInitialLoadComplete by remember { mutableStateOf(false) }
    var workoutsList by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var program by remember { mutableStateOf<Program?>(null) }
    var currentWeekData by remember { mutableStateOf<ProgramWeek?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun triggerInitialLoad() {
        loadError = null
        
        coroutineScope.launch {
            try {
                // Collect essential data independently
                launch {
                    repository.getActiveProgramState().collect { 
                        activeProgramState = it 
                        isInitialLoadComplete = true
                    }
                }
                launch {
                    repository.getActiveProgram().collect { program = it }
                }

                launch { repository.getActiveWorkout().collect { activeWorkout = it } }
                launch { repository.getWorkouts().collect { workoutsList = it } }

                // Separate job for dynamic week data based on state
                launch {
                    try {
                        var weekJob: kotlinx.coroutines.Job? = null
                        snapshotFlow { activeProgramState?.currentWeek }.collect { weekNum ->
                            weekJob?.cancel()
                            if (weekNum != null) {
                                weekJob = launch {
                                    try {
                                        repository.getProgramWeek(weekNum).collect { week ->
                                            currentWeekData = week
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Week flow error for $weekNum", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Snapshot flow error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Load error", e)
                val message = e.localizedMessage ?: "Unknown error occurred."
                if (message.contains("permission-denied", ignoreCase = true)) {
                    loadError = "Sign-in required."
                } else {
                    loadError = message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        repository.seedInitialExercises()
        triggerInitialLoad()
    }

    if (loadError != null) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor).padding(IronSpacing.x24), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(IronSpacing.x24), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(IronSpacing.x16))
                    Text(text = "SYNC ERROR", style = IronTypography.Headline, color = Color.White)
                    Spacer(modifier = Modifier.height(IronSpacing.x8))
                    Text(
                        text = loadError ?: "Unknown error occurred.",
                        style = IronTypography.Body,
                        color = TextSecondaryColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(IronSpacing.x24))
                    Button(
                        onClick = { 
                            if (loadError == "Sign-in required.") {
                                coroutineScope.launch { repository.signOut() }
                            } else {
                                triggerInitialLoad() 
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loadError == "Sign-in required.") "SIGN IN" else "RETRY SYNC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val p = program
            if (p != null) {
                // STRICT VALIDATION: Compare weeks in JSON vs Database vs UI
                val actualWeeks = p.weeks.size
                val expectedWeeks = p._meta?.schema?.totalWeeks ?: p.program?.durationWeeks ?: 12
                if (actualWeeks < expectedWeeks || actualWeeks < 12) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Outlined.Warning, contentDescription = "Error", tint = DestructiveColor, modifier = Modifier.size(64.dp))
                            Text("Program Import Error: Weeks Missing", style = IronTypography.Title, color = DestructiveColor, textAlign = TextAlign.Center)
                            Text("The application failed to load all 12 weeks from the JSON. Only $actualWeeks weeks were loaded.", style = IronTypography.Body, color = TextSecondaryColor, textAlign = TextAlign.Center)
                        }
                    }
                    return@Scaffold
                }

                if (isInitialLoadComplete && activeProgramState == null) {
                    OnboardingView(p, repository, onProfileClick)
                } else {
                    DashboardClean(
                        repository = repository,
                        program = p,
                        currentWeekData = currentWeekData,
                        activeProgramState = activeProgramState,
                        isInitialLoadComplete = isInitialLoadComplete,
                        activeWorkout = activeWorkout,
                        workoutsList = workoutsList,
                        onResumeWorkout = onResumeWorkout,
                        onStartWorkout = onStartWorkout,
                        onProfileClick = onProfileClick,
                        onNavigateToDiagnostics = onNavigateToDiagnostics
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextPrimaryColor)
                }
            }
        }
    }
}

@Composable
fun OnboardingView(
    program: Program,
    repository: IronLogRepository,
    onProfileClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val p = program
    val schema = p._meta?.schema
    var notesExpanded by remember { mutableStateOf(false) }
    
    var initState by remember { mutableStateOf<InitializeState>(InitializeState.Idle) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Program Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val brush = Brush.verticalGradient(
                        colors = listOf(TextPrimaryColor.copy(alpha = 0.15f), BgColor)
                    )
                    drawRect(brush)
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = IronSpacing.x24, vertical = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .glassRecipeRaised(RoundedCornerShape(IronCorner.RadiusMd))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = TextPrimaryColor, modifier = Modifier.size(32.dp))
                }
                
                Spacer(modifier = Modifier.height(IronSpacing.x24))
                
                Text(
                    "SYSTEM PROTOCOL",
                    style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 3.sp)
                )
                Spacer(modifier = Modifier.height(IronSpacing.x8))
                
                AutoResizingText(
                    text = p.programName.ifEmpty { "UNTITLED PROGRAM" },
                    style = IronTypography.Heading.copy(lineHeight = 38.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(IronSpacing.x12))
                Text(
                    text = "ENGINEERED BY ${p.program?.author?.uppercase() ?: "SYSTEM"}",
                    style = IronTypography.Caption.copy(color = TextSecondaryColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = IronSpacing.x20).offset(y = (-24).dp),
            verticalArrangement = Arrangement.spacedBy(IronSpacing.x20)
        ) {
            // 2. Quick Stats Section
            Text(
                "QUICK STATS",
                style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                modifier = Modifier.padding(start = 4.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val duration = p.durationWeeks
                val daysPerWeek = schema?.trainingDaysPerWeek
                val focus = schema?.primaryFocus
                val level = schema?.experienceLevel
                val style = schema?.trainingStyle
                
                if (duration > 0 || (daysPerWeek != null && daysPerWeek > 0)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (duration > 0) {
                            StatCard("Duration", if (duration == 1) "1 Week" else "$duration Weeks", modifier = Modifier.weight(1f))
                        } else if (daysPerWeek != null && daysPerWeek > 0) {
                             // Fill row if only one visible
                        }
                        
                        if (daysPerWeek != null && daysPerWeek > 0) {
                            StatCard("Frequency", "$daysPerWeek Days / Wk", modifier = Modifier.weight(1f))
                        } else if (duration > 0) {
                             // Fill row if only one visible
                        }
                    }
                }

                if (!focus.isNullOrEmpty() || !level.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!focus.isNullOrEmpty()) {
                            StatCard("Focus", focus, modifier = Modifier.weight(1f))
                        }
                        if (!level.isNullOrEmpty()) {
                            StatCard("Level", level, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (!style.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Style", style, modifier = Modifier.weight(1f))
                        StatCard("Intensity", "RPE Based", modifier = Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Intensity", "RPE Based", modifier = Modifier.weight(1f))
                        // Just an invisible box to keep layout consistent if needed, 
                        // but user said "shrink wrap content".
                        // StatCard has weight(1f), so if only one exists in Row, it fills.
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 3. Training Split Visualization
            Text(
                "TRAINING SPLIT",
                style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                modifier = Modifier.padding(start = 4.dp)
            )
            
            PremiumCard {
                val daysOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                val defaultMap = mapOf(
                    "Monday" to "Upper Body",
                    "Tuesday" to "Lower Body",
                    "Wednesday" to "Recovery",
                    "Thursday" to "Pull",
                    "Friday" to "Push",
                    "Saturday" to "Legs",
                    "Sunday" to "Recovery"
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    daysOrder.forEach { dayName ->
                        val label = schema?.weekdayMap?.get(dayName) ?: defaultMap[dayName] ?: "Rest"
                        val isRest = label.contains("Rest", ignoreCase = true) || label.contains("Recovery", ignoreCase = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dayName.substring(0, 3).uppercase(), style = IronTypography.Caption.copy(color = TextTertiaryColor, fontWeight = FontWeight.Bold), modifier = Modifier.width(48.dp))
                            Box(modifier = Modifier.size(6.dp).background(if (isRest) TextTertiaryColor.copy(alpha = 0.3f) else TextPrimaryColor, RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label, style = IronTypography.Callout.copy(color = if (isRest) TextTertiaryColor else TextPrimaryColor))
                        }
                    }
                }
            }

            // 4. Program Features
            val techniques = schema?.techniques ?: emptyList()
            if (techniques.isNotEmpty()) {
                Text(
                    "SYSTEM TECHNIQUES",
                    style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                    modifier = Modifier.padding(start = 4.dp)
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 12.dp,
                    crossAxisSpacing = 12.dp
                ) {
                    techniques.forEach { feature ->
                        Box(modifier = Modifier.glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(feature.uppercase(), style = IronTypography.Micro.copy(color = TextSecondaryColor, letterSpacing = 0.5.sp))
                        }
                    }
                }
            }

            // 5. Program Notes (Expandable)
            val notes = p.program?.notes
            if (!notes.isNullOrEmpty()) {
                PremiumCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().bouncyClick { notesExpanded = !notesExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PROGRAM NOTES", style = IronTypography.Caption.copy(color = TextSecondaryColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        Text(if (notesExpanded) "COLLAPSE" else "EXPAND", style = IronTypography.Micro.copy(color = TextPrimaryColor))
                    }
                    
                    if (notesExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = notes,
                            style = IronTypography.Body.copy(fontSize = 13.sp, lineHeight = 20.sp, color = TextSecondaryColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .bouncyClick(enabled = initState is InitializeState.Idle || initState is InitializeState.Error) {
                        Log.d("IronLogDiagnostics", "Initialize Button Pressed")
                        initState = InitializeState.Processing
                        
                        val calendar = Calendar.getInstance()
                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val initialDaySlot = when (currentDayOfWeek) {
                            Calendar.MONDAY -> 0
                            Calendar.TUESDAY -> 1
                            Calendar.WEDNESDAY -> 2
                            Calendar.THURSDAY -> 3
                            Calendar.FRIDAY -> 4
                            Calendar.SATURDAY -> 5
                            Calendar.SUNDAY -> 6
                            else -> 0
                        }

                        val state = ActiveProgramState(
                            programName = p.programName.ifEmpty { "bodybuilding_transformation" },
                            author = p.program?.author ?: "JEFF NIPPARD",
                            currentWeek = 1,
                            currentDaySlot = initialDaySlot,
                            completedWorkoutsMap = emptyMap(),
                            startDate = System.currentTimeMillis(),
                            totalWeeks = if (p.durationWeeks > 0) p.durationWeeks else 12,
                            warmupProtocol = p.warmupProtocol
                        )
                        Log.d("IronLogDiagnostics", "State object created: $state")
                        
                        coroutineScope.launch { 
                            Log.d("IronLogDiagnostics", "Launch coroutine for saving state")
                            try {
                                kotlinx.coroutines.withTimeout(12000) {
                                    Log.d("IronLogDiagnostics", "Calling repository.saveActiveProgramState")
                                    repository.saveActiveProgramState(state)
                                    Log.d("IronLogDiagnostics", "repository.saveActiveProgramState completed successfully")
                                    
                                    initState = InitializeState.Success
                                }
                            } catch (e: Exception) {
                                Log.e("IronLogDiagnostics", "Initialization failed", e)
                                initState = InitializeState.Error(e.localizedMessage ?: "Connection Timeout")
                            }
                        }
                    }
                    .background(
                        when(initState) {
                            is InitializeState.Error -> DestructiveColor
                            InitializeState.Success -> Color(0xFF4CAF50)
                            else -> TextPrimaryColor
                        }, 
                        RoundedCornerShape(IronCorner.RadiusMd)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when(initState) {
                    InitializeState.Processing -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BgColor, strokeWidth = 2.dp)
                    }
                    InitializeState.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = BgColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PROTOCOL READY", style = IronTypography.Headline.copy(color = BgColor, letterSpacing = 1.sp))
                        }
                    }
                    is InitializeState.Error -> {
                        Text("RETRY INITIALIZATION", style = IronTypography.Headline.copy(color = Color.White, letterSpacing = 1.sp))
                    }
                    InitializeState.Idle -> {
                        Text(
                            text = "INITIALIZE PROTOCOL", 
                            style = IronTypography.Headline.copy(color = BgColor, letterSpacing = 1.sp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(IronSpacing.x48))
        }
    }
}

@Suppress("ComposableNaming")
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val xSpace = mainAxisSpacing.roundToPx()
        val ySpace = crossAxisSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        
        var maxWidth = 0
        var height = 0
        var x = 0
        var y = 0
        var rowHeight = 0
        
        val positions = mutableListOf<androidx.compose.ui.unit.IntOffset>()
        
        placeables.forEach { placeable ->
            if (x + placeable.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + ySpace
                rowHeight = 0
            }
            positions.add(androidx.compose.ui.unit.IntOffset(x, y))
            rowHeight = maxOf(rowHeight, placeable.height)
            x += placeable.width + xSpace
            maxWidth = maxOf(maxWidth, x)
        }
        height = y + rowHeight
        
        layout(maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(positions[index])
            }
        }
    }
}

@Composable
fun DashboardClean(
    repository: IronLogRepository,
    program: Program,
    currentWeekData: ProgramWeek?,
    activeProgramState: ActiveProgramState?,
    isInitialLoadComplete: Boolean,
    activeWorkout: Workout?,
    workoutsList: List<Workout>,
    onResumeWorkout: () -> Unit,
    onStartWorkout: (Workout) -> Unit,
    onProfileClick: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val todayWeekday = remember {
        val cal = Calendar.getInstance()
        // Convert to 0=Mon, ..., 6=Sun
        (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    }

    val completedMap = activeProgramState?.completedWorkoutsMap ?: emptyMap()
    val weekKey = "week${activeProgramState?.currentWeek ?: 1}"
    val daysList = currentWeekData?.days ?: emptyList()
    
    // Session selection logic
    val progressionDayIndex = activeProgramState?.currentDaySlot ?: 0
    val todayTargetDayIndex = if (daysList.size == 7) todayWeekday else progressionDayIndex
    
    // selectedDay represents what we show on the Home card
    val selectedDay = daysList.getOrNull(todayTargetDayIndex) ?: daysList.getOrNull(progressionDayIndex)
    
    var showWarmupDialog by remember { mutableStateOf(false) }

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

    val dayNames = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    val currentSlotDayName = dayNames.getOrNull(todayTargetDayIndex) ?: "REST DAY"

    val todayInfo = remember {
        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        sdf.format(Date())
    }
    val todayDayName = remember {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        sdf.format(Date()).uppercase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IronSpacing.x16)
            .padding(top = IronSpacing.x20, bottom = IronSpacing.x20)
    ) {
        // Top row (Health Header)
        Row(
            modifier = Modifier
                .staggeredEntry(0)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = IronSpacing.x16)
                .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                .padding(horizontal = IronSpacing.x16, vertical = IronSpacing.x12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(todayInfo.uppercase(), style = IronTypography.Micro.copy(color = TextSecondaryColor, letterSpacing = 2.sp))
                Text("TODAY IS $todayDayName", style = IronTypography.Title.copy(letterSpacing = 1.5.sp), fontWeight = FontWeight.Black)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(IronCorner.RadiusFull)).background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(IronCorner.RadiusFull))
                    .bouncyClick { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Action Recommendation
        selectedDay?.let { day ->
            Column(modifier = Modifier.staggeredEntry(1)) {
                val isActuallyToday = todayTargetDayIndex == todayWeekday
                Text(
                    if (day.isRestDay) "TODAY IS FOR RECOVERY" else "RECOMMENDED ACTION",
                    style = IronTypography.Caption.copy(color = SuccessColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.padding(bottom = IronSpacing.x12, start = 4.dp)
                )
                Text(
                    if (day.isRestDay) "Focus on mobility and restoration." else "Ready for ${day.trainingDay.uppercase()}?",
                    style = IronTypography.Body.copy(color = TextSecondaryColor, fontSize = 12.sp),
                    modifier = Modifier.padding(bottom = IronSpacing.x20, start = 4.dp)
                )
            }
        }

        // PROGRAM DASHBOARD
        Column(
            modifier = Modifier.staggeredEntry(2, springSpec = IronAnimations.springBouncy()).fillMaxWidth().padding(bottom = IronSpacing.x24),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "PROGRAM DASHBOARD",
                style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp)
            )
            
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "${program.durationWeeks} Week Transformation Program",
                        style = IronTypography.Body.copy(color = TextPrimaryColor, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    )
                    
                    val cw = activeProgramState?.currentWeek ?: 1
                    val duration = program.durationWeeks.coerceAtLeast(1)
                    val wCompleted = cw - 1
                    val wRem = (duration - cw).coerceAtLeast(0)
                    val completionPct = ((wCompleted.toFloat() / duration.toFloat()) * 100).toInt()
                    val cDayNum = (activeProgramState?.currentDaySlot ?: 0) + 1
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Current Week", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                            Text("Week $cw", style = IronTypography.Body.copy(color = SuccessColor, fontWeight = FontWeight.Black))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Current Day", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                            Text("Day $cDayNum", style = IronTypography.Body.copy(color = TextPrimaryColor, fontWeight = FontWeight.Black))
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Weeks Completed", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                            Text("$wCompleted", style = IronTypography.Body.copy(color = TextPrimaryColor, fontWeight = FontWeight.Black))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Weeks Remaining", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                            Text("$wRem", style = IronTypography.Body.copy(color = TextPrimaryColor, fontWeight = FontWeight.Black))
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Program Completion", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                            Text("$completionPct%", style = IronTypography.Caption.copy(color = SuccessColor, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(0.05f), RoundedCornerShape(100))) {
                            Box(modifier = Modifier.fillMaxWidth(completionPct / 100f).fillMaxHeight().background(SuccessColor, RoundedCornerShape(100)))
                        }
                    }
                }
            }
        }

        // Stats row (Moved Up)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = IronSpacing.x24),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val stats = listOf(
                Pair("$currentStreak", "STREAK"),
                Pair("${weeklyVolume.toInt()}kg", "VOL"),
                Pair("$totalWorkouts", "DONE")
            )
            stats.forEach { (value, label) ->
                StatCard(label = label, value = value, modifier = Modifier.weight(1f))
            }
        }

        // Hero card (Mission)
        val missionKey = activeProgramState?.let { "${it.currentWeek}_${todayTargetDayIndex}" } ?: "no_program"
        AnimatedContent(
            targetState = missionKey,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn(tween(300))).togetherWith(slideOutHorizontally { -it } + fadeOut(tween(300)))
            },
            label = "mission_card_transition"
        ) { _ ->
            val targetWeek = activeProgramState?.currentWeek ?: 1
            val daysListLocal = currentWeekData?.days ?: emptyList()
            val selectedDayLocal = selectedDay 
            val currentSlotDayNameLocal = dayNames.getOrNull(todayTargetDayIndex) ?: (if (selectedDayLocal?.isRestDay == true) "REST DAY" else "SESSION")

            if (selectedDayLocal != null) {
                Column {
                    Text(
                        if (selectedDayLocal.isRestDay) "RECOVERY MISSION" else "ACTIVE MISSION",
                        style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp),
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                    PremiumCard(modifier = Modifier.padding(bottom = IronSpacing.x24)) {
                        Text(
                            text = if (selectedDayLocal.isRestDay) "FULLY RECHARGE" else "TIME TO WORK",
                            style = IronTypography.Micro.copy(color = SuccessColor, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSlotDayNameLocal,
                                    style = IronTypography.Micro.copy(color = TextPrimaryColor, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (selectedDayLocal.isRestDay) "R&R / RECOVERY" else selectedDayLocal.displayName.ifEmpty { selectedDayLocal.trainingDay }.uppercase(),
                                    style = IronTypography.Title.copy(fontWeight = FontWeight.Black),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "WEEK $targetWeek • TODAY'S PROTOCOL",
                                    style = IronTypography.Micro.copy(color = TextSecondaryColor, letterSpacing = 1.sp)
                                )
                            }
                            Box(modifier = Modifier.background(SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(IronCorner.RadiusFull)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(if (selectedDayLocal.isRestDay) "RELAX" else "TARGET", style = IronTypography.Caption.copy(color = SuccessColor, fontSize = 9.sp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(IronSpacing.x20))
                        
                        if (selectedDayLocal.isRestDay) {
                            Spacer(modifier = Modifier.height(IronSpacing.x20))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SuccessColor.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusMd))
                                    .border(1.dp, SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(IronCorner.RadiusMd))
                                    .padding(vertical = IronSpacing.x24),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RestDayAnimation()
                                    Spacer(modifier = Modifier.height(IronSpacing.x16))
                                    Text(
                                        "RECHARGE YOUR BATTERY",
                                        style = IronTypography.Headline.copy(color = SuccessColor, letterSpacing = 1.sp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(IronSpacing.x20))
                            Text(
                                selectedDayLocal.recovery?.instructions ?: "Rest is where the growth happens. Ensure high protein intake and 8+ hours of sleep today.",
                                style = IronTypography.Body.copy(color = TextSecondaryColor, fontSize = 13.sp),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = IronSpacing.x12)
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IronSpacing.x8)) {
                                selectedDayLocal.exercises.mapNotNull { it.muscleGroup }.distinct().take(3).forEach { m ->
                                    Box(modifier = Modifier.border(0.5.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(IronCorner.RadiusSm)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(m.uppercase(), style = IronTypography.Micro.copy(color = TextSecondaryColor))
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(IronSpacing.x24))
                        
                        if (activeWorkout != null) {
                            Button(onClick = onResumeWorkout, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor), shape = RoundedCornerShape(IronCorner.RadiusMd)) {
                                Text("RESUME LOGGING", style = IronTypography.Headline, color = BgColor)
                            }
                        } else if (selectedDayLocal.isRestDay) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val totalDays = daysList.size.takeIf { it > 0 } ?: 7
                                        val nextSlot = todayTargetDayIndex + 1
                                        val nextWeek = if (nextSlot >= totalDays) targetWeek + 1 else targetWeek
                                        val finalSlot = nextSlot % totalDays
                                        try {
                                            repository.saveActiveProgramState(activeProgramState?.copy(currentDaySlot = finalSlot, currentWeek = nextWeek))
                                        } catch (e: Exception) {
                                            Log.e("HomeScreen", "Error saving active program state", e)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f), contentColor = TextPrimaryColor),
                                shape = RoundedCornerShape(IronCorner.RadiusMd),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FastForward, contentDescription = null, tint = TextPrimaryColor, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SKIP TO TOMORROW'S WORKOUT", style = IronTypography.Headline, color = TextPrimaryColor)
                                }
                            }
                        } else {
                            Button(onClick = {
                                val newW = selectedDayLocal.toWorkout("week$targetWeek", todayTargetDayIndex)
                                onStartWorkout(newW)
                            }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor), shape = RoundedCornerShape(IronCorner.RadiusMd)) {
                                Text("BEGIN SESSION", style = IronTypography.Headline, color = BgColor)
                            }
                        }
                    }
                }
            } else {
                // Empty State / Fallback
                PremiumCard(modifier = Modifier.padding(bottom = IronSpacing.x24)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                        Icon(Icons.Outlined.Assignment, contentDescription = null, tint = TextTertiaryColor, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("NO SESSION SCHEDULED", style = IronTypography.Title, color = TextPrimaryColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Head to the Program tab to start a transformation.", style = IronTypography.Body, color = TextSecondaryColor, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Row(modifier = Modifier.staggeredEntry(3).fillMaxWidth().padding(bottom = IronSpacing.x32), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f).height(48.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusMd)).bouncyClick { showWarmupDialog = true }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Whatshot, null, tint = SuccessColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WARM UP", style = IronTypography.Caption, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.weight(1f).height(48.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusMd)).bouncyClick { onStartWorkout(Workout(templateName = "New Workout", date = System.currentTimeMillis())) }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, tint = TextPrimaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NEW LOG", style = IronTypography.Caption, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.weight(0.5f).height(48.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusMd)).bouncyClick { onNavigateToDiagnostics() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BugReport, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
            }
        }

        // Recent performance (Simplified)
        val lastWorkout = workoutsList.filter { it.status == "completed" }.maxByOrNull { it.date }
        if (lastWorkout != null) {
            val sdf = SimpleDateFormat("MMM dd", Locale.US)
            Column(modifier = Modifier.staggeredEntry(4)) {
                Text("RECENT PERFORMANCE", style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 2.sp), modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().glassRecipe(RoundedCornerShape(IronCorner.RadiusMd)).padding(IronSpacing.x16), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(lastWorkout.templateName ?: "UNTITLED SESSION", style = IronTypography.Headline, maxLines = 1)
                        Text(sdf.format(Date(lastWorkout.date)).uppercase(), style = IronTypography.Micro.copy(color = TextSecondaryColor))
                    }
                    Text("${lastWorkout.totalVolume.toInt()} KG", style = IronTypography.Subheading.copy(fontWeight = FontWeight.Black))
                }
            }
        }

        Spacer(modifier = Modifier.height(IronSpacing.x48))
    }

    if (showWarmupDialog) {
        AlertDialog(
            onDismissRequest = { showWarmupDialog = false },
            containerColor = Color(0xFF1C1C1E),
            confirmButton = { Button(onClick = { showWarmupDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = TextPrimaryColor, contentColor = BgColor)) { Text("GOT IT") } },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Whatshot, null, tint = SuccessColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("WARM-UP PROTOCOL", style = IronTypography.Title, color = TextPrimaryColor)
            } },
            text = {
                val protocol = program.warmupProtocol
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("GENERAL WARM-UP (5-10 MINS)", style = IronTypography.Caption, color = TextTertiaryColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (protocol?.general?.exercises?.isNotEmpty() == true) {
                        protocol.general.exercises.forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("• ", color = TextPrimaryColor)
                                Text("${item.name}: ${item.amount}", style = IronTypography.Body, color = TextSecondaryColor)
                            }
                        }
                    } else {
                        listOf("5-10 mins light cardio", "Dynamic stretching", "Bodyweight squats").forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("• ", color = TextPrimaryColor)
                                Text(item, style = IronTypography.Body, color = TextSecondaryColor)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("EXERCISE SPECIFIC", style = IronTypography.Caption, color = TextTertiaryColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Follow the specific warmup ramp for each movement once you begin.", style = IronTypography.Caption, color = TextSecondaryColor)
                }
            }
        )
    }
}


@Composable
fun RestDayAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "rest_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse circles
        Box(
            modifier = Modifier
                .size(100.dp * scale)
                .background(SuccessColor.copy(alpha = alpha), RoundedCornerShape(IronCorner.RadiusFull))
        )
        Box(
            modifier = Modifier
                .size(70.dp * scale * 0.8f)
                .background(SuccessColor.copy(alpha = alpha * 1.5f), RoundedCornerShape(IronCorner.RadiusFull))
        )
        
        // Floating Zzz symbols
        repeat(3) { i ->
            val floatAnim = rememberInfiniteTransition(label = "z_float_$i")
            val yOffset by floatAnim.animateFloat(
                initialValue = 0f,
                targetValue = -60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, delayMillis = i * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "y_offset"
            )
            val xOffset by floatAnim.animateFloat(
                initialValue = 0f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, delayMillis = i * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "x_offset"
            )
            val zAlpha by floatAnim.animateFloat(
                initialValue = 0.8f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, delayMillis = i * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )

            Text(
                "Z",
                style = IronTypography.Title.copy(fontWeight = FontWeight.Black, fontSize = (14 + i * 4).sp),
                color = SuccessColor.copy(alpha = zAlpha),
                modifier = Modifier.offset(x = (20 + i * 10).dp + xOffset.dp, y = yOffset.dp)
            )
        }

        // Icon
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(IronCorner.RadiusFull),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Hotel,
                    contentDescription = null,
                    tint = SuccessColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

