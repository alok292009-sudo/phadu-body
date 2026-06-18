package com.example.ui.home

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    val uriHandler = LocalUriHandler.current

    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    var workoutsList by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var rawPrs by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }
    var program by remember { mutableStateOf<Program?>(null) }
    var isLoadingProgram by remember { mutableStateOf(false) }

    // Onboarding wizard step
    var onboardingStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        repository.seedInitialExercises()
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
        launch { repository.getActiveProgramState().collect { activeProgramState = it } }
        launch { repository.getWorkouts().collect { workoutsList = it } }
        launch { repository.getPersonalRecords().collect { rawPrs = it } }
    }

    // Load static training program jeff_nippard.json on launch
    LaunchedEffect(Unit) {
        isLoadingProgram = true
        try {
            val json = context.assets.open("jeff_nippard.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(Program::class.java)
            val rawProgram = adapter.fromJson(json)
            program = ProgramValidator.validateAndSanitize(rawProgram)
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load local program asset", e)
        }
        isLoadingProgram = false
    }

    val currentWeekKey = remember(activeProgramState) {
        val idx = activeProgramState?.currentWeekIndex ?: 0
        "week${idx + 1}"
    }

    val daysList = remember(program, currentWeekKey) {
        program?.weeks?.get(currentWeekKey)?.days ?: emptyList()
    }

    // Selected day index to view detail in dashboard (defaults to currentDayIndex)
    var selectedDayIndex by remember { mutableStateOf(0) }
    val activeProgramStateSnap = activeProgramState
    LaunchedEffect(activeProgramStateSnap) {
        if (activeProgramStateSnap != null) {
            selectedDayIndex = activeProgramStateSnap.currentDayIndex
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF16161A), Color.Black),
                center = Offset(500f, -200f),
                radius = 2500f
            )
        )
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (activeProgramState == null) {
                // ==================== ONBOARDING FLOW ====================
                if (program == null || isLoadingProgram) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    OnboardingWizard(
                        program = program!!,
                        step = onboardingStep,
                        onStepChange = { onboardingStep = it },
                        onStartProgram = {
                            coroutineScope.launch {
                                val state = ActiveProgramState(
                                    programKey = "jeff_nippard.json",
                                    programName = program!!.programName,
                                    currentWeekIndex = 0,
                                    currentDayIndex = 0,
                                    completedWorkoutsMap = emptyMap(),
                                    freeNavigationEnabled = false,
                                    workoutsCompletedThisWeek = 0,
                                    totalWorkoutsThisWeek = daysList.count { !it.isRestDay }
                                )
                                repository.saveActiveProgramState(state)
                                Toast.makeText(context, "Operating system initialized for: ${program!!.programName}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            } else {
                // ==================== GUIDED TRAINING OS DASHBOARD ====================
                if (program == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    DashboardContent(
                        repository = repository,
                        program = program!!,
                        activeProgramState = activeProgramState!!,
                        activeWorkout = activeWorkout,
                        daysList = daysList,
                        selectedDayIndex = selectedDayIndex,
                        onSelectDayIndex = { selectedDayIndex = it },
                        workoutsList = workoutsList,
                        rawPrs = rawPrs,
                        onResumeWorkout = onResumeWorkout,
                        onStartWorkout = onStartWorkout,
                        onToggleFreeNav = { enabled ->
                            coroutineScope.launch {
                                repository.saveActiveProgramState(
                                    activeProgramState!!.copy(freeNavigationEnabled = enabled)
                                )
                            }
                        },
                        onUnlockNextWeek = {
                            coroutineScope.launch {
                                val nextWeekVal = activeProgramState!!.currentWeekIndex + 1
                                val state = activeProgramState!!.copy(
                                    currentWeekIndex = nextWeekVal,
                                    currentDayIndex = 0,
                                    completedWorkoutsMap = emptyMap(),
                                    workoutsCompletedThisWeek = 0
                                )
                                repository.saveActiveProgramState(state)
                                Toast.makeText(context, "UNLOCKED WEEK ${nextWeekVal + 1}!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onResetProgram = {
                            coroutineScope.launch {
                                repository.saveActiveProgramState(null)
                                onboardingStep = 0
                            }
                        }
                    )
                }
            }
        }
    }
}

// ==================== COMPOSABLE COMPONENTS ====================

@Composable
fun OnboardingWizard(
    program: Program,
    step: Int,
    onStepChange: (Int) -> Unit,
    onStartProgram: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Progress Indicator Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            for (i in 0..3) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (step == i) com.example.ui.theme.AccentGreen else Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }

        Surface(
            color = com.example.ui.theme.GrayDark,
            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> {
                        // Overview Step
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "GUIDED OPERATING SYSTEM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.AccentGreen,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = program.programName.uppercase(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "by ${program.author}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = com.example.ui.theme.GrayMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "This isn't a spreadsheet clicker. Beautifully engineered around modern science, this system will guide you set by set, with specific targets, warmups, substitutions, and rest days configured directly from the certified program instructions.",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }

                    1 -> {
                        // Explanation Step
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SCIENCE & RPE PRINCIPLES",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "RPE (Rate of Perceived Exertion) is your velocity controller:\n\n" +
                                    "• Early Sets: Usually 7-8 RPE. Leaves 2-3 reps in reserve to optimize pristine mechanical tension.\n\n" +
                                    "• Last Sets: Driven to ~9-10 RPE / complete failure. Generates hyper-intensified metabolic fatigue.\n\n" +
                                    "• Rest Timers: Science-backed rest durations up to 5 minutes allow target musculature to recover perfect ATP performance.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Start
                        )
                    }

                    2 -> {
                        // Split Step
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "THE WEEKLY TRAINING SPLIT",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Render days of week
                        val weekdays = remember(program) {
                            val daysList = program?.weeks?.get("week1")?.days ?: emptyList()
                            if (program != null && daysList.isNotEmpty()) {
                                val abbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                daysList.mapIndexed { i, day ->
                                    val abbrev = if (i < abbreviations.size) abbreviations[i] else "Day ${i + 1}"
                                    abbrev to day.dayName
                                }
                            } else {
                                listOf(
                                    "Mon" to "Upper (Strength Focus)",
                                    "Tue" to "Lower (Strength Focus)",
                                    "Wed" to "Rest & Recovery Day",
                                    "Thu" to "Push (Hypertrophy Focus)",
                                    "Fri" to "Pull (Hypertrophy Focus)",
                                    "Sat" to "Legs (Hypertrophy Focus)",
                                    "Sun" to "Rest Day"
                                )
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            weekdays.forEach { (day, target) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(day, fontWeight = FontWeight.Bold, color = com.example.ui.theme.AccentGreen, fontSize = 12.sp)
                                    Text(target, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    3 -> {
                        // Final step
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SYSTEM INITIALIZED",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "By clicking below, you'll spin up your active program instance of 'The Bodybuilding Transformation System'. Your metrics (completion percentage, streak, weekly volume, and PR history) will update automatically in real-time.",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Onboarding button actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { onStepChange(step - 1) },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("PREVIOUS")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (step < 3) {
                        onStepChange(step + 1)
                    } else {
                        onStartProgram()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .height(48.dp)
            ) {
                Text(if (step < 3) "CONTINUE" else "START GUIDED PROGRAM NOW 🚀", fontWeight = FontWeight.Black)
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun DashboardContent(
    repository: IronLogRepository,
    program: Program,
    activeProgramState: ActiveProgramState,
    activeWorkout: Workout?,
    daysList: List<ProgramDay>,
    selectedDayIndex: Int,
    onSelectDayIndex: (Int) -> Unit,
    workoutsList: List<Workout>,
    rawPrs: List<PersonalRecord>,
    onResumeWorkout: () -> Unit,
    onStartWorkout: (Workout) -> Unit,
    onToggleFreeNav: (Boolean) -> Unit,
    onUnlockNextWeek: () -> Unit,
    onResetProgram: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val completedMap = remember(activeProgramState, workoutsList) {
        val map = activeProgramState.completedWorkoutsMap.toMutableMap()
        workoutsList.filter { it.status == "completed" && !it.templateId.isNullOrBlank() }.forEach { w ->
            map[w.templateId!!] = true
        }
        map
    }

    val dynamicActiveWeekIndex = remember(completedMap, program) {
        var firstIncomplete = 0
        for (wIdx in 0 until 12) {
            val wKey = "week${wIdx + 1}"
            val weekData = program.weeks[wKey] ?: continue
            val weekDays = weekData.days
            val weekTotalWorkouts = weekDays.count { !it.isRestDay }
            val weekCompletedWorkouts = weekDays.indices.count { dayIdx ->
                !weekDays[dayIdx].isRestDay && completedMap["${wKey}_$dayIdx"] == true
            }
            if (weekTotalWorkouts > 0 && weekCompletedWorkouts < weekTotalWorkouts) {
                firstIncomplete = wIdx
                break
            }
        }
        firstIncomplete
    }

    val shownWeekIndex = activeProgramState.currentWeekIndex
    val shownWeekKey = "week${shownWeekIndex + 1}"
    val shownWeekObj = program.weeks[shownWeekKey]
    val currentWeekName = "${shownWeekObj?.block ?: "Foundation Block"} - Week ${shownWeekIndex + 1}"

    // Calculated metrics
    val totalScheduledDays = remember(program) {
        program.weeks.values.sumOf { block -> block.days.count { !it.isRestDay } }
    }
    
    val totalCompletedScheduledDays = remember(program, completedMap) {
        var count = 0
        program.weeks.forEach { (wKey, weekData) ->
            weekData.days.forEachIndexed { dayIdx, day ->
                if (!day.isRestDay && completedMap["${wKey}_$dayIdx"] == true) {
                    count++
                }
            }
        }
        count
    }
    
    val totalCompletedDays = remember(workoutsList) {
        workoutsList.count { it.status == "completed" }
    }
    
    val completionPercent = remember(totalScheduledDays, totalCompletedScheduledDays) {
        if (totalScheduledDays == 0) 0 else ((totalCompletedScheduledDays.toDouble() / totalScheduledDays) * 100).toInt().coerceIn(0, 100)
    }

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
        workoutsList.filter { it.status == "completed" && it.date >= sevenDaysAgo }
            .sumOf { it.totalVolume }
    }

    val newestPR = remember(rawPrs) {
        rawPrs.flatMap { pr ->
            listOfNotNull(pr.bestWeight, pr.bestVolume)
        }.maxByOrNull { it.date }
    }

    // Checking if all current week workouts are completed
    val totalWorkoutsThisWeek = remember(daysList) { daysList.count { !it.isRestDay } }
    val isWeekCompleted = remember(completedMap, activeProgramState) {
        val wc = (0 until daysList.size).count { idx ->
            val day = daysList[idx]
            !day.isRestDay && completedMap["week${activeProgramState.currentWeekIndex + 1}_$idx"] == true
        }
        wc >= totalWorkoutsThisWeek && totalWorkoutsThisWeek > 0
    }

    // Auto-advance to correct week if FREE NAV is disabled
    LaunchedEffect(dynamicActiveWeekIndex, activeProgramState.freeNavigationEnabled) {
        if (!activeProgramState.freeNavigationEnabled && activeProgramState.currentWeekIndex != dynamicActiveWeekIndex) {
            val nextWeekDays = program.weeks["week${dynamicActiveWeekIndex + 1}"]?.days ?: emptyList()
            repository.saveActiveProgramState(
                activeProgramState.copy(
                    currentWeekIndex = dynamicActiveWeekIndex,
                    currentDayIndex = 0,
                    workoutsCompletedThisWeek = 0,
                    totalWorkoutsThisWeek = nextWeekDays.count { !it.isRestDay }
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "IRON LOGG OS",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "PRECISION GUIDED ATHLETE HARDWARE",
                    color = com.example.ui.theme.AccentGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(com.example.ui.theme.GlassDark, RoundedCornerShape(12.dp))
                    .border(1.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(12.dp))
                    .clickable { onResetProgram() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("RESET ENGINE", color = com.example.ui.theme.ErrorColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Active Program Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CURRENT ACTIVE LESSON",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.GrayMedium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            program.programName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                        Text(
                            currentWeekName.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = com.example.ui.theme.AccentGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${completionPercent}%",
                            color = com.example.ui.theme.AccentGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Timeline slider header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROGRAM TIMELINE",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FREE NAV",
                    color = if (activeProgramState.freeNavigationEnabled) com.example.ui.theme.AccentGreen else com.example.ui.theme.GrayMedium,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Switch(
                    checked = activeProgramState.freeNavigationEnabled,
                    onCheckedChange = onToggleFreeNav,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = com.example.ui.theme.AccentGreen,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.Black
                    )
                )
            }
        }

        // Timeline Slider Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            daysList.forEachIndexed { idx, day ->
                val isSelected = selectedDayIndex == idx
                val isCompleted = completedMap["week${activeProgramState.currentWeekIndex + 1}_$idx"] == true
                val isUnlocked = isDayUnlocked(idx, activeProgramState.currentWeekIndex, daysList, completedMap, activeProgramState.freeNavigationEnabled)
                val dayLabel = when (idx) {
                    0 -> "MON"
                    1 -> "TUE"
                    2 -> "WED"
                    3 -> "THU"
                    4 -> "FRI"
                    5 -> "SAT"
                    6 -> "SUN"
                    else -> "D${idx + 1}"
                }

                Card(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable {
                            if (isUnlocked) {
                                onSelectDayIndex(idx)
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        "LOCKED: Complete prior workouts to progress, or enable FREE NAV!",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) com.example.ui.theme.GlassLight else if (isCompleted) Color(0x3300FF66) else if (!isUnlocked) Color(0x11FFFFFF) else com.example.ui.theme.GrayDark
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) com.example.ui.theme.AccentGreen else if (isCompleted) com.example.ui.theme.AccentGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dayLabel, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (isSelected) Color.White else com.example.ui.theme.GrayMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Status indicators (Icon: Check, Lock, or Target Muscle)
                        if (isCompleted) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = com.example.ui.theme.AccentGreen, modifier = Modifier.size(18.dp))
                        } else if (!isUnlocked) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        } else if (day.isRestDay) {
                            Icon(Icons.Default.Favorite, contentDescription = "Rest", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                        } else {
                            val abbreviation = if (day.dayName.contains("Upper", true)) "UPP"
                                               else if (day.dayName.contains("Lower", true)) "LOW"
                                               else if (day.dayName.contains("Push", true)) "PSH"
                                               else if (day.dayName.contains("Pull", true)) "PLL"
                                               else if (day.dayName.contains("Legs", true)) "LGS"
                                               else "WKT"
                            Text(abbreviation, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Target day overview box
        val selectedDay = daysList.getOrNull(selectedDayIndex)
        if (selectedDay != null) {
            Text(
                text = "SELECTED DAY DETAILS",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDay.dayName.uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        if (completedMap["week${activeProgramState.currentWeekIndex + 1}_$selectedDayIndex"] == true) {
                            Box(
                                modifier = Modifier
                                    .background(com.example.ui.theme.AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("COMPLETED", color = com.example.ui.theme.AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedDay.isRestDay) {
                        // Recovery Section
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = com.example.ui.theme.AccentGreen, modifier = Modifier.padding(end = 8.dp))
                            Text("RECOVERY & MOBILITY DAY HUB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Today is your designated Recovery and growth window. Saturday and Sunday are first-class recovery entities in this ecosystem.\n\n" +
                                    "✨ RECOVERY CUES:\n" +
                                    "• Mobility Work: Perform 10-15 mins of dynamic active stretches (Arm circles, Front-to-back swings, dynamic hips).\n" +
                                    "• Optional Cardio: Low-intensity steady-state (LISS) cardio (Elliptical, brisk walk) for 20-30 mins to flush lactic pooling.\n" +
                                    "• Growth Protocol: Rest target musculature. Drink optimal fluids and maintain caloric intake variables.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color.LightGray
                        )
                    } else {
                        // Exercises listing
                        Text(
                            "EXERCISES (${selectedDay.exercises.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.GrayMedium,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Exercises column
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedDay.exercises.forEachIndexed { i, ex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ex.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                        Text(
                                            "Target sets: ${ex.workingSets ?: "3"} • reps: ${ex.reps ?: ex.repRange ?: "10"}",
                                            fontSize = 11.sp,
                                            color = com.example.ui.theme.GrayMedium
                                        )
                                    }
                                    if (ex.videoUrl != null) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Demo available",
                                            tint = Color.Red,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable { uriHandler.openUri(ex.videoUrl!!) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger actions
                        if (activeWorkout != null) {
                            Button(
                                onClick = onResumeWorkout,
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("RESUME ACTIVE WORKOUT Session 🔄", fontWeight = FontWeight.Black)
                            }
                        } else {
                            val btnLabel = if (completedMap["week${activeProgramState.currentWeekIndex + 1}_$selectedDayIndex"] == true) "RESTART WORKOUT" else "START WORKOUT: ${selectedDay.dayName.uppercase()} 🔥"
                            Button(
                                onClick = {
                                    val newW = selectedDay.toWorkout("week${activeProgramState.currentWeekIndex + 1}", selectedDayIndex)
                                    onStartWorkout(newW)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(btnLabel, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Week complete summary module
        if (isWeekCompleted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.AccentGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, com.example.ui.theme.AccentGreen.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🎉 WEEK COMPLETED SUMMARY!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = com.example.ui.theme.AccentGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Outstanding commitment! You successfully checked off all active training days scheduled for Week ${activeProgramState.currentWeekIndex + 1} with absolute precision.\n\n" +
                                "📊 WEEK STATS:\n" +
                                "• Total Completed: $totalWorkoutsThisWeek / $totalWorkoutsThisWeek \n" +
                                "• Focus blocks: Foundation Block\n" +
                                "• Intensity parameters: Maximum intensity threshold",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (activeProgramState.currentWeekIndex + 1 < program.weeks.size) {
                        Button(
                            onClick = onUnlockNextWeek,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("UNLOCK WEEK ${activeProgramState.currentWeekIndex + 2} / NEXT BLOCK 🚀", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Text(
                            "🎓 ALL BLOCKS FINISHED SUCCESSFULLY!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = com.example.ui.theme.AccentGreen,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Metrics Grid Layout
        Text(
            text = "DASHBOARD PERFORMANCE METRICS",
            color = com.example.ui.theme.GrayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stat 1: streak
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                    Text(text = "$currentStreak DAYS", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                    Text(text = "CURRENT STREAK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium)
                }
            }

            // Stat 2: volume
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Timeline, contentDescription = null, tint = com.example.ui.theme.AccentGreen, modifier = Modifier.size(20.dp))
                    Text(text = if (weeklyVolume > 0) "${weeklyVolume.toInt()} KG" else "0 KG", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                    Text(text = "7-DAY VOLUME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stat 3: days completed
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(text = "$totalCompletedDays WORKOUTS", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    Text(text = "COMPLETED TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium)
                }
            }

            // Stat 4: newest PR
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = com.example.ui.theme.AccentGreen, modifier = Modifier.size(20.dp))
                    val valText = if (newestPR != null) "${newestPR.value.toInt()} KG" else "NONE SET"
                    Text(text = valText, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    Text(text = "MOST RECENT PR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Engine Import Diagnostics Panel
        ImportDiagnosticsPanel(program = program)

        // Collapsible 12-Week Roadmap Timeline (now at the bottom of the Column)
        var expandedWeekIndex by remember { mutableStateOf<Int?>(null) }
        
        Text(
            text = "12-WEEK TRAINING ROADMAP",
            color = com.example.ui.theme.GrayMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                for (wIdx in 0 until 12) {
                    val wKey = "week${wIdx + 1}"
                    val weekData = program.weeks[wKey] ?: continue
                    val isWeekExpanded = expandedWeekIndex == wIdx
                    
                    val weekDays = weekData.days
                    val weekTotalWorkouts = weekDays.count { !it.isRestDay }
                    val weekCompletedWorkouts = weekDays.indices.count { dayIdx ->
                        !weekDays[dayIdx].isRestDay && completedMap["${wKey}_$dayIdx"] == true
                    }
                    val weekProgressPercent = if (weekTotalWorkouts == 0) 0 else ((weekCompletedWorkouts.toDouble() / weekTotalWorkouts) * 100).toInt().coerceIn(0, 100)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Week Title Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isWeekExpanded) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    expandedWeekIndex = if (isWeekExpanded) null else wIdx
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Week ${wIdx + 1} of 12",
                                        fontWeight = FontWeight.Bold,
                                        color = if (wIdx == activeProgramState.currentWeekIndex) com.example.ui.theme.AccentGreen else Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "$weekProgressPercent%",
                                        color = if (weekProgressPercent == 100) com.example.ui.theme.AccentGreen else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = weekData.block.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.GrayMedium,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "$weekCompletedWorkouts of $weekTotalWorkouts completed",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = com.example.ui.theme.GrayMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = weekProgressPercent / 100f,
                                    color = if (weekProgressPercent == 100) com.example.ui.theme.AccentGreen else com.example.ui.theme.AccentGreen.copy(alpha = 0.6f),
                                    trackColor = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 8.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                            Icon(
                                imageVector = if (isWeekExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        // Expanded week details (Days)
                        if (isWeekExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                weekData.days.forEachIndexed { dayIdx, dayObj ->
                                    val isDayCompleted = completedMap["week${wIdx + 1}_$dayIdx"] == true
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                if (isDayCompleted) com.example.ui.theme.AccentGreen.copy(alpha = 0.2f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (!dayObj.isRestDay) {
                                                    val newW = dayObj.toWorkout("week${wIdx + 1}", dayIdx)
                                                    onStartWorkout(newW)
                                                }
                                            }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val dayLabel = when (dayIdx) {
                                                    0 -> "Monday"
                                                    1 -> "Tuesday"
                                                    2 -> "Wednesday"
                                                    3 -> "Thursday"
                                                    4 -> "Friday"
                                                    5 -> "Saturday"
                                                    6 -> "Sunday"
                                                    else -> "Day ${dayIdx + 1}"
                                                }
                                                Text(
                                                    text = dayLabel,
                                                    fontWeight = FontWeight.Bold,
                                                    color = com.example.ui.theme.AccentGreen,
                                                    fontSize = 11.sp
                                                )
                                                if (dayObj.isRestDay) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(start = 8.dp)
                                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("REST", color = Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (dayObj.isRestDay) "Recovery & stretches" else dayObj.dayName,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isDayCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Completed",
                                                tint = com.example.ui.theme.AccentGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (!dayObj.isRestDay) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Start",
                                                tint = Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (wIdx < 11) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

fun isDayUnlocked(dayIdx: Int, weekIndex: Int, daysList: List<ProgramDay>, completedMap: Map<String, Boolean>, freeNav: Boolean): Boolean {
    if (freeNav) return true
    if (dayIdx == 0) return true
    
    // Find the immediately preceding non-rest day
    for (i in dayIdx - 1 downTo 0) {
        val d = daysList[i]
        if (d.isRestDay) continue
        return completedMap["week${weekIndex + 1}_$i"] == true
    }
    return true
}

@Composable
fun ImportDiagnosticsPanel(program: Program?) {
    if (program == null) return
    
    val weeksFound = program.weeks.size
    val daysFound = program.weeks.values.sumOf { it.days.size }
    val workoutsFound = program.weeks.values.sumOf { wk -> wk.days.count { !it.isRestDay } }
    val exercisesFound = program.weeks.values.flatMap { wk -> wk.days.flatMap { it.exercises } }.size
    val subsFound = program.weeks.values.flatMap { wk -> wk.days.flatMap { it.exercises } }.count { it.substitution1 != null || it.substitution2 != null }
    val videoLinksFound = program.weeks.values.flatMap { wk -> wk.days.flatMap { it.exercises } }.count { !it.demoLink.isNullOrBlank() || !it.link.isNullOrBlank() }
    
    // Safety abort Check
    if (weeksFound <= 1) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ErrorColor.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, com.example.ui.theme.ErrorColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ CRITICAL PROGRAM IMPORT FAILURE", color = com.example.ui.theme.ErrorColor, fontWeight = FontWeight.Bold)
                Text("Only 1 week found. Parser failed. Stop execution.", color = Color.White)
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
        border = BorderStroke(1.dp, com.example.ui.theme.AccentGreen.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = com.example.ui.theme.AccentGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ENGINE IMPORT DIAGNOSTICS", color = com.example.ui.theme.AccentGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem("Weeks", weeksFound.toString())
                DiagnosticItem("Days", daysFound.toString())
                DiagnosticItem("Workouts", workoutsFound.toString())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem("Exercises", exercisesFound.toString())
                DiagnosticItem("Substitutions", subsFound.toString())
                DiagnosticItem("Demo Videos", videoLinksFound.toString())
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, valStr: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(text = label.uppercase(), fontSize = 8.sp, color = com.example.ui.theme.GrayMedium, fontWeight = FontWeight.Bold)
        Text(text = valStr, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Black)
    }
}
