package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Template
import com.example.model.Workout
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.model.ActiveProgramState
import com.example.model.Program
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import com.example.ui.theme.bounceClick

@Composable
fun HomeScreen(
    repository: IronLogRepository,
    onStartWorkout: (templateId: String?) -> Unit,
    onResumeWorkout: () -> Unit,
    onProfileClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var recentWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    var activeProgramState by remember { mutableStateOf<ActiveProgramState?>(null) }

    LaunchedEffect(Unit) {
        repository.seedInitialExercises()
        launch { repository.getTemplates().collect { templates = it } }
        launch {
            repository.getWorkouts().collect { workouts ->
                recentWorkout = workouts.filter { it.status == "completed" }.maxByOrNull { it.date }
            }
        }
        launch { repository.getActiveWorkout().collect { activeWorkout = it } }
        launch { repository.getActiveProgramState().collect { activeProgramState = it } }
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (activeProgramState != null && 
            activeProgramState!!.workoutsCompletedThisWeek >= activeProgramState!!.totalWorkoutsThisWeek && 
            !activeProgramState!!.isWeekCompletedMessageShown &&
            activeProgramState!!.totalWorkoutsThisWeek > 0
        ) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = com.example.ui.theme.GrayDark,
                titleContentColor = Color.White,
                textContentColor = com.example.ui.theme.GrayMedium,
                shape = RoundedCornerShape(24.dp),
                title = { Text("🎉 WEEK ${activeProgramState!!.currentWeekIndex + 1} COMPLETED!", fontWeight = FontWeight.Black, fontSize = 20.sp) },
                text = { Text("Great job crushing all your workouts this week! Ready to load the next week's routine?", fontSize = 16.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Mark message as shown immediately
                                repository.saveActiveProgramState(activeProgramState!!.copy(isWeekCompletedMessageShown = true))
                                
                                try {
                                    val json = context.assets.open(activeProgramState!!.programKey).bufferedReader().use { it.readText() }
                                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                                    val program = moshi.adapter(Program::class.java).fromJson(json)
                                    val nextWeekIndex = activeProgramState!!.currentWeekIndex + 1
                                    
                                    if (program != null && nextWeekIndex < program.weeks.size) {
                                        // Clear current templates
                                        repository.clearAllTemplates()
                                        
                                        val nextWeek = program.weeks.values.toList()[nextWeekIndex]
                                        var templatesAdded = 0
                                        var totalWorkoutsNextWeek = 0
                                        nextWeek.days.filter { !it.isRestDay }.forEach { day ->
                                            val tExercises = day.exercises.mapIndexed { index, ex ->
                                                com.example.model.TemplateExercise(
                                                    exerciseId = UUID.randomUUID().toString(),
                                                    exerciseName = ex.name,
                                                    targetSets = ex.workingSets?.toIntOrNull() ?: 3,
                                                    targetReps = ex.reps?.split("-")?.last()?.toIntOrNull() ?: 10,
                                                    order = index,
                                                    videoUrl = ex.demoLink
                                                )
                                            }
                                            val template = Template(
                                                id = UUID.randomUUID().toString(),
                                                name = day.dayName,
                                                exercises = tExercises
                                            )
                                            repository.saveTemplate(template)
                                            templatesAdded++
                                            totalWorkoutsNextWeek++
                                        }
                                        
                                        repository.saveActiveProgramState(
                                            activeProgramState!!.copy(
                                                currentWeekIndex = nextWeekIndex,
                                                workoutsCompletedThisWeek = 0,
                                                totalWorkoutsThisWeek = totalWorkoutsNextWeek,
                                                isWeekCompletedMessageShown = false
                                            )
                                        )
                                        Toast.makeText(context, "Loaded Week ${nextWeekIndex + 1} routines!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Program Complete!", Toast.LENGTH_LONG).show()
                                        repository.saveActiveProgramState(null) // clear active program
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error loading next week", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("START NEXT WEEK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GYM KRTA H JI",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(com.example.ui.theme.GlassDark, shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .bounceClick {
                    if (activeWorkout != null) onResumeWorkout() else onStartWorkout(null)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (activeWorkout != null) "RESUME WORKOUT" else "START FREE WORKOUT",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (templates.isNotEmpty()) {
            val titleText = if (activeProgramState != null) "WEEK ${activeProgramState!!.currentWeekIndex + 1} ROUTINE" else "MAIN ROUTINE"
            Text(
                text = titleText,
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                templates.forEachIndexed { index, template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick {
                                if (activeWorkout != null) onResumeWorkout() else onStartWorkout(template.id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark), // Solid background for text legibility
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                                    .background(com.example.ui.theme.GrayDark, shape = RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "D${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = template.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${template.exercises.size} Exercises",
                                    color = com.example.ui.theme.GrayMedium,
                                    fontSize = 12.sp
                                )
                            }
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
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GrayDark), // Solid background for clarity & numbers
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
