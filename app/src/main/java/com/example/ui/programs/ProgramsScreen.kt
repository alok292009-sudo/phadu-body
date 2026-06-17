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
        val json = context.assets.open("jeff_nippard.json").bufferedReader().use { it.readText() }
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
                    containerColor = com.example.ui.theme.GlassDark,
                    title = { Text("Start Program", color = Color.White) },
                    text = { Text("Do you want to set this program as your main routine?", color = com.example.ui.theme.GrayMedium) },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmDialog = false
                            if (isLoading) return@TextButton
                            isLoading = true
                            coroutineScope.launch {
                                // Generate templates for the first week to keep it clean
                                val week1 = program!!.weeks["week1"]
                                week1?.days?.filter { !it.isRestDay }?.forEach { day ->
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
                                }
                                isLoading = false
                                onProgramStarted()
                            }
                        }) {
                            Text("Yes", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
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
