package com.example.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.input.KeyboardType
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
    var activeTab by remember { mutableStateOf(0) } // 0: Charts & PRs, 1: Plate Calculator

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
                title = { Text("ANALYTICS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Elegant Pill Segmented Tab Bar for subtabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(com.example.ui.theme.GlassDark, RoundedCornerShape(16.dp))
                    .border(1.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("PROGRESS & PRS", "PLATE CALCULATOR").forEachIndexed { index, title ->
                    val selected = activeTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) com.example.ui.theme.AccentGreen else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { activeTab = index }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            if (activeTab == 0) {
                ProgressionAndPrsContent(
                    history = history,
                    exercises = exercises,
                    prs = prs
                )
            } else {
                PlateCalculatorContent()
            }
        }
    }
}

@Composable
fun ProgressionAndPrsContent(
    history: List<Workout>,
    exercises: List<Exercise>,
    prs: Map<String, PersonalRecord>
) {
    var expandedExerciseId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            if (history.isNotEmpty()) {
                VolumeDashboard(history)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(com.example.ui.theme.GlassDark, RoundedCornerShape(16.dp))
                        .border(1.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No completed workouts yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Finish a workout session from programs or home to view charts!", color = com.example.ui.theme.GrayMedium, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Text(
                text = "PERSONAL RECORDS",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = com.example.ui.theme.GrayMedium,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        val list = exercises.filter { prs.containsKey(it.id) }
        
        if (list.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(com.example.ui.theme.GlassDark, RoundedCornerShape(16.dp))
                        .border(1.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Personal Records set yet 🏋️", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Perform high-weight exercises to track records here.", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(list) { ex ->
                val pr = prs[ex.id]
                val isExpanded = expandedExerciseId == ex.id
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            expandedExerciseId = if (isExpanded) null else ex.id
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                    border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(ex.name.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
                                Text(
                                    text = if (isExpanded) "Click to collapse chart" else "Click to visualize progression",
                                    color = com.example.ui.theme.AccentGreen.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Visual indicator
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(com.example.ui.theme.GlassDark, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isExpanded) "−" else "+",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
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

                        // Chart section dynamically fetched and rendered on expand
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                val chartPoints = remember(ex.id, history) {
                                    getExerciseProgression(ex.id, history)
                                }
                                ExerciseProgressionChart(
                                    dataPoints = chartPoints,
                                    unit = ex.unit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getExerciseProgression(exerciseId: String, workouts: List<Workout>): List<Pair<Long, Double>> {
    return workouts
        .filter { it.status == "completed" }
        .mapNotNull { workout ->
            val logEx = workout.loggedExercises.find { it.exerciseId == exerciseId }
            if (logEx != null) {
                val maxWeight = logEx.sets
                    .filter { !it.isWarmup && it.reps > 0 }
                    .maxOfOrNull { it.weight }
                if (maxWeight != null && maxWeight > 0.0) {
                    workout.date to maxWeight
                } else null
            } else null
        }
        .sortedBy { it.first }
}

@Composable
fun ExerciseProgressionChart(
    dataPoints: List<Pair<Long, Double>>,
    unit: String
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(com.example.ui.theme.GlassDark, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No weight records found in history.", color = com.example.ui.theme.GrayMedium, fontSize = 13.sp)
                Text("Log sets with weights for this exercise to populate graph! 📈", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }

    val maxWeight = dataPoints.maxOf { it.second }.toFloat()
    val minWeight = dataPoints.minOf { it.second }.toFloat()
    
    val yMax = if (maxWeight == minWeight) maxWeight + 10f else maxWeight + (maxWeight - minWeight) * 0.15f
    val yMin = if (maxWeight == minWeight) (maxWeight - 10f).coerceAtLeast(0f) else (minWeight - (maxWeight - minWeight) * 0.15f).coerceAtLeast(0f)
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weight Lifted Progress ($unit)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val paddingX = 70f
                    val paddingY = 30f
                    
                    val chartWidth = width - paddingX * 2
                    val chartHeight = height - paddingY * 2
                    
                    // Gridlines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = paddingY + (chartHeight / gridSteps) * i
                        drawLine(
                            color = Color.DarkGray.copy(alpha = 0.15f),
                            start = Offset(paddingX, y),
                            end = Offset(width - paddingX, y),
                            strokeWidth = 1.5f
                        )
                        
                        val yVal = yMax - ((yMax - yMin) / gridSteps) * i
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 22f
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "${yVal.toInt()} $unit",
                            5f,
                            y + 8f,
                            paint
                        )
                    }

                    if (dataPoints.size > 1) {
                        val pointXSpace = chartWidth / (dataPoints.size - 1)
                        val path = Path()
                        val fillPath = Path()
                        
                        dataPoints.forEachIndexed { index, pair ->
                            val x = paddingX + (index * pointXSpace)
                            val normalizedY = if (yMax != yMin) (pair.second.toFloat() - yMin) / (yMax - yMin) else 0.5f
                            val y = paddingY + chartHeight - (normalizedY * chartHeight)
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, paddingY + chartHeight)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            
                            // Highlight individual node
                            drawCircle(
                                color = com.example.ui.theme.AccentGreen,
                                radius = 7f,
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f,
                                center = Offset(x, y)
                            )
                            
                            // Highlight weight text above dot
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                "${pair.second.toInt()}",
                                x,
                                y - 16f,
                                textPaint
                            )

                            // Print date under chart
                            if (index == 0 || index == dataPoints.size - 1 || (dataPoints.size > 2 && index == dataPoints.size / 2)) {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 20f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    sdf.format(Date(pair.first)),
                                    x,
                                    height - 5f,
                                    paint
                                )
                            }
                        }
                        
                        fillPath.lineTo(paddingX + chartWidth, paddingY + chartHeight)
                        fillPath.close()
                        
                        // Draw Area Shade Gradient
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    com.example.ui.theme.AccentGreen.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                startY = paddingY,
                                endY = paddingY + chartHeight
                            )
                        )
                        
                        // Draw elegant line stroke
                        drawPath(
                            path = path,
                            color = com.example.ui.theme.AccentGreen,
                            style = Stroke(width = 4f)
                        )
                    } else if (dataPoints.size == 1) {
                        val x = width / 2f
                        val y = height / 2f
                        drawCircle(
                            color = com.example.ui.theme.AccentGreen,
                            radius = 8f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(x, y)
                        )
                        
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            sdf.format(Date(dataPoints[0].first)),
                            x,
                            height - 5f,
                            paint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeDashboard(workouts: List<Workout>) {
    val now = System.currentTimeMillis()
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
    val cutoff = now - thirtyDaysMs

    val recentWorkouts = workouts.filter { it.date >= cutoff }.sortedBy { it.date }
    if (recentWorkouts.isEmpty()) return

    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    val grouped = recentWorkouts.groupBy { sdf.format(Date(it.date)) }
    val dataPoints = grouped.map { (date, dailyWorkouts) ->
        date to dailyWorkouts.sumOf { it.totalVolume }
    }.sortedBy { it.first }

    if (dataPoints.isEmpty()) return

    val maxVolume = dataPoints.maxOf { it.second }.toFloat()

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
                    
                    val paddingX = 70f
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
                        val pointXSpace = chartWidth / (dataPoints.size - 1)
                        val path = Path()
                        val fillPath = Path()
                        
                        dataPoints.forEachIndexed { index, pair ->
                            val x = paddingX + (index * pointXSpace)
                            val normalizedVol = if (maxVolume > 0) pair.second.toFloat() / maxVolume else 0f
                            val y = paddingY + chartHeight - (normalizedVol * chartHeight)
                            
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, paddingY + chartHeight)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = Offset(x, y)
                            )
                            
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
                         val normalizedVol = if (maxVolume > 0) dataPoints[0].second.toFloat() / maxVolume else 0f
                         val y = paddingY + chartHeight - (normalizedVol * chartHeight)
                         val x = width / 2f
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorContent() {
    var targetWeightInput by remember { mutableStateOf("100") }
    var isKg by remember { mutableStateOf(true) }
    var barbellWeight by remember { mutableStateOf(20.0) }

    // Multi-select for plate inventory (KG and LBS inventory states)
    var availableKgPlates by remember {
        mutableStateOf(mapOf(
            25.0 to true, 20.0 to true, 15.0 to true, 10.0 to true,
            5.0 to true, 2.5 to true, 1.25 to true, 0.5 to false, 0.25 to false
        ))
    }
    var availableLbsPlates by remember {
        mutableStateOf(mapOf(
            45.0 to true, 35.0 to true, 25.0 to true, 10.0 to true,
            5.0 to true, 2.5 to true
        ))
    }

    val targetWeightDouble = targetWeightInput.toDoubleOrNull() ?: 0.0
    val inventory = if (isKg) {
        availableKgPlates.filter { it.value }.keys.sortedDescending()
    } else {
        availableLbsPlates.filter { it.value }.keys.sortedDescending()
    }

    val platesResult = remember(targetWeightDouble, barbellWeight, inventory, isKg) {
        calculatePlates(targetWeightDouble, barbellWeight, inventory)
    }

    val loadedWeight = barbellWeight + 2.0 * platesResult.sumOf { it.first * it.second }
    val unresolved = targetWeightDouble - loadedWeight

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Title and toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TARGET LOAD WEIGHT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        
                        // KG vs LBS Unit Switcher
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(true, false).forEach { value ->
                                val label = if (value) "KG" else "LBS"
                                val isSelected = isKg == value
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) com.example.ui.theme.AccentGreen else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            isKg = value
                                            // Auto-convert standard barbell weigh
                                            barbellWeight = if (value) 20.0 else 45.0
                                            targetWeightInput = if (value) "100" else "225"
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // TextField Input
                    OutlinedTextField(
                        value = targetWeightInput,
                        onValueChange = { targetWeightInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = {
                            Text(if (isKg) "kg" else "lbs", color = com.example.ui.theme.GrayMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.example.ui.theme.AccentGreen,
                            unfocusedBorderColor = com.example.ui.theme.GlassBorderDark,
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fast Increments presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val steps = if (isKg) listOf(-20.0, -5.0, -2.5, 2.5, 5.0, 20.0) else listOf(-45.0, -10.0, -5.0, 5.0, 10.0, 45.0)
                        steps.forEach { step ->
                            val label = if (step > 0) "+${step.toInt()}" else "${step.toInt()}"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(com.example.ui.theme.GlassDark, RoundedCornerShape(10.dp))
                                    .border(0.5.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val current = targetWeightInput.toDoubleOrNull() ?: 0.0
                                        val nextVal = (current + step).coerceAtLeast(barbellWeight)
                                        targetWeightInput = if (nextVal % 1.0 == 0.0) "${nextVal.toInt()}" else "$nextVal"
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Barbell select card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BARBELL WEIGHT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val barWeights = if (isKg) listOf(20.0, 15.0, 10.0, 8.0) else listOf(45.0, 35.0, 25.0, 15.0)
                        barWeights.forEach { wt ->
                            val selected = barbellWeight == wt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) com.example.ui.theme.AccentGreen else com.example.ui.theme.GlassDark,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { barbellWeight = wt }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${if (wt % 1.0 == 0.0) wt.toInt() else wt} ${if (isKg) "kg" else "lbs"}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live visual loaded barbell preview
        item {
            Text(
                "SLEEVE PREVIEW (ON EACH SIDE)",
                color = com.example.ui.theme.GrayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            VisualBarbellSleeve(platesResult, isKg)
        }

        // Detail load card breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL LOADED", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "${if (loadedWeight % 1.0 == 0.0) loadedWeight.toInt() else loadedWeight} ${if (isKg) "KG" else "LBS"}",
                            color = com.example.ui.theme.AccentGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Barbell Weight", color = com.example.ui.theme.GrayMedium, fontSize = 13.sp)
                        Text("${if (barbellWeight % 1.0 == 0.0) barbellWeight.toInt() else barbellWeight} ${if (isKg) "kg" else "lbs"}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Plates Weight (Both Sides)", color = com.example.ui.theme.GrayMedium, fontSize = 13.sp)
                        val platesTotalWeight = loadedWeight - barbellWeight
                        Text("${if (platesTotalWeight % 1.0 == 0.0) platesTotalWeight.toInt() else platesTotalWeight} ${if (isKg) "kg" else "lbs"}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (unresolved > 0.0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = com.example.ui.theme.GlassBorderDark)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Unresolved Weight", color = com.example.ui.theme.ErrorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${if (unresolved % 1.0 == 0.0) unresolved.toInt() else unresolved} ${if (isKg) "kg" else "lbs"}", color = com.example.ui.theme.ErrorColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Gym Plate Inventory management
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GYM PLATE INVENTORY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text("Toggle off plates that are not available in your gym.", color = com.example.ui.theme.GrayMedium, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isKg) {
                            availableKgPlates.forEach { (wt, available) ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (available) com.example.ui.theme.AccentGreen.copy(alpha = 0.25f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (available) com.example.ui.theme.AccentGreen else com.example.ui.theme.GlassBorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val mutable = availableKgPlates.toMutableMap()
                                            mutable[wt] = !available
                                            availableKgPlates = mutable
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${if (wt % 1.0 == 0.0) wt.toInt() else wt} kg",
                                        color = if (available) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            availableLbsPlates.forEach { (wt, available) ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (available) com.example.ui.theme.AccentGreen.copy(alpha = 0.25f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (available) com.example.ui.theme.AccentGreen else com.example.ui.theme.GlassBorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val mutable = availableLbsPlates.toMutableMap()
                                            mutable[wt] = !available
                                            availableLbsPlates = mutable
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${if (wt % 1.0 == 0.0) wt.toInt() else wt} lbs",
                                        color = if (available) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculatePlates(
    targetWeight: Double,
    barbellWeight: Double,
    availablePlates: List<Double>
): List<Pair<Double, Int>> {
    val remaining = targetWeight - barbellWeight
    if (remaining <= 0) return emptyList()
    
    val singleSide = remaining / 2.0
    var currentRemainder = singleSide
    val result = mutableListOf<Pair<Double, Int>>()
    
    for (plate in availablePlates) {
        if (plate <= 0) continue
        val countVal = (currentRemainder / plate).toInt()
        if (countVal > 0) {
            result.add(plate to countVal)
            currentRemainder -= countVal * plate
            currentRemainder = Math.round(currentRemainder * 100.0) / 100.0
        }
    }
    return result
}

@Composable
fun VisualBarbellSleeve(
    plates: List<Pair<Double, Int>>,
    isKg: Boolean
) {
    val itemsToDraw = remember(plates) {
        val list = mutableListOf<Double>()
        plates.forEach { (denom, count) ->
            repeat(count) {
                list.add(denom)
            }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(com.example.ui.theme.GlassDark, RoundedCornerShape(16.dp))
            .border(1.dp, com.example.ui.theme.GlassBorderDark, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            
            // Draw shaft extending right
            val sleeveLeft = 140f
            val sleeveWidth = width - 180f
            val sleeveHeight = 24f
            
            drawRect(
                color = Color(0xFF6B6B76),
                topLeft = Offset(sleeveLeft, centerY - sleeveHeight / 2f),
                size = androidx.compose.ui.geometry.Size(sleeveWidth, sleeveHeight)
            )
            
            // Draw inner barbell shaft going left
            drawRect(
                color = Color(0xFF45454F),
                topLeft = Offset(0f, centerY - 16f / 2f),
                size = androidx.compose.ui.geometry.Size(sleeveLeft, 16f)
            )
            
            // Draw collar stop
            drawRoundRect(
                color = Color(0xFF2C2C35),
                topLeft = Offset(sleeveLeft - 24f, centerY - 64f),
                size = androidx.compose.ui.geometry.Size(24f, 128f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            
            // Load plates side-by-side
            var currentX = sleeveLeft + 4f
            
            itemsToDraw.forEach { plateWeight ->
                val plateColor = getPlateColor(plateWeight, isKg)
                val plateHeight = getPlateHeight(plateWeight, isKg)
                val plateWidth = getPlateWidth(plateWeight, isKg)
                
                // Draw weight plate
                drawRoundRect(
                    color = plateColor,
                    topLeft = Offset(currentX, centerY - plateHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(plateWidth, plateHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                
                // Silver ring hub center
                drawCircle(
                    color = Color(0xFFCFD8DC),
                    radius = 9f,
                    center = Offset(currentX + plateWidth / 2f, centerY)
                )
                
                // Light inner shadow on plate for 3D bezel look
                drawRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    topLeft = Offset(currentX, centerY - plateHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(4f, plateHeight)
                )

                // Label inside plate
                val label = if (plateWeight % 1.0 == 0.0) "${plateWeight.toInt()}" else "$plateWeight"
                val textPaint = android.graphics.Paint().apply {
                    color = if (plateColor == Color.White || plateColor == Color(0xFFFFFFFF)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    textSize = 21f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    currentX + plateWidth / 2f,
                    centerY + 7f,
                    textPaint
                )
                
                currentX += plateWidth + 5f
            }
            
            // Collar clip clamp
            if (itemsToDraw.isNotEmpty()) {
                drawRoundRect(
                    color = Color(0xFFB0BEC5),
                    topLeft = Offset(currentX + 2f, centerY - 28f),
                    size = androidx.compose.ui.geometry.Size(12f, 56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }
        }
    }
}

fun getPlateColor(weight: Double, isKg: Boolean): Color {
    return if (isKg) {
        when {
            weight >= 25.0 -> Color(0xFFD32F2F) // Red
            weight >= 20.0 -> Color(0xFF1976D2) // Blue
            weight >= 15.0 -> Color(0xFFFBC02D) // Yellow
            weight >= 10.0 -> Color(0xFF388E3C) // Green
            weight >= 5.0 -> Color(0xFFEEEEEE)  // White
            weight >= 2.5 -> Color(0xFF37474F)  // Dark/Charcoal
            weight >= 1.25 -> Color(0xFF9E9E9E) // Medium Grey
            else -> Color(0xFFCFD8DC)           // Light Grey
        }
    } else {
        when {
            weight >= 45.0 -> Color(0xFFD32F2F) // Red
            weight >= 35.0 -> Color(0xFF1976D2) // Blue
            weight >= 25.0 -> Color(0xFFFBC02D) // Yellow
            weight >= 10.0 -> Color(0xFF388E3C) // Green
            weight >= 5.0 -> Color(0xFFEEEEEE)  // White
            else -> Color(0xFF37474F)           // Dark/Charcoal
        }
    }
}

fun getPlateHeight(weight: Double, isKg: Boolean): Float {
    return if (isKg) {
        when {
            weight >= 25.0 -> 120f
            weight >= 20.0 -> 110f
            weight >= 15.0 -> 100f
            weight >= 10.0 -> 90f
            weight >= 5.0 -> 75f
            weight >= 2.5 -> 62f
            weight >= 1.25 -> 52f
            else -> 42f
        }
    } else {
        when {
            weight >= 45.0 -> 120f
            weight >= 35.0 -> 110f
            weight >= 25.0 -> 98f
            weight >= 10.0 -> 85f
            weight >= 5.0 -> 72f
            else -> 58f
        }
    }
}

fun getPlateWidth(weight: Double, isKg: Boolean): Float {
    return if (isKg) {
        when {
            weight >= 25.0 -> 24f
            weight >= 20.0 -> 22f
            weight >= 15.0 -> 20f
            weight >= 10.0 -> 18f
            weight >= 5.0 -> 16f
            weight >= 2.5 -> 14f
            else -> 12f
        }
    } else {
        when {
            weight >= 45.0 -> 24f
            weight >= 35.0 -> 22f
            weight >= 25.0 -> 19f
            weight >= 10.0 -> 17f
            weight >= 5.0 -> 15f
            else -> 13f
        }
    }
}
