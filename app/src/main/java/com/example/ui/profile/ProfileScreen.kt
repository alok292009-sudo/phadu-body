package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.IronLogRepository
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.net.Uri
import com.example.model.UserProfile
import com.example.model.ProgressPhoto
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    var profile by remember { mutableStateOf(UserProfile()) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    
    // Form Input states (only active or updated in edit mode)
    var nameInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var genderInput by remember { mutableStateOf("") }
    var progressPhotos by remember { mutableStateOf(listOf<ProgressPhoto>()) }
    
    // Load profile from repository
    LaunchedEffect(currentUser) {
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
            isLoading = false
        }
    }
    
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
                        
                        // Automatically update profile to include new photo
                        val updatedProfile = profile.copy(
                            progressPhotos = updatedPhotos
                        )
                        repository.saveUserProfile(updatedProfile)
                        Toast.makeText(context, "Progress photo added! 📸", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MY PROFILE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (!isEditing && currentUser != null) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = com.example.ui.theme.AccentGreen)
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1C22), Color.Black),
                    center = Offset(500f, -200f),
                    radius = 2500f
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
                CircularProgressIndicator(color = com.example.ui.theme.AccentGreen)
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
                // User info header glass card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                    border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            com.example.ui.theme.AccentGreen,
                                            Color(0xFF2ecc71)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Avatar",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Column {
                            val displayName = if (profile.name.isNotBlank()) profile.name else "Iron Gymner"
                            Text(
                                text = displayName.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentUser != null) currentUser.email ?: "Logged In User" else "Guest Mode (Offline)",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                if (isEditing) {
                    // Editing Mode form fields view
                    Text(
                        text = "EDIT STATS",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                    
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.example.ui.theme.AccentGreen,
                            unfocusedBorderColor = com.example.ui.theme.GrayMedium,
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
                                focusedBorderColor = com.example.ui.theme.AccentGreen,
                                unfocusedBorderColor = com.example.ui.theme.GrayMedium,
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
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Gender",
                                            tint = Color.White
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.AccentGreen,
                                    unfocusedBorderColor = com.example.ui.theme.GrayMedium,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { genderExpanded = true }
                            )
                            
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { genderExpanded = true }
                            )
                            
                            DropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false },
                                modifier = Modifier.background(com.example.ui.theme.GlassDark)
                            ) {
                                listOf("Male", "Female", "Prefer not to say").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White) },
                                        onClick = {
                                            genderInput = option
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
                                focusedBorderColor = com.example.ui.theme.AccentGreen,
                                unfocusedBorderColor = com.example.ui.theme.GrayMedium,
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
                                focusedBorderColor = com.example.ui.theme.AccentGreen,
                                unfocusedBorderColor = com.example.ui.theme.GrayMedium,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                                    Toast.makeText(context, "Profile details saved successfully! 💾", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Done")
                                Text("SAVE", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        OutlinedButton(
                            onClick = {
                                // Restore original states
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                                Text("CANCEL", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // View stat metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stat Metric Card 1: WEIGHT
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("WEIGHT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val weightText = if (profile.weightKg > 0) "${profile.weightKg} kg" else "--"
                                Text(weightText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        
                        // Stat Metric Card 2: HEIGHT
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("HEIGHT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val heightText = if (profile.heightCm > 0) "${profile.heightCm.toInt()} cm" else "--"
                                Text(heightText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stat Metric Card 3: AGE
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("AGE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val ageText = if (profile.age > 0) "${profile.age}" else "--"
                                Text(ageText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        
                        // Stat Metric Card 4: GENDER
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("GENDER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                val genderText = if (profile.gender.isNotBlank()) profile.gender else "--"
                                Text(genderText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    
                    // BMI Visual Card
                    val w = profile.weightKg
                    val h = profile.heightCm
                    if (w > 0 && h > 0) {
                        val hm = h / 100.0
                        val bmi = w / (hm * hm)
                        val bmiText = String.format("%.1f", bmi)
                        
                        val (category, color, rangeDesc) = when {
                            bmi < 18.5 -> Triple("Underweight", Color(0xFF3498db), "Below 18.5")
                            bmi < 25.0 -> Triple("Normal Weight", com.example.ui.theme.AccentGreen, "18.5 to 24.9")
                            bmi < 30.0 -> Triple("Overweight", Color(0xFFf39c12), "25 to 29.9")
                            else -> Triple("Obese", com.example.ui.theme.ErrorColor, "30.0 or higher")
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("BODY MASS INDEX (BMI)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(bmiText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(category, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("IDEAL RANGE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("18.5–24.9", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(rangeDesc, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    if (currentUser == null) {
                        Button(
                            onClick = { isEditing = true },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.GlassDark, contentColor = Color.White),
                            border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                                Text("SETUP MY STATS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // PROGRESS PHOTOS Row Segment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        text = "PROGRESS PHOTOS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Photo", tint = com.example.ui.theme.AccentGreen)
                    }
                }
                
                if (progressPhotos.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", tint = Color.Gray, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No progress photos added yet.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(progressPhotos.sortedByDescending { it.date }) { photo ->
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(com.example.ui.theme.GrayMedium)
                            ) {
                                AsyncImage(
                                    model = File(photo.localUri),
                                    contentDescription = "Progress Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0x99000000), RoundedCornerShape(topStart = 8.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    val formattedDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(photo.date))
                                    Text(
                                        text = "${photo.weightKg}kg • $formattedDate",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Sign Out or Log In button depending on status
                if (currentUser != null) {
                    Button(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = com.example.ui.theme.ErrorColor),
                        border = BorderStroke(1.dp, com.example.ui.theme.ErrorColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIGN OUT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                } else {
                    Button(
                        onClick = onLoginClick,
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.AccentGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Log In", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LOG IN / REGISTER ACCOUNT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
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
