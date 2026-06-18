package com.example.ui.programs

import android.net.Uri
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.IronLogRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(repository: IronLogRepository, onProgramStarted: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var program by remember { mutableStateOf<Program?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Navigation and state tracking
    var activeWeekIndex by remember { mutableStateOf(0) } // 0 to 11
    var activeTabDayIndex by remember { mutableStateOf(0) }

    LaunchedEffect(activeWeekIndex) {
        activeTabDayIndex = 0
    }

    // Track chosen substitution active slot mappings: Key = Primary Exercise Name, Value = Chosen Active Exercise
    val activeSubstitutions = remember { mutableStateMapOf<String, ProgramExercise>() }

    // Track inline log inputs: Key = "exerciseName_set_X", Value = Weight / Reps string
    val loggedWeights = remember { mutableStateMapOf<String, String>() }
    val loggedReps = remember { mutableStateMapOf<String, String>() }
    val completedSets = remember { mutableStateMapOf<String, Boolean>() }

    var showLoggedSuccessDialog by remember { mutableStateOf(false) }
    var showPlateCalcWeight by remember { mutableStateOf<Double?>(null) }

    val activeProgramState by repository.getActiveProgramState().collectAsState(initial = null)
    var selectedProgramKey by remember { mutableStateOf<String?>(null) }
    
    var activeValidationErrors by remember { mutableStateOf<List<String>?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val processImportedJson: (String) -> Unit = { rawJson ->
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(Program::class.java)
            val rawProgram = adapter.fromJson(rawJson)
            
            val errors = ProgramValidator.validateStrict(rawProgram)
            if (errors.isNotEmpty()) {
                activeValidationErrors = errors
            } else {
                program = ProgramValidator.validateAndSanitize(rawProgram)
                selectedProgramKey = "custom_imported.json"
                activeValidationErrors = null
            }
        } catch (e: Exception) {
            Log.e("ProgramsScreen", "Invalid JSON syntax", e)
            activeValidationErrors = listOf("JSON syntax error: ${e.localizedMessage ?: e.message}")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                    if (!json.isNullOrBlank()) {
                        processImportedJson(json)
                    } else {
                        activeValidationErrors = listOf("Selected JSON file is empty or unreadable.")
                    }
                } catch (e: Exception) {
                    Log.e("ProgramsScreen", "Error picking file", e)
                    activeValidationErrors = listOf("Failed to read selected file: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Auto-select program if already active
    LaunchedEffect(activeProgramState) {
        if (activeProgramState != null) {
            activeWeekIndex = activeProgramState!!.currentWeekIndex.coerceIn(0, 11)
            selectedProgramKey = activeProgramState!!.programKey
        }
    }

    // Load program files on demand when a program is selected / active
    LaunchedEffect(selectedProgramKey) {
        val key = selectedProgramKey ?: return@LaunchedEffect
        if (key == "custom_imported.json") return@LaunchedEffect
        isLoading = true
        // Try reading from Firestore first, else fallback to asset
        var fetchedFromFirestore = false
        if (key == "jeff_nippard.json") {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = firestore.collection("programs").document("program-data").get().await()
                if (doc.exists()) {
                    val fetched = doc.toObject(Program::class.java)
                    if (fetched != null) {
                        program = ProgramValidator.validateAndSanitize(fetched)
                        fetchedFromFirestore = true
                    }
                }
            } catch (e: Exception) {
                Log.e("ProgramsScreen", "Error loading from Firestore, trying fallback", e)
            }
        }

        if (!fetchedFromFirestore) {
            try {
                val json = context.assets.open(key).bufferedReader().use { it.readText() }
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(Program::class.java)
                val rawProgram = adapter.fromJson(json)
                program = ProgramValidator.validateAndSanitize(rawProgram)
            } catch (e: Exception) {
                Log.e("ProgramsScreen", "Failed to load local asset fallback", e)
            }
        }
        isLoading = false
    }

    val currentWeekKey = "week${activeWeekIndex + 1}"
    val targetWeek = program?.weeks?.get(currentWeekKey)
    val daysList = targetWeek?.days ?: emptyList()

    val daysOfWeekList = remember(daysList) {
        val weekDaysAbbrevs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        daysList.mapIndexed { idx, day ->
            val abbrev = if (idx < weekDaysAbbrevs.size) weekDaysAbbrevs[idx] else "Day ${idx + 1}"
            Pair(abbrev, day.dayName)
        }
    }

    val mappedDay = remember(daysList, activeTabDayIndex) {
        daysList.getOrNull(activeTabDayIndex) ?: ProgramDay(dayName = "Rest & Recovery", isRestDay = true)
    }

    // Helper functions for estimated times
    fun parseWorkingSets(workingStr: String?): Int {
        if (workingStr == null) return 3
        val cleaned = workingStr.replace("~", "").trim()
        val numbers = "\\d+".toRegex().findAll(cleaned).map { it.value.toIntOrNull() ?: 3 }.toList()
        return numbers.maxOrNull() ?: 3
    }

    fun parseWarmupSets(warmupStr: String?): Int {
        if (warmupStr == null) return 0
        val cleaned = warmupStr.replace("~", "").trim()
        if (cleaned.equals("n/a", ignoreCase = true) || cleaned.equals("none", ignoreCase = true)) return 0
        val numbers = "\\d+".toRegex().findAll(cleaned).map { it.value.toIntOrNull() ?: 1 }.toList()
        return numbers.maxOrNull() ?: 0
    }

    fun parseRestMinutes(restStr: String?): Double {
        if (restStr == null) return 2.0
        val numbers = "\\d+".toRegex().findAll(restStr).map { it.value.toDoubleOrNull() ?: 2.0 }.toList()
        return if (numbers.size >= 2) {
            (numbers[0] + numbers[1]) / 2.0
        } else if (numbers.isNotEmpty()) {
            numbers[0]
        } else {
            2.0
        }
    }

    // Function to sanitize any UI string to respect the negative constraint list: Block, Phase, Accumulation, Intensification
    fun sanitizeUiText(text: String): String {
        return text.replace("Block", "N/A", ignoreCase = true)
            .replace("Phase", "N/A", ignoreCase = true)
            .replace("Accumulation", "Focus", ignoreCase = true)
            .replace("Intensification", "Hypertrophy", ignoreCase = true)
    }

    // Move remember computations outside LazyColumn builder scope
    val isGrouped = remember(mappedDay) { mappedDay.exercises.any { it.muscleGroup?.isNotBlank() == true } }
    val groupedMap = remember(mappedDay, isGrouped) {
        val actualExercises = mappedDay.exercises
        if (isGrouped) {
            val groups = linkedMapOf<String, MutableList<ProgramExercise>>()
            actualExercises.forEach { ex ->
                val grp = ex.muscleGroup?.trim()?.uppercase() ?: "GENERAL"
                groups.getOrPut(grp) { mutableListOf() }.add(ex)
            }
            groups
        } else {
            linkedMapOf("" to actualExercises.toMutableList())
        }
    }

    val showSelector = activeProgramState == null && selectedProgramKey == null

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
                title = {
                    Text(
                        text = if (showSelector) "CHOOSE PROGRAM" else "PROGRAM DETAILS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    if (!showSelector && activeProgramState == null) {
                        IconButton(onClick = { selectedProgramKey = null }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to selection", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (activeProgramState != null) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    repository.saveActiveProgramState(null)
                                    repository.clearAllTemplates()
                                    selectedProgramKey = null
                                    program = null
                                }
                            }
                        ) {
                            Text("SWITCH", color = com.example.ui.theme.AccentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (activeValidationErrors != null) {
            ImportErrorScreen(
                errors = activeValidationErrors!!,
                onBack = {
                    activeValidationErrors = null
                    selectedProgramKey = null
                    program = null
                },
                modifier = Modifier.padding(padding)
            )
        } else if (showSelector) {
            ProgramSelectionCatalog(
                onSelect = { key ->
                    selectedProgramKey = key
                },
                onImportClick = {
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            if (isLoading || program == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    containerColor = com.example.ui.theme.GrayDark,
                    titleContentColor = Color.White,
                    textContentColor = com.example.ui.theme.GrayMedium,
                    shape = RoundedCornerShape(24.dp),
                    title = { Text("Start Program \uD83D\uDE80", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                    text = { 
                        Text(
                            "Do you want to set '${program?.programName ?: "this program"}' as your main routine?\n\nThis will add the current week's workouts to your quick start templates.",
                            fontSize = 15.sp, 
                            lineHeight = 22.sp
                        ) 
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                if (isLoading) return@Button
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        if (program!!.weeks.isEmpty()) {
                                            android.widget.Toast.makeText(context, "Error: No routines found in program", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        // Clear existing templates to start fresh
                                        repository.clearAllTemplates()

                                        val currentWeekData = program!!.weeks[currentWeekKey]
                                        var templatesAdded = 0
                                        var totalWorkoutsCurrentWeek = 0
                                        currentWeekData?.days?.filter { !it.isRestDay }?.forEach { day ->
                                            val tExercises = day.exercises.mapIndexed { index, ex ->
                                                TemplateExercise(
                                                    exerciseId = UUID.randomUUID().toString(),
                                                    exerciseName = ex.name,
                                                    targetSets = parseWorkingSets(ex.workingSets),
                                                    targetReps = ex.reps?.split("-")?.last()?.toIntOrNull() ?: 10,
                                                    order = index,
                                                    videoUrl = ex.videoUrl
                                                )
                                            }
                                            val template = Template(
                                                id = UUID.randomUUID().toString(),
                                                name = day.dayName,
                                                exercises = tExercises
                                            )
                                            repository.saveTemplate(template)
                                            templatesAdded++
                                            totalWorkoutsCurrentWeek++
                                        }

                                        repository.saveActiveProgramState(
                                            ActiveProgramState(
                                                programKey = selectedProgramKey ?: "jeff_nippard.json",
                                                currentWeekIndex = activeWeekIndex,
                                                workoutsCompletedThisWeek = 0,
                                                totalWorkoutsThisWeek = totalWorkoutsCurrentWeek,
                                                isWeekCompletedMessageShown = false
                                            )
                                        )

                                        android.widget.Toast.makeText(context, "Added $templatesAdded routines", android.widget.Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        onProgramStarted()
                                    } catch (e: Exception) {
                                        Log.e("ProgramsScreen", "Error", e)
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Let's Go!", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { if (!isLoading) showConfirmDialog = false }) {
                            Text("Cancel", color = com.example.ui.theme.GrayMedium)
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // 0. Scrollable Timeline Component with Expandable Week Headers
                item {
                    ProgramTimeline(
                        program = program!!,
                        activeWeekIndex = activeWeekIndex,
                        onWeekSelect = { newWeekIdx ->
                            activeWeekIndex = newWeekIdx
                            activeTabDayIndex = 0
                            coroutineScope.launch {
                                repository.saveActiveProgramState(
                                    ActiveProgramState(
                                        programKey = selectedProgramKey ?: "jeff_nippard.json",
                                        currentWeekIndex = newWeekIdx,
                                        workoutsCompletedThisWeek = 0,
                                        totalWorkoutsThisWeek = 5
                                    )
                                )
                            }
                        }
                    )
                }

                // 1. Week navigation row (Never show words: Block, Phase, Accumulation, Intensification)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (activeWeekIndex > 0) {
                                        activeWeekIndex--
                                        coroutineScope.launch {
                                            repository.saveActiveProgramState(
                                                ActiveProgramState(
                                                    programKey = "jeff_nippard.json",
                                                    currentWeekIndex = activeWeekIndex,
                                                    workoutsCompletedThisWeek = 0,
                                                    totalWorkoutsThisWeek = 5
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = activeWeekIndex > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Previous Week",
                                    tint = if (activeWeekIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }

                            Text(
                                text = "Week ${activeWeekIndex + 1} of 12",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            IconButton(
                                onClick = {
                                    if (activeWeekIndex < 11) {
                                        activeWeekIndex++
                                        coroutineScope.launch {
                                            repository.saveActiveProgramState(
                                                ActiveProgramState(
                                                    programKey = "jeff_nippard.json",
                                                    currentWeekIndex = activeWeekIndex,
                                                    workoutsCompletedThisWeek = 0,
                                                    totalWorkoutsThisWeek = 5
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = activeWeekIndex < 11
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Next Week",
                                    tint = if (activeWeekIndex < 11) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                // 2. Weekly calendar day-based Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        daysOfWeekList.forEachIndexed { idx, (dayAbbrev, title) ->
                            val isSelected = activeTabDayIndex == idx
                            Card(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .padding(horizontal = 2.dp)
                                    .clickable { activeTabDayIndex = idx },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) com.example.ui.theme.AccentGreen else com.example.ui.theme.GlassDark
                                ),
                                border = BorderStroke(1.dp, if (isSelected) Color.White else com.example.ui.theme.GlassBorderDark),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayAbbrev.uppercase(),
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (title.contains("Rest", true)) "REST" else {
                                            if (title.contains("Upper", true)) "UPP"
                                            else if (title.contains("Lower", true)) "LOW"
                                            else if (title.contains("Push", true)) "PSH"
                                            else if (title.contains("Pull", true)) "PLL"
                                            else if (title.contains("Legs", true)) "LGS"
                                            else "WKT"
                                        },
                                        color = if (isSelected) Color.Black.copy(alpha = 0.8f) else com.example.ui.theme.GrayMedium,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Day header with total exercises, est duration, and total rest time (computed from actual exercises)
                if (mappedDay.isRestDay) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🧘 RECOVERY & REST DAY",
                                    color = com.example.ui.theme.AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Rest and recovery is when muscle hypertrophy and adaptation actually happen! Perform Jeff Nippard's suggested general recovery stretches, sleep well, log your nutrition progress, and prepare your body to target the next training session.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val actualExercises = mappedDay.exercises

                    // Calculate computed metrics
                    var computedRestTotal = 0.0
                    var computedDurationTotal = 0.0
                    actualExercises.forEach { ex ->
                        val workingS = parseWorkingSets(ex.workingSets)
                        val warmupS = parseWarmupSets(ex.warmupSets)
                        val totalS = workingS + warmupS
                        val restM = parseRestMinutes(ex.rest)
                        
                        val exRest = (totalS - 1).coerceAtLeast(0) * restM
                        val exWork = totalS * 1.5 // 1.5 mins avg space per set
                        
                        computedRestTotal += exRest
                        computedDurationTotal += (exWork + exRest)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sanitizeUiText(mappedDay.dayName).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Button(
                                        onClick = { showConfirmDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("ACTIVATE WEEK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Exercises", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp)
                                        Text("${actualExercises.size} Items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    Column {
                                        Text("Est. Duration", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp)
                                        Text("${computedDurationTotal.toInt()} mins", color = com.example.ui.theme.AccentGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    Column {
                                        Text("Est. Rest Time", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp)
                                        Text("${computedRestTotal.toInt()} mins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Render exercise list grouped or raw
                    groupedMap.forEach { (muscleGrp, listForMuscleGrp) ->
                        if (muscleGrp.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    Text(
                                        text = muscleGrp,
                                        color = com.example.ui.theme.AccentGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        letterSpacing = 1.sp
                                    )
                                    HorizontalDivider(
                                        color = com.example.ui.theme.GlassBorderDark,
                                        modifier = Modifier.padding(top = 4.dp),
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }

                        items(listForMuscleGrp) { primaryEx ->
                            // Resolve currently swapped active exercise (primary vs substitution)
                            val resolvedEx = activeSubstitutions[primaryEx.name] ?: primaryEx

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (activeSubstitutions.containsKey(primaryEx.name)) com.example.ui.theme.AccentGreen.copy(alpha = 0.6f) else com.example.ui.theme.GlassBorderDark
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Row 1: Exercise name + watch button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = resolvedEx.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                            Text(
                                                text = resolvedEx.muscleGroup ?: "General",
                                                color = com.example.ui.theme.AccentGreen,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        resolvedEx.videoUrl?.let { link ->
                                            IconButton(
                                                onClick = { uriHandler.openUri(link) },
                                                modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Watch Demo",
                                                    tint = com.example.ui.theme.AccentGreen
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Row 2: Routine specs grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Warm-up", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.warmupSets ?: "N/A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Column {
                                            Text("Working Sets", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.workingSets ?: "3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Column {
                                            Text("Reps Target", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.reps ?: resolvedEx.repRange ?: "10", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Column {
                                            Text("Rest Target", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.rest ?: "2m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Intensity Techniques and RPE info
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Early Set RPE", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.earlySetRPE ?: "7-8", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Column {
                                            Text("Last Set RPE", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.lastSetRPEStr, color = com.example.ui.theme.AccentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Column {
                                            Text("Technique", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                            Text(resolvedEx.lastSetTechnique ?: "N/A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }

                                    // Exercise Notes (if present)
                                    resolvedEx.notes?.let { note ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Note",
                                                    tint = com.example.ui.theme.GrayMedium,
                                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = note,
                                                    color = com.example.ui.theme.GrayMedium,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 5. INLINE LOGGING INPUTS (weight, reps, complete per working set)
                                    val totalWorkingSets = parseWorkingSets(resolvedEx.workingSets)
                                    
                                    val isBarbell = remember(resolvedEx.name) {
                                        val lower = resolvedEx.name.lowercase()
                                        lower.contains("barbell") || lower.contains("squat") || lower.contains("press") || lower.contains("deadlift") || lower.contains("row") || lower.contains("bench") || lower.contains("rdl") || lower.contains("clean") || lower.contains("snatch") || lower.contains("smith") || lower.contains("thruster") || lower.contains("curl")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LOG WORKING SETS",
                                            color = com.example.ui.theme.GrayMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        )

                                        if (isBarbell) {
                                            TextButton(
                                                onClick = {
                                                    val typedWeight = loggedWeights["${resolvedEx.name}_set_1"]?.toDoubleOrNull() ?: 100.0
                                                    showPlateCalcWeight = typedWeight
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "CALCULATED PLATES \uD83C\uDFCB\uFE0F",
                                                    color = com.example.ui.theme.AccentGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    for (sNum in 1..totalWorkingSets) {
                                        val logKeyPrefix = "${resolvedEx.name}_set_${sNum}"
                                        val weightVal = loggedWeights[logKeyPrefix] ?: ""
                                        val repsVal = loggedReps[logKeyPrefix] ?: ""
                                        val isCompleted = completedSets[logKeyPrefix] ?: false

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "S$sNum",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isCompleted) com.example.ui.theme.AccentGreen else Color.White,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            
                                            OutlinedTextField(
                                                value = weightVal,
                                                onValueChange = { loggedWeights[logKeyPrefix] = it },
                                                label = { Text("Weight (kg)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.White,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                    focusedLabelColor = Color.White,
                                                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.weight(1f).height(50.dp)
                                            )

                                            OutlinedTextField(
                                                value = repsVal,
                                                onValueChange = { loggedReps[logKeyPrefix] = it },
                                                label = { Text("Reps", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.White,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                    focusedLabelColor = Color.White,
                                                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.weight(1f).height(50.dp)
                                            )

                                            IconButton(
                                                onClick = { completedSets[logKeyPrefix] = !isCompleted },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        if (isCompleted) com.example.ui.theme.AccentGreen else Color.White.copy(alpha = 0.08f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Log set",
                                                    tint = if (isCompleted) Color.Black else Color.White
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Substitutions header and selector
                                    if (primaryEx.substitution1 != null || primaryEx.substitution2 != null) {
                                        Text(
                                            text = "SUBSTITUTIONS AVAILABLE",
                                            color = com.example.ui.theme.GrayMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Render swap selections
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Handle restoring to original option
                                            if (resolvedEx.name != primaryEx.name) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { activeSubstitutions.remove(primaryEx.name) },
                                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                                    border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.SwapHoriz,
                                                                contentDescription = "Swap",
                                                                tint = com.example.ui.theme.AccentGreen,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "Restore original: ${primaryEx.name}",
                                                                color = Color.White,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                        Text("TAP TO RESET", color = com.example.ui.theme.AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            // Render Substitution Option 1
                                            primaryEx.substitution1?.let { sub1 ->
                                                if (resolvedEx.name != sub1.name) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { activeSubstitutions[primaryEx.name] = sub1 },
                                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(sub1.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                                Text("Sets: ${sub1.workingSets ?: "3"} • Reps: ${sub1.reps ?: "8-10"} • Rest: ${sub1.rest ?: "2m"}", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                                            }
                                                            Row {
                                                                sub1.videoUrl?.let { url ->
                                                                    IconButton(
                                                                        onClick = { uriHandler.openUri(url) },
                                                                        modifier = Modifier.size(28.dp).padding(end = 4.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Watch Demo", tint = com.example.ui.theme.GrayMedium)
                                                                    }
                                                                }
                                                                TextButton(
                                                                    onClick = { activeSubstitutions[primaryEx.name] = sub1 },
                                                                    contentPadding = PaddingValues(0.dp),
                                                                    modifier = Modifier.height(30.dp)
                                                                ) {
                                                                    Text("SWAP", color = com.example.ui.theme.AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Render Substitution Option 2
                                            primaryEx.substitution2?.let { sub2 ->
                                                if (resolvedEx.name != sub2.name) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { activeSubstitutions[primaryEx.name] = sub2 },
                                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(sub2.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                                Text("Sets: ${sub2.workingSets ?: "3"} • Reps: ${sub2.reps ?: "8-10"} • Rest: ${sub2.rest ?: "2m"}", color = com.example.ui.theme.GrayMedium, fontSize = 10.sp)
                                                            }
                                                            Row {
                                                                sub2.videoUrl?.let { url ->
                                                                    IconButton(
                                                                        onClick = { uriHandler.openUri(url) },
                                                                        modifier = Modifier.size(28.dp).padding(end = 4.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Watch Demo", tint = com.example.ui.theme.GrayMedium)
                                                                    }
                                                                }
                                                                TextButton(
                                                                    onClick = { activeSubstitutions[primaryEx.name] = sub2 },
                                                                    contentPadding = PaddingValues(0.dp),
                                                                    modifier = Modifier.height(30.dp)
                                                                ) {
                                                                    Text("SWAP", color = com.example.ui.theme.AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Big "Complete & Log Today's Workout" button
                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // Gather logged exercises to complete
                                    val loggedExercises = actualExercises.map { pEx ->
                                        val activeEx = activeSubstitutions[pEx.name] ?: pEx
                                        val workingS = parseWorkingSets(activeEx.workingSets)
                                        val loggedSets = (1..workingS).map { sNum ->
                                            val key = "${activeEx.name}_set_${sNum}"
                                            val wVal = loggedWeights[key]?.toDoubleOrNull() ?: 0.0
                                            val rVal = loggedReps[key]?.toIntOrNull() ?: 10
                                            WorkoutSet(
                                                setNumber = sNum,
                                                weight = wVal,
                                                reps = rVal,
                                                completedAt = System.currentTimeMillis()
                                            )
                                        }
                                        LoggedExercise(
                                            exerciseId = UUID.randomUUID().toString(),
                                            exerciseName = activeEx.name,
                                            videoUrl = activeEx.videoUrl,
                                            sets = loggedSets
                                        )
                                    }

                                    val completedWorkout = Workout(
                                        id = UUID.randomUUID().toString(),
                                        date = System.currentTimeMillis(),
                                        templateName = sanitizeUiText(mappedDay.dayName),
                                        status = "completed",
                                        loggedExercises = loggedExercises
                                    )

                                    repository.finishWorkout(completedWorkout)
                                    
                                    // Clear current entries
                                    loggedWeights.clear()
                                    loggedReps.clear()
                                    completedSets.clear()
                                    
                                    showLoggedSuccessDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp)
                        ) {
                            Text("COMPLETE & LOG TODAYS WORKOUT 🎉", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            }
        }
    }

    if (showLoggedSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showLoggedSuccessDialog = false },
            containerColor = com.example.ui.theme.GrayDark,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Workout Logged! 💪", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
            text = { Text("Great work! Your daily stats have been mapped, calculated, and successfully synced to Firestore. Rest up for your next session!", fontSize = 15.sp, lineHeight = 22.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showLoggedSuccessDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Crushed it!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPlateCalcWeight != null) {
        com.example.ui.progress.PlateCalculatorDialog(
            initialTargetWeight = showPlateCalcWeight!!,
            isKgInitially = true,
            onDismiss = { showPlateCalcWeight = null }
        )
    }
}

@Composable
fun ProgramSelectionCatalog(
    onSelect: (String) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        item {
            Text(
                text = "SCIENCE-BASED SPLITS",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
            )
        }
        
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect("jeff_nippard.json") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECOMMENDED BY PLATFORM",
                            color = com.example.ui.theme.AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(com.example.ui.theme.AccentGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "12 WEEKS",
                                color = com.example.ui.theme.AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "The Bodybuilding Transformation System",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        lineHeight = 26.sp
                    )
                    Text(
                        text = "By Jeff Nippard • Intermediate-Advanced",
                        color = com.example.ui.theme.GrayMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "A science-driven, high-frequency full body split structured to optimize both muscle hypertrophy and raw athletic strength. Guided by precise RPE targets, automatic rest timers, and custom exercise substitution choices.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onSelect("jeff_nippard.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("EXPLORE & START PROGRAM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onImportClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CUSTOM IMPORT",
                        color = com.example.ui.theme.AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Import Custom JSON",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Import your own custom training program structure conforming to the standard JSON format. Validates sets, RPEs, and structural integrity before saving.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onImportClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("BROWSE FILES...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramTimeline(
    program: Program,
    activeWeekIndex: Int,
    onWeekSelect: (Int) -> Unit
) {
    val expandedWeeks = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = "PROGRAM TIMELINE (12-WEEK OVERVIEW)",
            color = Color.LightGray.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(12) { weekIdx ->
                val weekKey = "week${weekIdx + 1}"
                val weekData = program.weeks[weekKey]
                val isSelected = weekIdx == activeWeekIndex
                val isExpanded = expandedWeeks[weekIdx] ?: false

                Card(
                    modifier = Modifier
                        .width(if (isExpanded) 195.dp else 125.dp)
                        .clickable { onWeekSelect(weekIdx) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) com.example.ui.theme.AccentGreen.copy(alpha = 0.15f) else com.example.ui.theme.GlassDark
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) com.example.ui.theme.AccentGreen else com.example.ui.theme.GlassBorderDark
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WEEK ${weekIdx + 1}",
                                color = if (isSelected) com.example.ui.theme.AccentGreen else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        expandedWeeks[weekIdx] = !isExpanded
                                    }
                                    .background(Color.White.copy(alpha = 0.08f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isExpanded) "−" else "+",
                                    color = if (isExpanded) com.example.ui.theme.AccentGreen else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val rawBlock = weekData?.block ?: "Accumulation"
                        val cleanBlock = rawBlock.replace("Block", "N/A", ignoreCase = true)
                            .replace("Phase", "N/A", ignoreCase = true)
                            .replace("Accumulation", "Focus", ignoreCase = true)
                            .replace("Intensification", "Hypertrophy", ignoreCase = true)

                        Text(
                            text = cleanBlock,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                            Spacer(modifier = Modifier.height(8.dp))

                            val days = weekData?.days ?: emptyList()
                            if (days.isEmpty()) {
                                Text(
                                    text = "No days configured",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp
                                )
                            } else {
                                days.forEachIndexed { dayIdx, day ->
                                    val split = getDaySplit(day)
                                    val badgeColor = when (split) {
                                        "Upper" -> Color(0xFF3F51B5)
                                        "Lower" -> Color(0xFFE91E63)
                                        "Push" -> Color(0xFFFF5722)
                                        "Pull" -> Color(0xFF009688)
                                        "Legs" -> Color(0xFF9C27B0)
                                        else -> Color(0xFF607D8B)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Day ${dayIdx + 1}",
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = split.uppercase(),
                                                color = badgeColor,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap + to view splits",
                                color = Color.White.copy(alpha = 0.25f),
                                fontSize = 8.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getDaySplit(day: ProgramDay): String {
    if (day.isRestDay || day.exercises.isEmpty()) return "Recovery"
    val nameLower = day.dayName.lowercase()
    if (nameLower.contains("rest") || nameLower.contains("recovery") || nameLower.contains("off day")) {
        return "Recovery"
    }
    
    if (nameLower.contains("upper")) return "Upper"
    if (nameLower.contains("lower")) return "Lower"
    if (nameLower.contains("push")) return "Push"
    if (nameLower.contains("pull")) return "Pull"
    if (nameLower.contains("legs") || nameLower.contains("leg")) return "Legs"
    
    val muscleGroups = day.exercises.mapNotNull { it.muscleGroup?.lowercase() }.filter { it.isNotBlank() }
    if (muscleGroups.isEmpty()) {
        if (nameLower.contains("squat") || nameLower.contains("deadlift") || nameLower.contains("quad") || nameLower.contains("hamstring")) return "Legs"
        if (nameLower.contains("chest") || nameLower.contains("shoulder") || nameLower.contains("press")) return "Push"
        if (nameLower.contains("back") || nameLower.contains("row") || nameLower.contains("bicep")) return "Pull"
        return "Recovery"
    }
    
    val legCount = muscleGroups.count { it.contains("quad") || it.contains("hamstring") || it.contains("calves") || it.contains("glutes") || it.contains("leg") || it.contains("thigh") }
    val pushCount = muscleGroups.count { it.contains("chest") || it.contains("shoulder") || it.contains("triceps") || it.contains("pecs") || it.contains("push") }
    val pullCount = muscleGroups.count { it.contains("back") || it.contains("biceps") || it.contains("pull") || it.contains("lats") || it.contains("rear") }
    
    if (legCount > muscleGroups.size / 2) return "Legs"
    if (pushCount > pullCount && pushCount > legCount) {
        if (nameLower.contains("upper")) return "Upper"
        return "Push"
    }
    if (pullCount > pushCount && pullCount > legCount) {
        if (nameLower.contains("upper")) return "Upper"
        return "Pull"
    }
    if (legCount > 0) return "Lower"
    return "Upper"
}

@Composable
fun ImportErrorScreen(
    errors: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Error",
            tint = Color(0xFFE53935),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "IMPORT FAILED",
            color = Color(0xFFE53935),
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The uploaded JSON file does not match the required schema structure.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.3f))
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(errors) { error ->
                    Text(
                        text = "• $error",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("GO BACK", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
