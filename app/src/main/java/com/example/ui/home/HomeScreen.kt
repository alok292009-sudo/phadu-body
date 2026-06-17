package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Template
import com.example.model.Workout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset

@Composable
fun HomeScreen(
    repository: IronLogRepository,
    onStartWorkout: (templateId: String?) -> Unit,
    onResumeWorkout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var recentWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }

    LaunchedEffect(Unit) {
        repository.seedInitialExercises()
        launch { repository.getTemplates().collect { templates = it } }
        launch {
            repository.getWorkouts().collect { workouts ->
                recentWorkout = workouts.filter { it.status == "completed" }.maxByOrNull { it.date }
            }
        }
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1C22), Color.Black),
                    center = Offset(500f, -200f),
                    radius = 2500f
                )
            )
            .padding(16.dp)
    ) {
        Text(
            text = "IRONLOG",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        Button(
            onClick = {
                if (activeWorkout != null) onResumeWorkout() else onStartWorkout(null)
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (activeWorkout != null) "RESUME WORKOUT" else "START FREE WORKOUT",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        if (templates.isNotEmpty()) {
            Text(
                text = "MAIN ROUTINE",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                items(templates) { template ->
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .height(100.dp)
                            .clickable {
                                if (activeWorkout != null) onResumeWorkout() else onStartWorkout(template.id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = template.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (recentWorkout != null) {
            Text(
                text = "RECENT WORKOUT",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(recentWorkout!!.date))
                    Text(
                        text = (recentWorkout!!.templateName ?: "AD-HOC WORKOUT").uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = dateStr.uppercase(), color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${recentWorkout!!.totalVolume.toInt()}", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                            Text("VOLUME (KG)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), letterSpacing = 1.sp)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("${recentWorkout!!.durationMinutes}", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                            Text("MINUTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}
