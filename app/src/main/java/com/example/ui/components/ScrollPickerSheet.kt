package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StepperChip(
    value: Double,
    unit: String,
    onValueChange: (Double) -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    step: Double = 2.5,
    displayValueOverride: String? = null
) {
    var showQuickAdjust by remember { mutableStateOf(false) }
    
    LaunchedEffect(showQuickAdjust) {
        if (showQuickAdjust) {
            delay(3000)
            showQuickAdjust = false
        }
    }
    
    Row(
        modifier = modifier
            .height(44.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showQuickAdjust = true }
            )
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showQuickAdjust) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .clickable { 
                        onValueChange(maxOf(0.0, value - step)) 
                        showQuickAdjust = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-", style = IronTypography.Subheading, color = TextPrimaryColor)
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(2f)
        ) {
            val displayVal = displayValueOverride ?: (if (value % 1.0 == 0.0) "${value.toInt()}" else String.format("%.1f", value))
            Text(
                text = displayVal,
                style = IronTypography.Headline,
                color = TextPrimaryColor
            )
        }
        
        if (showQuickAdjust) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .clickable { 
                        onValueChange(value + step) 
                        showQuickAdjust = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = IronTypography.Subheading, color = TextPrimaryColor)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScrollPickerSheet(
    initialValue: Double,
    type: String, // "WEIGHT" or "REPS"
    onDismiss: () -> Unit,
    onDone: (Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var wholePart by remember { mutableStateOf(initialValue.toInt()) }
    var decimalPart by remember { mutableStateOf(if (initialValue % 1.0 >= 0.5) 0.5 else 0.0) }
    var repPart by remember { mutableStateOf(if (initialValue < 1.0) 1 else initialValue.toInt()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (type == "WEIGHT") "WEIGHT" else "REPS",
                style = IronTypography.Caption.copy(color = TextSecondaryColor)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(contentAlignment = Alignment.Center) {
                // Shared Highlight Band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (type == "WEIGHT") {
                        val wholeItems = (0..300).map { it.toString() }
                        val decItems = listOf(".0", ".5")
                        
                        val wholeState = rememberPagerState(initialPage = minOf(wholePart, 300), pageCount = { wholeItems.size })
                        val decState = rememberPagerState(initialPage = if (decimalPart == 0.0) 0 else 1, pageCount = { decItems.size })
                        
                        LaunchedEffect(wholeState.currentPage) {
                            wholePart = wholeItems[wholeState.currentPage].toInt()
                        }
                        LaunchedEffect(decState.currentPage) {
                            decimalPart = if (decState.currentPage == 0) 0.0 else 0.5
                        }
                        
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            VerticalWheelPicker(state = wholeState, items = wholeItems, modifier = Modifier.width(80.dp))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                            VerticalWheelPicker(state = decState, items = decItems, modifier = Modifier.width(60.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("kg", style = IronTypography.Headline, color = TextSecondaryColor)
                        }
                    } else {
                        val repItems = (1..50).map { it.toString() }
                        val repState = rememberPagerState(initialPage = maxOf(0, repPart - 1), pageCount = { repItems.size })
                        
                        LaunchedEffect(repState.currentPage) {
                            repPart = repItems[repState.currentPage].toInt()
                        }
                        
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                            VerticalWheelPicker(state = repState, items = repItems, modifier = Modifier.width(80.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    onDone(if (type == "WEIGHT") wholePart + decimalPart else repPart.toDouble()) 
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(IronCorner.RadiusSm)
            ) {
                Text("Done", style = IronTypography.Headline.copy(color = Color.Black))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalWheelPicker(
    state: PagerState,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    VerticalPager(
        state = state,
        modifier = modifier.height(192.dp), // 64 * 3
        contentPadding = PaddingValues(vertical = 64.dp),
        pageSize = PageSize.Fixed(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { page ->
        val pageOffset = (state.currentPage - page) + state.currentPageOffsetFraction
        val absoluteOffset = abs(pageOffset)
        
        val alphaVal = 1f - minOf(1f, absoluteOffset * 0.6f)
        val scaleVal = 1f - minOf(0.4f, absoluteOffset * 0.2f)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .alpha(alphaVal)
                .graphicsLayer {
                    scaleX = scaleVal
                    scaleY = scaleVal
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = items[page],
                style = IronTypography.Display.copy(fontSize = 32.sp), 
                color = Color.White
            )
        }
    }
}
