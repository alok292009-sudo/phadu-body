package com.example.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Exercise
import com.example.model.PersonalRecord
import com.example.model.Workout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(repository: IronLogRepository) {
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var prs by remember { mutableStateOf<Map<String, PersonalRecord>>(emptyMap()) }
    var history by remember { mutableStateOf<List<Workout>>(emptyList()) }

    LaunchedEffect(Unit) {
        launch {
            repository.getExercises().combine(repository.getPersonalRecords()) { ex, recs ->
                exercises = ex
                prs = recs.associateBy { it.exerciseId }
            }.collect {}
        }
        launch {
            repository.getWorkouts().collect {
                history = it
            }
        }
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
                title = { Text("PROGRESS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                if (history.isNotEmpty()) {
                    VolumeDashboard(history)
                }
            }

            val list = exercises.filter { prs.containsKey(it.id) }
            items(list) { ex ->
                val pr = prs[ex.id]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                    border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(ex.name.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${pr?.bestWeight?.value ?: 0.0} ${ex.unit} x ${pr?.bestWeight?.reps ?: 0}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                Text("BEST WEIGHT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium, letterSpacing = 1.sp)
                            }
                            Column {
                                Text("${pr?.bestVolume?.value ?: 0.0} ${ex.unit}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                Text("BEST VOLUME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium, letterSpacing = 1.sp)
                            }
                            Column {
                                Text("${pr?.bestEstimated1RM?.value?.toInt() ?: 0} ${ex.unit}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                Text("EST 1RM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun VolumeDashboard(workouts: List<Workout>) {
    // 30 days progression
    val now = System.currentTimeMillis()
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
    val cutoff = now - thirtyDaysMs

    val recentWorkouts = workouts.filter { it.date >= cutoff }.sortedBy { it.date }
    if (recentWorkouts.isEmpty()) return

    // Group by Date String for unique days
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    val grouped = recentWorkouts.groupBy { sdf.format(Date(it.date)) }
    val dataPoints = grouped.map { (date, dailyWorkouts) ->
        date to dailyWorkouts.sumOf { it.totalVolume }
    }

    if (dataPoints.isEmpty()) return

    val maxVolume = dataPoints.maxOf { it.second }.toFloat()
    val minVolume = 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Volume Progression (30 Days)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Total volume lifted across all exercises", color = com.example.ui.theme.GrayMedium, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val paddingX = 40f
                    val paddingY = 40f
                    
                    val chartWidth = width - paddingX * 2
                    val chartHeight = height - paddingY * 2
                    
                    // Draw grid lines
                    val steps = 4
                    for (i in 0..steps) {
                        val y = paddingY + (chartHeight / steps) * i
                        drawLine(
                            color = Color.DarkGray.copy(alpha = 0.3f),
                            start = Offset(paddingX, y),
                            end = Offset(width - paddingX, y),
                            strokeWidth = 2f
                        )
                        
                        // Y axis labels
                        val labelValue = maxVolume - ((maxVolume / steps) * i)
                        val text = "${labelValue.toInt()}kg"
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 24f
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            0f,
                            y + 8f,
                            paint
                        )
                    }

                    if (dataPoints.size > 1) {
                        val pointXSpace = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)
                        val path = Path()
                        val fillPath = Path()
                        
                        var firstPointX = paddingX
                        var firstPointY = paddingY + chartHeight
                        
                        dataPoints.forEachIndexed { index, pair ->
                            val x = paddingX + (index * pointXSpace)
                            // Normalized y: 0 is at bottom (paddingY + chartHeight), max is at top (paddingY)
                            val normalizedVol = if(maxVolume > 0) pair.second.toFloat() / maxVolume else 0f
                            val y = paddingY + chartHeight - (normalizedVol * chartHeight)
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, paddingY + chartHeight)
                                fillPath.lineTo(x, y)
                                firstPointX = x
                                firstPointY = y
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            
                            // Draw dots
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = Offset(x, y)
                            )
                            
                            // X axis labels (first, middle, last)
                            if (index == 0 || index == dataPoints.size - 1 || index == dataPoints.size / 2) {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    pair.first,
                                    x,
                                    height - 5f,
                                    paint
                                )
                            }
                        }
                        
                        // Fill path
                        fillPath.lineTo(paddingX + chartWidth, paddingY + chartHeight)
                        fillPath.close()
                        
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                startY = paddingY,
                                endY = paddingY + chartHeight
                            )
                        )
                        
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(width = 6f)
                        )
                    } else if (dataPoints.size == 1) {
                         // Just one single workout dot
                         val normalizedVol = if(maxVolume > 0) dataPoints[0].second.toFloat() / maxVolume else 0f
                         val y = paddingY + chartHeight - (normalizedVol * chartHeight)
                         val x = width / 2
                         drawCircle(
                                color = Color.White,
                                radius = 8f,
                                center = Offset(x, y)
                         )
                    }
                }
            }
        }
    }
}
