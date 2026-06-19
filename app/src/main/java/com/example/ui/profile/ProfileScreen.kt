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
        containerColor = BgColor
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
                    Text(if (profile.name.isNotBlank()) profile.name else "Athlete", style = IronTypography.Title2)
                    Text("${workoutsList.size} workouts completed", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x48))

            // Actions list
            Text("UTILITIES", style = IronTypography.Caption.copy(color = TextTertiaryColor))
            Spacer(modifier = Modifier.height(IronSpacing.x12))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
            ) {
                // Plate Calculator menu item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClick { onNavigateToPlateCalc() }
                        .padding(IronSpacing.x20),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Plate Calculator", style = IronTypography.Body)
                    Text("→", style = IronTypography.Body.copy(color = TextSecondaryColor))
                }
                
                HorizontalDivider(color = BorderColor)

                // Sign Out
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClick { onSignOutClick() }
                        .padding(IronSpacing.x20),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sign Out", style = IronTypography.Body.copy(color = DestructiveColor))
                }
            }
        }
    }
}
