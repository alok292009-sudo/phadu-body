package com.example.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IronLogRepository
import com.example.model.Workout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: IronLogRepository) {
    var history by remember { mutableStateOf<List<Workout>>(emptyList()) }

    LaunchedEffect(Unit) {
        repository.getWorkouts().collect { workouts ->
            history = workouts.filter { it.status == "completed" }
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
                title = { Text("HISTORY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TrainingCalendar(workouts = history)
            }
            
            items(history.sortedByDescending { it.date }) { workout ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
                    border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dateStr = SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(workout.date)).uppercase()
                        Text((workout.templateName ?: "AD-HOC WORKOUT").uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
                        Text(dateStr, color = com.example.ui.theme.GrayMedium, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${workout.totalVolume.toInt()}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                                Text("VOLUME (KG)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium, letterSpacing = 1.sp)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text("${workout.durationMinutes}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                                Text("MINUTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.GrayMedium, letterSpacing = 1.sp)
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
fun TrainingCalendar(workouts: List<Workout>) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val workoutDates = remember(workouts) {
        workouts.map { sdf.format(Date(it.date)) }.toSet()
    }

    var currentMonthOffset by remember { mutableIntStateOf(0) }
    
    val calendar = remember(currentMonthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, currentMonthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 (Sun) to 6 (Sat)
    
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time).uppercase()
    
    val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.GlassDark),
        border = BorderStroke(1.dp, com.example.ui.theme.GlassBorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonthOffset-- }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month", tint = Color.White)
                }
                
                Text(
                    text = monthName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                
                IconButton(onClick = { currentMonthOffset++ }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Days of week
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                weekDays.forEach { day ->
                    Text(
                        text = day,
                        color = com.example.ui.theme.GrayMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Calendar Grid
            val totalCells = ((daysInMonth + firstDayOfWeek + 6) / 7) * 7
            
            Column {
                for (row in 0 until (totalCells / 7)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayOfMonth = cellIndex - firstDayOfWeek + 1
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayOfMonth in 1..daysInMonth) {
                                    val cellCalendar = calendar.clone() as Calendar
                                    cellCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    val dateStr = sdf.format(cellCalendar.time)
                                    val hasWorkout = workoutDates.contains(dateStr)
                                    val isToday = sdf.format(Date()) == dateStr
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    hasWorkout -> Color.White
                                                    isToday -> com.example.ui.theme.GrayDark
                                                    else -> Color.Transparent
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayOfMonth.toString(),
                                            color = when {
                                                hasWorkout -> Color.Black
                                                isToday -> Color.White
                                                else -> Color.White
                                            },
                                            fontSize = 14.sp,
                                            fontWeight = if (hasWorkout || isToday) FontWeight.Bold else FontWeight.Normal
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
}
