package com.example.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Workout
import com.example.model.WorkoutSet
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data Models
data class ExerciseSummary(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val mostRecentDate: Long,
    val mostRecentSetsString: String,
    val bestWeightEver: Double,
    val allSessions: List<ExerciseSession>
)

data class ExerciseSession(
    val date: Long,
    val sets: List<WorkoutSet>
)

enum class SortType(val label: String) {
    RECENT("Most Recent"), ALPHA("A-Z"), MUSCLE("Muscle Group")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: IronLogRepository) {
    val coroutineScope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var selectedDateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }

    var selectedFilterGroup by remember { mutableStateOf("All") }
    var currentSort by remember { mutableStateOf(SortType.RECENT) }
    var searchQuery by remember { mutableStateOf("") }
    
    var selectedExerciseForDetail by remember { mutableStateOf<ExerciseSummary?>(null) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val shortDateDf = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    
    val allMuscleGroups = listOf("All", "Chest", "Back", "Lats", "Shoulders", "Arms", "Legs", "Core")

    LaunchedEffect(Unit) {
        selectedDateStr = sdf.format(Date())
        repository.getWorkouts().collect { workouts ->
            history = workouts.filter { it.status == "completed" }
        }
    }

    val exerciseSummaries = remember(history) {
        val map = mutableMapOf<String, MutableList<ExerciseSession>>()
        val infoMap = mutableMapOf<String, Pair<String, String>>()
        
        history.forEach { workout ->
            workout.loggedExercises.forEach { le ->
                val validSets = le.sets.filter { it.completedAt != null && !it.isWarmup }
                if (validSets.isNotEmpty()) {
                    map.getOrPut(le.exerciseId) { mutableListOf() }
                        .add(ExerciseSession(workout.date, validSets))
                    infoMap[le.exerciseId] = Pair(le.exerciseName, le.muscleGroup)
                }
            }
        }
        
        val result = map.map { (exId, sessions) ->
            val sortedSessions = sessions.sortedByDescending { it.date }
            val mostRecent = sortedSessions.first()
            val bestWeight = sessions.flatMap { it.sets }.maxOfOrNull { it.weight } ?: 0.0
            val info = infoMap[exId]!!
            
            val recentDateStr = shortDateDf.format(Date(mostRecent.date))
            val setsStr = mostRecent.sets.take(3).joinToString(", ") { 
                val w = if (it.weight % 1.0 == 0.0) it.weight.toInt().toString() else it.weight.toString()
                "${w}kg×${it.reps}" 
            } + if (mostRecent.sets.size > 3) ", ..." else ""
            val mostRecentString = "$recentDateStr — ${mostRecent.sets.size} sets: $setsStr"
            
            ExerciseSummary(
                exerciseId = exId,
                exerciseName = info.first,
                muscleGroup = info.second.uppercase(),
                mostRecentDate = mostRecent.date,
                mostRecentSetsString = mostRecentString,
                bestWeightEver = bestWeight,
                allSessions = sortedSessions
            )
        }
        result
    }
    
    val filteredAndSorted = remember(exerciseSummaries, selectedFilterGroup, currentSort, searchQuery) {
        val filtered = exerciseSummaries.filter { summary ->
            (selectedFilterGroup == "All" || summary.muscleGroup.equals(selectedFilterGroup, ignoreCase = true)) &&
            (searchQuery.isBlank() || summary.exerciseName.contains(searchQuery, ignoreCase = true))
        }
        
        when (currentSort) {
            SortType.RECENT -> filtered.sortedByDescending { it.mostRecentDate }
            SortType.ALPHA -> filtered.sortedBy { it.exerciseName }
            SortType.MUSCLE -> filtered.sortedBy { it.muscleGroup }
        }
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("History", style = IronTypography.Subheading) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = IronSpacing.x16),
            verticalArrangement = Arrangement.spacedBy(IronSpacing.x16)
        ) {
            item {
                CalendarWidget(
                    currentMonth = currentMonth,
                    selectedDateStr = selectedDateStr,
                    history = history,
                    onMonthChange = { diff ->
                        val newMonth = currentMonth.clone() as Calendar
                        newMonth.add(Calendar.MONTH, diff)
                        currentMonth = newMonth
                    },
                    onDateSelect = { selectedDateStr = it }
                )
            }
            
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search exercises...", style = IronTypography.Body.copy(color = TextSecondaryColor)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = IronSpacing.x16),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextPrimaryColor,
                        unfocusedBorderColor = BorderStrongColor,
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                        cursorColor = TextPrimaryColor
                    ),
                    shape = RoundedCornerShape(IronCorner.RadiusMd)
                )
            }
            
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allMuscleGroups) { group ->
                        val isSelected = group == selectedFilterGroup
                        Box(
                            modifier = Modifier
                                .bouncyClick { selectedFilterGroup = group }
                                .background(if (isSelected) TextPrimaryColor else Color.Transparent, RoundedCornerShape(IronCorner.RadiusFull))
                                .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(IronCorner.RadiusFull))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = group.uppercase(),
                                style = IronTypography.Caption,
                                color = if (isSelected) BgColor else TextPrimaryColor
                            )
                        }
                    }
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EXERCISE BREAKDOWN", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    Row(
                        modifier = Modifier.clickable { 
                            currentSort = when(currentSort) {
                                SortType.RECENT -> SortType.ALPHA
                                SortType.ALPHA -> SortType.MUSCLE
                                SortType.MUSCLE -> SortType.RECENT
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort: ${currentSort.label}", style = IronTypography.Footnote, color = TextPrimaryColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Outlined.Sort, contentDescription = "Sort", tint = TextPrimaryColor, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (filteredAndSorted.isEmpty()) {
                item {
                    EmptyState(message = "No logged exercises found.")
                }
            } else {
                items(filteredAndSorted) { ex ->
                    ExerciseHistoryRow(ex) {
                        selectedExerciseForDetail = ex
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
    
    if (selectedExerciseForDetail != null) {
        ExerciseDetailSheet(
            summary = selectedExerciseForDetail!!,
            onDismiss = { selectedExerciseForDetail = null }
        )
    }
}

@Composable
fun ExerciseHistoryRow(ex: ExerciseSummary, onClick: () -> Unit) {
    val bestStr = if (ex.bestWeightEver % 1.0 == 0.0) ex.bestWeightEver.toInt().toString() else ex.bestWeightEver.toString()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
            .bouncyClick { onClick() }
            .padding(IronSpacing.x16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutoResizingText(
                        text = ex.exerciseName,
                        style = IronTypography.Headline,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(ex.muscleGroup, style = IronTypography.Caption.copy(fontSize = 9.sp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ex.mostRecentSetsString,
                    style = IronTypography.Footnote.copy(color = TextSecondaryColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${bestStr}kg PR",
                style = IronTypography.Callout.copy(color = Color.White),
                textAlign = TextAlign.End
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(summary: ExerciseSummary, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayDf = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.exerciseName, style = IronTypography.Title, color = TextPrimaryColor)
                    Text(summary.muscleGroup, style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.offset(x = 12.dp, y = (-12).dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextPrimaryColor)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Chart Area
            Text("PROGRESSION CHART (MAX WEIGHT)", style = IronTypography.Caption.copy(color = TextTertiaryColor))
            Spacer(modifier = Modifier.height(12.dp))
            
            val reversedSessions = summary.allSessions.sortedBy { it.date }
            if (reversedSessions.size > 1) {
                val weights = reversedSessions.map { s -> s.sets.maxOfOrNull { it.weight } ?: 0.0 }.filter { it > 0.0 }
                if (weights.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(SurfaceColor, RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val maxW = weights.maxOrNull() ?: 1.0
                            val minW = (weights.minOrNull() ?: 0.0) * 0.9
                            val range = maxW - minW
                            val rangeNorm = if (range == 0.0) 1.0 else range
                            
                            val stepX = size.width / (weights.size - 1).coerceAtLeast(1)
                            val path = Path()
                            
                            weights.forEachIndexed { i, w ->
                                val x = i * stepX
                                val y = size.height - (((w - minW) / rangeNorm) * size.height).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = TextPrimaryColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            
                            weights.forEachIndexed { i, w ->
                                val x = i * stepX
                                val y = size.height - (((w - minW) / rangeNorm) * size.height).toFloat()
                                drawCircle(color = BgColor, radius = 4.dp.toPx(), center = Offset(x, y))
                                drawCircle(color = TextPrimaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(SurfaceColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("Not enough data points yet", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(SurfaceColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("Complete more workouts to see progress chart", style = IronTypography.Footnote.copy(color = TextSecondaryColor))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("SESSION HISTORY", style = IronTypography.Caption.copy(color = TextTertiaryColor))
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(summary.allSessions) { session ->
                    val dateStr = displayDf.format(Date(session.date))
                    val sessionMax = session.sets.maxOfOrNull { it.weight } ?: 0.0
                    val isPR = sessionMax >= summary.bestWeightEver && summary.bestWeightEver > 0.0
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dateStr, style = IronTypography.Headline)
                            if (isPR) {
                                Box(
                                    modifier = Modifier
                                        .background(SuccessColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("PR", style = IronTypography.Caption.copy(color = SuccessColor, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        session.sets.forEach { set ->
                            val isPrSet = set.weight >= summary.bestWeightEver && summary.bestWeightEver > 0.0
                            val weightStr = if (set.weight % 1.0 == 0.0) set.weight.toInt().toString() else set.weight.toString()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Set ${set.setNumber}",
                                    style = IronTypography.Footnote.copy(color = TextSecondaryColor),
                                    modifier = Modifier.width(50.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "$weightStr kg × ${set.reps}",
                                    style = IronTypography.Body.copy(color = if (isPrSet) SuccessColor else TextPrimaryColor, fontWeight = if (isPrSet) FontWeight.Bold else FontWeight.Normal)
                                )
                                if (set.rpe != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "@${if (set.rpe % 1.0f == 0.0f) set.rpe.toInt().toString() else set.rpe.toString()}",
                                        style = IronTypography.Footnote.copy(color = TextSecondaryColor)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// Ensure the CalendarWidget is here so the app keeps working
@Composable
fun CalendarWidget(
    currentMonth: Calendar,
    selectedDateStr: String,
    history: List<Workout>,
    onMonthChange: (Int) -> Unit,
    onDateSelect: (String) -> Unit
) {
    val monthSdf = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    val workoutDates = remember(history) {
        history.map { sdf.format(Date(it.date)) }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Prev", tint = TextPrimaryColor) }
            Text(monthSdf.format(currentMonth.time), style = IronTypography.Headline)
            IconButton(onClick = { onMonthChange(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next", tint = TextPrimaryColor) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Days of week
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    style = IronTypography.Footnote.copy(color = TextTertiaryColor),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        var dayCounter = 1
        for (row in 0..5) {
            if (dayCounter > maxDays) break
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (col in 0..6) {
                    if (row == 0 && col < firstDayOfWeek || dayCounter > maxDays) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val currentDay = dayCounter
                        cal.set(Calendar.DAY_OF_MONTH, currentDay)
                        val dateString = sdf.format(cal.time)
                        val isSelected = dateString == selectedDateStr
                        val hasWorkout = workoutDates.contains(dateString)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .bouncyClick { onDateSelect(dateString) }
                                .background(
                                    if (isSelected) SurfaceRaisedColor else if (hasWorkout) SurfaceColor else Color.Transparent,
                                    RoundedCornerShape(IronCorner.RadiusSm)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) TextPrimaryColor else Color.Transparent,
                                    RoundedCornerShape(IronCorner.RadiusSm)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentDay.toString(),
                                style = IronTypography.Callout.copy(color = if (isSelected) BgColor else TextPrimaryColor)
                            )
                        }
                        dayCounter++
                    }
                }
            }
        }
    }
}
