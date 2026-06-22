package com.example.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Program
import com.example.ui.theme.BgColor
import com.example.ui.theme.IronTypography
import com.example.ui.theme.TextPrimaryColor
import com.example.ui.theme.TextSecondaryColor
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(repository: IronLogRepository, onBack: () -> Unit) {
    var program by remember { mutableStateOf<Program?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        program = repository.getActiveProgram().firstOrNull()
        isLoading = false
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("JSON DIAGNOSTICS", style = IronTypography.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = TextPrimaryColor)
            }
        } else if (program == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("NO PROGRAM LOADED", color = Color.Red)
            }
        } else {
            val p = program!!
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                item { DiagnosticHeader("PIPELINE AUDIT") }
                item { DiagnosticStat("Program Name", p.programName) }
                item { DiagnosticStat("Weeks Found", p.weeks.size.toString()) }
                
                val allDays = p.weeks.values.flatMap { it.days }
                item { DiagnosticStat("Total Days Found", allDays.size.toString()) }
                
                val nonRestDays = allDays.filter { !it.isRestDay }
                item { DiagnosticStat("Training Days", nonRestDays.size.toString()) }
                
                val allExercises = nonRestDays.flatMap { it.exercises }
                item { DiagnosticStat("Exercises Found", allExercises.size.toString()) }
                
                val subsCount = allExercises.count { it.alternatives?.substitution1 != null || it.alternatives?.substitution2 != null }
                item { DiagnosticStat("Exercises with Subs", subsCount.toString()) }
                
                val videoCount = allExercises.count { !it.demoLink.isNullOrBlank() }
                item { DiagnosticStat("Video Links Found", videoCount.toString()) }
                
                val warmupCount = allExercises.count { it.prescription?.warmup != null }
                item { DiagnosticStat("Warmup Ramps Found", warmupCount.toString()) }
                
                val techCount = allExercises.count { it.technique != null }
                item { DiagnosticStat("Technique Objects Found", techCount.toString()) }

                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { DiagnosticHeader("DAY DRILLDOWN") }
                
                items(allDays) { day ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("W${day.slot / 7 + 1} - ${day.weekday} (${day.displayName})", style = IronTypography.Headline)
                            if (day.isRestDay) {
                                Text("REST DAY", color = Color.Gray, style = IronTypography.Caption)
                            } else {
                                Text("${day.exercises.size} EXERCISES", color = TextPrimaryColor, style = IronTypography.Caption)
                                day.exercises.forEach { ex ->
                                    Text("• ${ex.name} [Sub:${ex.alternatives != null}, Vid:${!ex.demoLink.isNullOrBlank()}]", fontSize = 10.sp, color = TextSecondaryColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticHeader(text: String) {
    Text(text, style = IronTypography.Caption, color = Color.Yellow, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun DiagnosticStat(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondaryColor, style = IronTypography.Body)
        Text(value, color = TextPrimaryColor, style = IronTypography.Headline)
    }
}
