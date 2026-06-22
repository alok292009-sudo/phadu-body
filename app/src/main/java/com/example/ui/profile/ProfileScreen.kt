package com.example.ui.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.example.data.IronLogRepository
import com.example.model.UserProfile
import com.example.model.Workout
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: IronLogRepository,
    onSignOutClick: () -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToPlateCalc: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var profile by remember { mutableStateOf(UserProfile()) }
    var workoutsList by remember { mutableStateOf(listOf<Workout>()) }

    LaunchedEffect(Unit) {
        launch { repository.getUserProfile().collect { if (it != null) profile = it } }
        launch { repository.getWorkouts().collect { workoutsList = it.filter { w -> w.status == "completed" } } }
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(IronSpacing.x16)
        ) {
            Spacer(modifier = Modifier.height(IronSpacing.x12))
            
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(IronCorner.RadiusFull)).background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(IronSpacing.x16))
                Column {
                    AutoResizingText(
                        text = if (profile.name.isNotBlank()) profile.name else "Athlete", 
                        style = IronTypography.Subheading,
                        maxLines = 1
                    )
                    Text("${workoutsList.size} workouts completed", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x48))

            var showWeightPicker by remember { mutableStateOf(false) }

            Text("MEASUREMENTS", style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 1.5.sp))
            Spacer(modifier = Modifier.height(IronSpacing.x12))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                    .padding(IronSpacing.x16)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bodyweight", style = IronTypography.Body.copy(color = Color.White))
                    com.example.ui.components.StepperChip(
                        value = profile.weightKg.takeIf { it > 0 } ?: 70.0,
                        unit = "KG",
                        onValueChange = { newVal ->
                            val updatedProfile = profile.copy(weightKg = newVal)
                            coroutineScope.launch { repository.saveUserProfile(updatedProfile) }
                        },
                        onClick = { showWeightPicker = true },
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            if (showWeightPicker) {
                com.example.ui.components.ScrollPickerSheet(
                    initialValue = profile.weightKg.takeIf { it > 0 } ?: 70.0,
                    type = "WEIGHT",
                    onDismiss = { showWeightPicker = false },
                    onDone = { newVal ->
                        val updatedProfile = profile.copy(weightKg = newVal)
                        coroutineScope.launch { repository.saveUserProfile(updatedProfile) }
                        showWeightPicker = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(IronSpacing.x48))

            // Actions list
            Text("UTILITIES", style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 1.5.sp))
            Spacer(modifier = Modifier.height(IronSpacing.x12))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
            ) {
                // Sign Out
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClick { onSignOutClick() }
                        .padding(IronSpacing.x20),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sign Out", style = IronTypography.Body.copy(color = Color.White))
                }
                
                Divider(color = Color.White.copy(alpha = 0.05f))

                // Reset Program
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClick { 
                            coroutineScope.launch {
                                try {
                                    repository.saveActiveProgramState(null)
                                    Toast.makeText(context, "Program state reset", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("Profile", "Error resetting program", e)
                                    Toast.makeText(context, "Failed to reset: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(IronSpacing.x20),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reset Program State", style = IronTypography.Body.copy(color = DestructiveColor))
                }
            }
        }
    }
}
