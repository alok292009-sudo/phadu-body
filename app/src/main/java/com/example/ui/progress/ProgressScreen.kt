package com.example.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.IronLogRepository
import com.example.model.Exercise
import com.example.model.PersonalRecord
import com.example.model.Workout
import com.example.ui.theme.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(repository: IronLogRepository) {
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var history by remember { mutableStateOf<List<Workout>>(emptyList()) }

    LaunchedEffect(Unit) {
        launch {
            repository.getExercises().collect { exercises = it }
        }
        launch {
            repository.getWorkouts().collect { history = it.filter { w -> w.status == "completed" } }
        }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("Progress", style = IronTypography.Title2) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = IronSpacing.x16),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                if (history.isNotEmpty()) {
                    Text("VOLUME HISTORY", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                    Spacer(modifier = Modifier.height(IronSpacing.x12))
                    VolumeDashboardCustom(history)
                } else {
                    EmptyState(message = "Complete workouts to see your volume trend here.")
                }
                Spacer(modifier = Modifier.height(IronSpacing.x32))
            }

            item {
                Text("EXERCISE PROGRESSION", style = IronTypography.Caption.copy(color = TextTertiaryColor))
                Spacer(modifier = Modifier.height(IronSpacing.x12))
            }

            val list = exercises.filter { ex -> history.any { w -> w.loggedExercises.any { le -> le.exerciseId == ex.id } } }
            
            if (list.isEmpty()) {
                item {
                    EmptyState(message = "No exercise data yet. Log exercises to build your charts.")
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(IronSpacing.x12)
                    ) {
                        list.forEach { ex ->
                            ProgressSwipeableCard(ex = ex, history = history)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressSwipeableCard(ex: Exercise, history: List<Workout>) {
    var isExpanded by remember { mutableStateOf(false) }
    val dataPoints = remember(ex.id, history) {
        val points = history.sortedBy { it.date }.mapNotNull { w ->
            val log = w.loggedExercises.find { it.exerciseId == ex.id }
            if (log != null && log.sets.isNotEmpty()) {
                val best = log.sets.filter { it.completedAt != null }.maxOfOrNull { it.weight }
                if (best != null && best > 0) Pair(w.date, best.toFloat()) else null
            } else null
        }
        points
    }
    
    if (dataPoints.isEmpty()) return

    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 320.dp)
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
            .bouncyClick { isExpanded = !isExpanded }
            .padding(IronSpacing.x20)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(ex.name, style = IronTypography.Headline)
            Text("${dataPoints.last().second.toInt()} ${ex.unit}", style = IronTypography.Callout)
        }
        Spacer(modifier = Modifier.height(IronSpacing.x16))
        
        // Mini sparkline always visible
        SparklineChart(points = dataPoints, height = 60)
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = IronSpacing.x16)) {
                // Full chart
                SparklineChart(points = dataPoints, height = 150)
            }
        }
    }
}

@Composable
fun SparklineChart(points: List<Pair<Long, Float>>, height: Int) {
    if (points.size < 2) {
        Text("Need more data points", style = IronTypography.Caption.copy(color = TextSecondaryColor))
        return
    }
    
    val maxVal = points.maxOf { it.second }
    val minVal = points.minOf { it.second }
    val range = if (maxVal == minVal) 1f else maxVal - minVal

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
    ) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)

        val path = Path()
        var currentX = 0f
        
        points.forEachIndexed { i, pt ->
            val normY = (pt.second - minVal) / range
            val y = h - (normY * h * 0.8f) - (h * 0.1f) // 10% padding top/bottom
            
            if (i == 0) {
                path.moveTo(currentX, y)
            } else {
                path.lineTo(currentX, y)
            }
            currentX += stepX
        }
        
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun VolumeDashboardCustom(history: List<Workout>) {
    val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    val recent = history.filter { it.date >= sevenDaysAgo }
    val currentWeeklyVol = recent.sumOf { it.totalVolume }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
            .padding(IronSpacing.x24)
    ) {
        Column {
            Text("${currentWeeklyVol.toInt()} kg", style = IronTypography.LargeTitle)
            Text("Past 7 Days", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
            Spacer(modifier = Modifier.height(IronSpacing.x24))
            
            // Fix inverted rendering bug, date ascending order
            val sorted = history.sortedBy { it.date }.takeLast(14)
            if (sorted.size >= 2) {
                CanvasChartSmooth(points = sorted.map { Pair(it.date, it.totalVolume.toFloat()) })
            } else {
                Text("Not enough data to plot volume curve.", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
            }
        }
    }
}

@Composable
fun CanvasChartSmooth(points: List<Pair<Long, Float>>) {
    val maxVal = points.maxOf { it.second }
    val minVal = points.minOf { it.second }
    val range = if (maxVal == minVal) 1f else maxVal - minVal

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)

        val path = Path()
        val fillPath = Path()
        var currentX = 0f
        
        val mappedPoints = points.mapIndexed { i, pt ->
            val normY = (pt.second - minVal) / range
            val y = h - (normY * h * 0.8f) - (h * 0.1f)
            Pair(i * stepX, y)
        }

        path.moveTo(mappedPoints.first().first, mappedPoints.first().second)
        fillPath.moveTo(mappedPoints.first().first, h)
        fillPath.lineTo(mappedPoints.first().first, mappedPoints.first().second)

        // Smooth curve
        for (i in 0 until mappedPoints.size - 1) {
            val p0 = mappedPoints[i]
            val p1 = mappedPoints[i + 1]
            val cx = (p0.first + p1.first) / 2f
            
            path.cubicTo(cx, p0.second, cx, p1.second, p1.first, p1.second)
            fillPath.cubicTo(cx, p0.second, cx, p1.second, p1.first, p1.second)
        }

        fillPath.lineTo(mappedPoints.last().first, h)
        fillPath.close()

        // Gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        // White line
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
