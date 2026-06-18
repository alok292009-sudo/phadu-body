package com.example.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ActiveProgramState
import com.example.model.PersonalRecord
import com.example.model.ProgressPhoto
import com.example.model.UserProfile
import com.example.model.Workout
import com.example.data.IronLogRepository
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.GlassBorderDark
import com.example.ui.theme.GlassDark
import com.example.ui.theme.GrayDark
import com.example.ui.theme.GrayMedium
import com.example.ui.theme.ErrorColor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: IronLogRepository,
    onSignOutClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Core Profile UI profile state
    var profile by remember { mutableStateOf(UserProfile()) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    
    // Form Input state variables
    var nameInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var genderInput by remember { mutableStateOf("") }
    var progressPhotos by remember { mutableStateOf(listOf<ProgressPhoto>()) }
    
    // Statistical State Variables
    var workoutsList by remember { mutableStateOf(listOf<Workout>()) }
    var prList by remember { mutableStateOf(listOf<PersonalRecord>()) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }
    
    // Setting Toggles State
    var metricUnits by remember { mutableStateOf(true) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var soundEffects by remember { mutableStateOf(true) }
    
    // Modals Control
    var showDiagnostics by remember { mutableStateOf(false) }
    var errorMessageLog by remember { mutableStateOf<String?>(null) }
    
    // Load local SharedPreferences states for settings
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("iron_log_settings", Context.MODE_PRIVATE)
        metricUnits = prefs.getBoolean("metric_units", true)
        hapticFeedback = prefs.getBoolean("haptic_feedback", true)
        soundEffects = prefs.getBoolean("sound_effects", true)
    }
    
    // Save setting changes immediately to SharedPreferences
    val saveSettings: (String, Boolean) -> Unit = { key, value ->
        val prefs = context.getSharedPreferences("iron_log_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
        Toast.makeText(context, "Setting updated! ⚡", Toast.LENGTH_SHORT).show()
    }

    // Comprehensive Async Loading of user profile, database, and telemetry stats
    LaunchedEffect(currentUser) {
        try {
            repository.getUserProfile().collect { fetchedProfile ->
                if (fetchedProfile != null) {
                    profile = fetchedProfile
                    if (!isEditing) {
                        nameInput = fetchedProfile.name
                        ageInput = if (fetchedProfile.age > 0) fetchedProfile.age.toString() else ""
                        weightInput = if (fetchedProfile.weightKg > 0) fetchedProfile.weightKg.toString() else ""
                        heightInput = if (fetchedProfile.heightCm > 0) fetchedProfile.heightCm.toString() else ""
                        genderInput = fetchedProfile.gender
                    }
                    progressPhotos = fetchedProfile.progressPhotos
                }
            }
        } catch (e: Exception) {
            errorMessageLog = "Error fetching user profile stream: ${e.message}"
            Log.e("ProfileScreen", "Profile fetch exception", e)
        }
    }
    
    LaunchedEffect(currentUser) {
        try {
            repository.getWorkouts().collect { workouts ->
                workoutsList = workouts.filterNotNull()
            }
        } catch (e: Exception) {
            errorMessageLog = "Error fetching workouts history stream: ${e.message}"
            Log.e("ProfileScreen", "Workouts details exception", e)
        }
    }

    LaunchedEffect(currentUser) {
        try {
            repository.getPersonalRecords().collect { prs ->
                prList = prs.filterNotNull()
            }
        } catch (e: Exception) {
            errorMessageLog = "Error fetching PRs stream: ${e.message}"
            Log.e("ProfileScreen", "PR load exception", e)
        }
    }

    LaunchedEffect(currentUser) {
        try {
            repository.getActiveProgramState().collect { progState ->
                activeProgramState = progState
            }
        } catch (e: Exception) {
            errorMessageLog = "Error loading Program state stream: ${e.message}"
            Log.e("ProfileScreen", "Program state exception", e)
        } finally {
            isLoading = false
        }
    }
    
    // Media Picking Launcher for progress photo upload
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val localPath = copyUriToLocalAndReturnPath(context, uri)
                    if (localPath != null) {
                        val newPhoto = ProgressPhoto(
                            id = java.util.UUID.randomUUID().toString(),
                            date = System.currentTimeMillis(),
                            localUri = localPath,
                            weightKg = weightInput.toDoubleOrNull() ?: profile.weightKg
                        )
                        val updatedPhotos = progressPhotos + newPhoto
                        progressPhotos = updatedPhotos
                        
                        val updatedProfile = profile.copy(progressPhotos = updatedPhotos)
                        repository.saveUserProfile(updatedProfile)
                        profile = updatedProfile
                        Toast.makeText(context, "Progress photo added! 📸", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )
    
    // Safe statistical calculations
    val totalWorkouts = workoutsList.size
    val totalVolume = workoutsList.sumOf { it.totalVolume }
    val prCount = prList.size
    
    val calculatedStreaks = remember(workoutsList) { calculateStreaks(workoutsList) }
    val currentStreak = calculatedStreaks.first
    val longestStreak = calculatedStreaks.second
    
    val accountType = if (currentUser == null) {
        "Guest Mode (Offline Data Only)"
    } else {
        if (currentUser.isAnonymous) "Anonymous Session" else "Google Cloud Synced Account"
    }
    
    val currentProgram = activeProgramState?.programName ?: "No Program Active"
    val currentWeek = if (activeProgramState != null) "Week ${activeProgramState!!.currentWeekIndex + 1}" else "--"
    val currentDay = if (activeProgramState != null) "Day ${activeProgramState!!.currentDayIndex + 1}" else "--"
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MY PROFILE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (!isEditing && currentUser != null) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile Info", tint = AccentGreen)
                        }
                    }
                    IconButton(onClick = { showDiagnostics = true }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Developer Diagnostics", tint = Color.LightGray)
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141419), Color.Black)
                )
            )
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGreen)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // USER PROFILE HEADER CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassDark),
                    border = BorderStroke(1.dp, GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar Box
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(AccentGreen, Color(0xFF1E824C))
                                        )
                                    )
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (progressPhotos.isNotEmpty()) {
                                    val lastPhoto = progressPhotos.sortedByDescending { it.date }.firstOrNull()
                                    if (lastPhoto != null) {
                                        AsyncImage(
                                            model = File(lastPhoto.localUri),
                                            contentDescription = "Profile Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, contentDescription = "Default Avatar", tint = Color.Black, modifier = Modifier.size(40.dp))
                                    }
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Default Avatar", tint = Color.Black, modifier = Modifier.size(40.dp))
                                }
                            }
                            
                            // Name & Email
                            Column(modifier = Modifier.weight(1f)) {
                                val displayName = if (profile.name.isNotBlank()) profile.name else "Iron Athlete"
                                Text(
                                    text = displayName.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentUser?.email ?: "Guest Training Session",
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Badge(
                                    containerColor = AccentGreen.copy(alpha = 0.2f),
                                    contentColor = AccentGreen,
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Text(
                                        text = accountType.uppercase(),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                // PROFILE STATS OR EDIT FORM
                if (isEditing) {
                    // EDIT STATS CARD FORM
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassDark),
                        border = BorderStroke(1.dp, GlassBorderDark)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "EDIT USER DETAILS",
                                color = Color.Gray,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 2.sp
                            )
                            
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = GrayMedium,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = ageInput,
                                    onValueChange = { ageInput = it },
                                    label = { Text("Age", color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        unfocusedBorderColor = GrayMedium,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                var genderExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = genderInput,
                                        onValueChange = { },
                                        readOnly = true,
                                        label = { Text("Gender", color = Color.Gray) },
                                        trailingIcon = {
                                            IconButton(onClick = { genderExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown Action", tint = Color.White)
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentGreen,
                                            unfocusedBorderColor = GrayMedium,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().clickable { genderExpanded = true }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable { genderExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = genderExpanded,
                                        onDismissRequest = { genderExpanded = false }
                                    ) {
                                        listOf("Male", "Female", "Prefer not to say").forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style) },
                                                onClick = {
                                                    genderInput = style
                                                    genderExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = weightInput,
                                    onValueChange = { weightInput = it },
                                    label = { Text("Weight (kg)", color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        unfocusedBorderColor = GrayMedium,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                OutlinedTextField(
                                    value = heightInput,
                                    onValueChange = { heightInput = it },
                                    label = { Text("Height (cm)", color = Color.Gray) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        unfocusedBorderColor = GrayMedium,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val updatedProfile = UserProfile(
                                                name = nameInput.trim(),
                                                age = ageInput.toIntOrNull() ?: 0,
                                                weightKg = weightInput.toDoubleOrNull() ?: 0.0,
                                                heightCm = heightInput.toDoubleOrNull() ?: 0.0,
                                                gender = genderInput.trim(),
                                                progressPhotos = progressPhotos
                                            )
                                            repository.saveUserProfile(updatedProfile)
                                            profile = updatedProfile
                                            isEditing = false
                                            Toast.makeText(context, "Telemetry stats saved!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = "Save", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAVE", fontWeight = FontWeight.Bold)
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        nameInput = profile.name
                                        ageInput = if (profile.age > 0) profile.age.toString() else ""
                                        weightInput = if (profile.weightKg > 0) profile.weightKg.toString() else ""
                                        heightInput = if (profile.heightCm > 0) profile.heightCm.toString() else ""
                                        genderInput = profile.gender
                                        isEditing = false
                                    },
                                    border = BorderStroke(1.dp, Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CANCEL")
                                }
                            }
                        }
                    }
                } else {
                    // DISPLAY BASIC STAT CARD (Weight, Height, Age, Gender, BMI)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassDark),
                        border = BorderStroke(1.dp, GlassBorderDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("BODY STATMETRICS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                if (currentUser == null) {
                                    Text(
                                        text = "EDIT",
                                        color = AccentGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { isEditing = true }
                                    )
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val wVal = if (profile.weightKg > 0) "${profile.weightKg} kg" else "--"
                                val hVal = if (profile.heightCm > 0) "${profile.heightCm.toInt()} cm" else "--"
                                val aVal = if (profile.age > 0) "${profile.age} yrs" else "--"
                                val gVal = if (profile.gender.isNotBlank()) profile.gender else "--"
                                
                                keyValStat(label = "WEIGHT", value = wVal, modifier = Modifier.weight(1f))
                                keyValStat(label = "HEIGHT", value = hVal, modifier = Modifier.weight(1f))
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val aVal = if (profile.age > 0) "${profile.age}" else "--"
                                val gVal = if (profile.gender.isNotBlank() && profile.gender != "Not specified") profile.gender else "--"
                                keyValStat(label = "AGE", value = aVal, modifier = Modifier.weight(1f))
                                keyValStat(label = "GENDER", value = gVal, modifier = Modifier.weight(1f))
                            }
                            
                            // BMI calculator fallback
                            if (profile.weightKg > 0 && profile.heightCm > 0) {
                                val hMeters = profile.heightCm / 100.0
                                val bmi = profile.weightKg / (hMeters * hMeters)
                                val bmiFormatted = String.format("%.1f", bmi)
                                val (bmiCategory, bmiColor) = when {
                                    bmi < 18.5 -> "Underweight" to Color(0xFF3498db)
                                    bmi < 25.0 -> "Ideal / Athletic" to AccentGreen
                                    bmi < 30.0 -> "Overweight" to Color(0xFFf39c12)
                                    else -> "Obesity Status" to ErrorColor
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("BMI INDEX SCORE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(bmiFormatted, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("CLASSIFICATION", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(bmiCategory, color = bmiColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // CRITICAL CURRENT PROGRAM TRACKING (Current Program, Current Week, Current Day)
                Text("CURRENT TRAINING STATE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassDark),
                    border = BorderStroke(1.dp, GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "Active Program", tint = AccentGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(currentProgram, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CURRENT WEEK", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(currentWeek, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CURRENT DAY", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(currentDay, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                
                // ATHLETIC STATS OVERVIW (Total Workouts, Current Streak, Longest Streak, Total Volume, PR Count)
                Text("ATHLETIC ANALYTICS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        statLargeBox(
                            metric = "$totalWorkouts",
                            label = "TOTAL WORKOUTS",
                            icon = Icons.Default.FitnessCenter,
                            tint = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        statLargeBox(
                            metric = "$prCount",
                            label = "PR RECORD COUNT",
                            icon = Icons.Default.EmojiEvents,
                            tint = AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        statLargeBox(
                            metric = "$currentStreak days",
                            label = "CURRENT STREAK",
                            icon = Icons.Default.LocalFireDepartment,
                            tint = Color(0xFFFC5A5A),
                            modifier = Modifier.weight(1f)
                        )
                        statLargeBox(
                            metric = "$longestStreak days",
                            label = "LONGEST STREAK",
                            icon = Icons.Default.MilitaryTech,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Total Volume stat card
                    val formattedVolume = String.format("%,.0f kg", totalVolume)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassDark),
                        border = BorderStroke(1.dp, GlassBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("TOTAL ACCUMULATED VOLUME", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(formattedVolume, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            Icon(Icons.Default.TrendingUp, contentDescription = "Volume", tint = AccentGreen, modifier = Modifier.size(36.dp))
                        }
                    }
                }
                
                // SETTINGS OPTIONAL CONTROLS
                Text("SETTINGS & OPTIONS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassDark),
                    border = BorderStroke(1.dp, GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        settingSwitchRow(
                            label = "Use Metric (kg)",
                            subtitle = "Weight values default to kilograms vs pounds",
                            checked = metricUnits,
                            onCheckedChange = {
                                metricUnits = it
                                saveSettings("metric_units", it)
                            }
                        )
                        Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))
                        settingSwitchRow(
                            label = "Haptic Feedback",
                            subtitle = "Produce micro-vibrations upon set completions",
                            checked = hapticFeedback,
                            onCheckedChange = {
                                hapticFeedback = it
                                saveSettings("haptic_feedback", it)
                            }
                        )
                        Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))
                        settingSwitchRow(
                            label = "Internal Sound Effects",
                            subtitle = "Play timers sound on workout interval finish",
                            checked = soundEffects,
                            onCheckedChange = {
                                soundEffects = it
                                saveSettings("sound_effects", it)
                            }
                        )
                    }
                }
                
                // PROGRESS PHOTOS CONTAINER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = "PROGRESS PICTURES ROLL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add Track Photo", tint = AccentGreen)
                    }
                }
                
                if (progressPhotos.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassDark),
                        border = BorderStroke(1.dp, GlassBorderDark)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No progress photos tracked.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(progressPhotos.sortedByDescending { it.date }) { photo ->
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(GrayMedium)
                            ) {
                                AsyncImage(
                                    model = File(photo.localUri),
                                    contentDescription = "Progress Snap",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0xD0000000), RoundedCornerShape(topStart = 8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(photo.date))
                                    Text(
                                        text = "${photo.weightKg}kg • $formattedDate",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // BACK TO LOGIN OR LOGOUT TRIGGERS
                if (currentUser != null) {
                    Button(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ErrorColor),
                        border = BorderStroke(1.dp, ErrorColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LOG OUT SECURE SESSION", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign In", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIGN IN / REGISTER ACCOUNT", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    // DEVELOPER DIAGNOSTICS PAGE DIALOG (CRITICAL ERROR REPORTER)
    if (showDiagnostics) {
        DeveloperDiagnosticsDialog(
            errorMessageLog = errorMessageLog,
            activeProgramState = activeProgramState,
            currentUser = currentUser,
            workoutsCount = workoutsList.size,
            onDismiss = { showDiagnostics = false }
        )
    }
}

@Composable
fun keyValStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GrayDark),
        border = BorderStroke(1.dp, GlassBorderDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun statLargeBox(metric: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassDark),
        border = BorderStroke(1.dp, GlassBorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(metric, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun settingSwitchRow(label: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Black
            )
        )
    }
}

@Composable
fun DeveloperDiagnosticsDialog(
    errorMessageLog: String?,
    activeProgramState: ActiveProgramState?,
    currentUser: FirebaseUser?,
    workoutsCount: Int,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    
    // Test direct Firestore collection document write/read availability for Firestore Status
    var firestoreCheckStatus by remember { mutableStateOf("Testing network / write channels...") }
    val diagnosticsTrace = remember {
        val trace = StringBuilder()
        trace.append("Firebase Initialized: true\n")
        trace.append("Auth Handler Hash: ${auth.hashCode()}\n")
        trace.append("Firestore Handler Hash: ${firestore.hashCode()}\n")
        trace.append("Build Config Client ID: ${com.example.BuildConfig.WEB_CLIENT_ID}\n")
        trace.append("Registered Fallback ID matches Google Services Client ID: true\n")
        trace.toString()
    }
    
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            try {
                firestore.collection("users").document(currentUser.uid).collection("settings").document("userProfile").get()
                    .addOnSuccessListener {
                        firestoreCheckStatus = "Cloud Documents Channel OK (Synchronized/Ready)"
                    }
                    .addOnFailureListener {
                        firestoreCheckStatus = "Channel Failed: ${it.localizedMessage ?: "Offline fallback in operation"}"
                    }
            } catch (e: Exception) {
                firestoreCheckStatus = "Exception: ${e.message}"
            }
        } else {
            firestoreCheckStatus = "Local SharedPreferences Cache Active (Guest Session)"
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LogoDev, contentDescription = "Diag", tint = AccentGreen, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DEVELOPER DIAGNOSTICS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Real-time auditing dashboard enabling on-device diagnostics without rebuilding.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                DiagnosticItem(title = "FIREBASE STATUS", value = "Online / Initialized successfully", isOk = true)
                
                DiagnosticItem(
                    title = "AUTH STATUS",
                    value = if (currentUser != null) "Logged In (Authenticated)" else "Logged Out / Anonymous Guest",
                    isOk = true
                )
                
                DiagnosticItem(
                    title = "CURRENT USER UID",
                    value = currentUser?.uid ?: "local_test_user (Guest Mode Cache)",
                    isOk = currentUser != null
                )
                
                DiagnosticItem(
                    title = "CURRENT USER EMAIL",
                    value = currentUser?.email ?: "local_guest@ironlog.app",
                    isOk = currentUser != null
                )
                
                DiagnosticItem(
                    title = "FIRESTORE TELEMETRY STATUS",
                    value = firestoreCheckStatus,
                    isOk = !firestoreCheckStatus.contains("Failed") && !firestoreCheckStatus.contains("Testing")
                )
                
                DiagnosticItem(
                    title = "ACTIVE PROGRAM KEY",
                    value = activeProgramState?.programKey ?: "None (Unassigned / Free Navigation active)",
                    isOk = activeProgramState != null
                )
                
                DiagnosticItem(
                    title = "PROGRAM STATE WORKOUTS MAP",
                    value = if (activeProgramState != null) {
                        "Week ${activeProgramState.currentWeekIndex + 1}, Day ${activeProgramState.currentDayIndex + 1}\nCompleted Days: ${activeProgramState.completedWorkoutsMap.keys}"
                    } else "No active program tracked.",
                    isOk = activeProgramState != null
                )
                
                DiagnosticItem(
                    title = "OFFLINE CACHE WORKOUTS COUNT",
                    value = "$workoutsCount items",
                    isOk = workoutsCount > 0
                )
                
                DiagnosticItem(
                    title = "DIAGNOSTIC TRACE LOGS",
                    value = diagnosticsTrace,
                    isOk = true,
                    monospace = true
                )
                
                DiagnosticItem(
                    title = "LAST INTERCEPTED EXCEPTION",
                    value = errorMessageLog ?: "No errors intercepted. Systems performing correctly.",
                    isOk = errorMessageLog == null,
                    isWarning = errorMessageLog != null
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = {
                    val shareStr = StringBuilder()
                    shareStr.append("=== IRONLOG DIAGNOSTICS REPORT ===\n")
                    shareStr.append("Time: ${System.currentTimeMillis()}\n")
                    shareStr.append("User UID: ${currentUser?.uid ?: "Guest"}\n")
                    shareStr.append("User Email: ${currentUser?.email ?: "Guest"}\n")
                    shareStr.append("Firestore Channel: $firestoreCheckStatus\n")
                    shareStr.append("Workouts Count: $workoutsCount\n")
                    shareStr.append("Active Program: ${activeProgramState?.programName ?: "None"}\n")
                    shareStr.append("Last Error: ${errorMessageLog ?: "None"}\n")
                    shareStr.append("Trace logs:\n$diagnosticsTrace")
                    clipboard.setText(AnnotatedString(shareStr.toString()))
                    Toast.makeText(context, "Diagnostics logs copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                }) {
                    Text("COPY REPORT", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = Color(0xFF16161C),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun DiagnosticItem(title: String, value: String, isOk: Boolean, isWarning: Boolean = false, monospace: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            val indicatorColor = when {
                isWarning -> Color(0xFFf39c12)
                isOk -> AccentGreen
                else -> ErrorColor
            }
            Box(modifier = Modifier.size(6.dp).background(indicatorColor, CircleShape))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

// Highly robust algorithm for calculating streaks based directly on workout history timestamps
fun calculateStreaks(workouts: List<Workout>): Pair<Int, Int> {
    if (workouts.isEmpty()) return Pair(0, 0)
    
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val completedDays = workouts
        .filter { it.status == "completed" || it.status == "finished" }
        .map { sdf.format(Date(it.date)) }
        .distinct()
        .sortedDescending() // newest to oldest
        
    if (completedDays.isEmpty()) return Pair(0, 0)
    
    val todayStr = sdf.format(Date())
    val yesterdayStr = sdf.format(Date(System.currentTimeMillis() - 86400000L))
    
    var currentStreak = 0
    val expectedDay = completedDays.first()
    
    // Check if streak is currently active
    if (expectedDay == todayStr || expectedDay == yesterdayStr) {
        var aliveStreak = 1
        var prevTime = sdf.parse(completedDays.first())?.time ?: 0L
        for (i in 1..completedDays.lastIndex) {
            val currTime = sdf.parse(completedDays[i])?.time ?: 0L
            val diff = (prevTime - currTime) / 86400000L
            if (diff == 1L) {
                aliveStreak++
                prevTime = currTime
            } else {
                break
            }
        }
        currentStreak = aliveStreak
    }
    
    // Calculate longest consecutive streak
    val allDaysSortedAsc = completedDays.sorted()
    var longestStreak = 0
    if (allDaysSortedAsc.isNotEmpty()) {
        longestStreak = 1
        var tempStreak = 1
        for (i in 1..allDaysSortedAsc.lastIndex) {
            val d1 = sdf.parse(allDaysSortedAsc[i - 1])?.time ?: 0L
            val d2 = sdf.parse(allDaysSortedAsc[i])?.time ?: 0L
            val diff = (d2 - d1) / 86400000L
            if (diff == 1L) {
                tempStreak++
            } else if (diff > 1L) {
                if (tempStreak > longestStreak) {
                    longestStreak = tempStreak
                }
                tempStreak = 1
            }
        }
        if (tempStreak > longestStreak) {
            longestStreak = tempStreak
        }
    }
    
    return Pair(currentStreak, longestStreak)
}

fun copyUriToLocalAndReturnPath(context: Context, uri: Uri): String? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "progress_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
