package com.example.ui.programs

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(repository: IronLogRepository, onProgramStarted: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var program by remember { mutableStateOf<Program?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val json = context.assets.open("bodybuilding_program.json").bufferedReader().use { it.readText() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Program::class.java)
        program = adapter.fromJson(json)
    }

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
                title = { Text("PROGRAMS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (program == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
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
                            "Do you want to set '${program?.programName ?: "this program"}' as your main routine?\n\nThis will add the first week's workouts to your quick start templates.",
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
                                        repository.getTemplates().firstOrNull()?.forEach { 
                                           repository.deleteTemplate(it.id)
                                        }

                                        val firstWeek = program!!.weeks.values.firstOrNull()
                                        var templatesAdded = 0
                                        var totalWorkoutsFirstWeek = 0
                                        firstWeek?.days?.filter { !it.isRestDay }?.forEach { day ->
                                            val tExercises = day.exercises.mapIndexed { index, ex ->
                                                TemplateExercise(
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
                                            totalWorkoutsFirstWeek++
                                        }

                                        repository.saveActiveProgramState(
                                            ActiveProgramState(
                                                programKey = "bodybuilding_program.json",
                                                currentWeekIndex = 0,
                                                workoutsCompletedThisWeek = 0,
                                                totalWorkoutsThisWeek = totalWorkoutsFirstWeek,
                                                isWeekCompletedMessageShown = false
                                            )
                                        )

                                        android.widget.Toast.makeText(context, "Added $templatesAdded routines", android.widget.Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        onProgramStarted()
                                    } catch (e: Exception) {
                                        if (e !is kotlin.coroutines.cancellation.CancellationException && e !is kotlinx.coroutines.CancellationException) {
                                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
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
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(program!!.programName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                            Text("By ${program!!.author}", color = com.example.ui.theme.GrayMedium, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    showConfirmDialog = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("START THIS PROGRAM", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        "WEEKS",
                        color = com.example.ui.theme.GrayMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                items(program!!.weeks.entries.toList()) { (weekKey, week) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(weekKey.replaceFirstChar { it.titlecase() }, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
                            Text(week.block, color = com.example.ui.theme.GrayMedium, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            week.days.forEach { day ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(if (day.isRestDay) "Rest" else day.dayName, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
