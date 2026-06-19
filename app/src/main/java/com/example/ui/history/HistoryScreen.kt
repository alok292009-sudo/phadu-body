package com.example.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.IronLogRepository
import com.example.model.Workout
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: IronLogRepository) {
    var history by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var selectedDateStr by remember { mutableStateOf("") }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var isCalendarView by remember { mutableStateOf(true) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDf = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        selectedDateStr = sdf.format(Date())
        repository.getWorkouts().collect { workouts ->
            history = workouts.filter { it.status == "completed" }
        }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("History", style = IronTypography.Title2) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor),
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = IronSpacing.x16)
                            .glassRecipe(RoundedCornerShape(IronCorner.RadiusSm))
                    ) {
                        IconButton(
                            onClick = { isCalendarView = true },
                            modifier = Modifier.background(if (isCalendarView) TextPrimaryColor else Color.Transparent, RoundedCornerShape(IronCorner.RadiusSm))
                        ) {
                            Icon(Icons.Outlined.Event, contentDescription = "Calendar", tint = if (isCalendarView) BgColor else TextPrimaryColor)
                        }
                        IconButton(
                            onClick = { isCalendarView = false },
                            modifier = Modifier.background(if (!isCalendarView) TextPrimaryColor else Color.Transparent, RoundedCornerShape(IronCorner.RadiusSm))
                        ) {
                            Icon(Icons.Outlined.List, contentDescription = "List", tint = if (!isCalendarView) BgColor else TextPrimaryColor)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(horizontal = IronSpacing.x16),
            verticalArrangement = Arrangement.spacedBy(IronSpacing.x16)
        ) {
            if (isCalendarView) {
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

                val matchedWorkouts = history.filter { sdf.format(Date(it.date)) == selectedDateStr }
                
                item {
                    val dateObj = sdf.parse(selectedDateStr)
                    val formatted = if (dateObj != null) displayDf.format(dateObj) else selectedDateStr
                    Text(
                        text = "Workout on $formatted",
                        style = IronTypography.Caption.copy(color = TextSecondaryColor),
                        modifier = Modifier.padding(top = IronSpacing.x16)
                    )
                }

                if (matchedWorkouts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().glassRecipe(RoundedCornerShape(IronCorner.RadiusMd)).padding(IronSpacing.x24),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Rest Day", style = IronTypography.Body.copy(color = TextSecondaryColor))
                        }
                    }
                } else {
                    items(matchedWorkouts) { wk ->
                        WorkoutHistoryCard(workout = wk)
                    }
                }
            } else {
                if (history.isEmpty()) {
                    item {
                        EmptyState(message = "No workouts found in your history.")
                    }
                } else {
                    items(history.sortedByDescending { it.date }) { workout ->
                        WorkoutHistoryCard(workout = workout)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun CalendarWidget(
    currentMonth: Calendar,
    selectedDateStr: String,
    history: List<Workout>,
    onMonthChange: (Int) -> Unit,
    onDateSelect: (String) -> Unit
) {
    val monthSdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
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

@Composable
fun WorkoutHistoryCard(workout: Workout) {
    val displayDf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val timeDf = SimpleDateFormat("h:mm a", Locale.getDefault())
    val d = Date(workout.date)
    val displayDate = displayDf.format(d)
    val displayTime = timeDf.format(d)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
            .padding(IronSpacing.x20)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(workout.templateName ?: "Workout", style = IronTypography.Title3)
                Text("$displayDate at $displayTime", style = IronTypography.Caption.copy(color = TextSecondaryColor))
            }
            Text("${workout.totalVolume.toInt()} kg", style = IronTypography.Headline)
        }

        Spacer(modifier = Modifier.height(IronSpacing.x16))
        
        workout.loggedExercises.forEach { ex ->
            val muscleGroup = ex.muscleGroup?.uppercase() ?: "GENERAL"
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(ex.exerciseName, style = IronTypography.Body)
                    Text(muscleGroup, style = IronTypography.Caption.copy(color = TextSecondaryColor))
                }
                Text("${ex.sets.filter { it.completedAt != null }.size} sets", style = IronTypography.Footnote)
            }
        }
    }
}
