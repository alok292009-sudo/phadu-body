package com.example.ui.prs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.IronLogRepository
import com.example.model.Exercise
import com.example.model.PersonalRecord
import com.example.ui.theme.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PRsScreen(repository: IronLogRepository) {
    var exercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var prs by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        launch {
            repository.getExercises().combine(repository.getPersonalRecords()) { exList, prList ->
                exercises = exList
                prs = prList.filterNotNull()
            }.collect {}
        }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("Personal Records", style = IronTypography.Title2) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = IronSpacing.x16),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (prs.isEmpty()) {
                item {
                    EmptyState(message = "No PRs set yet. Keep lifting!")
                }
            } else {
                items(prs) { pr ->
                    val ex = exercises.find { it.id == pr.exerciseId }
                    if (ex != null && (pr.bestWeight != null || pr.bestEstimated1RM != null)) {
                        PrCard(pr = pr, ex = ex)
                    }
                }
            }
        }
    }
}

@Composable
fun PrCard(pr: PersonalRecord, ex: Exercise) {
    val displayDf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = IronSpacing.x16)
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
            .padding(IronSpacing.x20)
    ) {
        Text(ex.name, style = IronTypography.Headline)
        Spacer(modifier = Modifier.height(IronSpacing.x16))

        if (pr.bestWeight != null && pr.bestWeight.value > 0) {
            val dateStr = displayDf.format(Date(pr.bestWeight.date))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Max Weight", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    Text(dateStr, style = IronTypography.Footnote.copy(color = TextTertiaryColor))
                }
                Text("${pr.bestWeight.value} ${ex.unit}", style = IronTypography.Body)
            }
        }
        
        if (pr.bestEstimated1RM != null && pr.bestEstimated1RM.value > 0) {
            val dateStr = displayDf.format(Date(pr.bestEstimated1RM.date))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Est 1RM", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    Text(dateStr, style = IronTypography.Footnote.copy(color = TextTertiaryColor))
                }
                Text("${pr.bestEstimated1RM.value} ${ex.unit}", style = IronTypography.Body)
            }
        }
    }
}
