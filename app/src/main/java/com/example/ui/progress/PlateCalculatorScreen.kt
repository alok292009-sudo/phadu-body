package com.example.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorScreen(
    onBack: () -> Unit
) {
    var barbellWeight by remember { mutableStateOf(20.0) }
    val selectedPlates = remember { mutableStateListOf<Double>() }
    
    // Logic: Rod weight + (Sum of plates on one side * 2)
    val perSideWeight = selectedPlates.sum()
    val totalWeight = barbellWeight + (perSideWeight * 2)
    
    val availablePlates = listOf(
        Pair(25.0, Plate25),
        Pair(20.0, Plate20),
        Pair(15.0, Plate15),
        Pair(10.0, Plate10),
        Pair(5.0, Plate5),
        Pair(2.5, Plate2_5),
        Pair(1.25, Plate2_5)
    )

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("PLATE CALCULATOR", style = IronTypography.Title.copy(letterSpacing = 1.sp), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                },
                actions = {
                    IconButton(onClick = { selectedPlates.clear() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Clear", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(IronSpacing.x16)
        ) {
            var showTargetWeightPicker by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                    .padding(IronSpacing.x16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target Weight", style = IronTypography.Body.copy(color = Color.White))
                com.example.ui.components.StepperChip(
                    value = totalWeight,
                    unit = "KG",
                    onValueChange = { newVal ->
                        // Calculate plates for newVal
                        selectedPlates.clear()
                        var remaining = (newVal - barbellWeight) / 2.0
                        for ((plateWeight, _) in availablePlates) {
                            while (remaining >= plateWeight) {
                                selectedPlates.add(plateWeight)
                                remaining -= plateWeight
                            }
                        }
                    },
                    onClick = { showTargetWeightPicker = true },
                    modifier = Modifier.width(120.dp)
                )
            }
            
            if (showTargetWeightPicker) {
                com.example.ui.components.ScrollPickerSheet(
                    initialValue = totalWeight,
                    type = "WEIGHT",
                    onDismiss = { showTargetWeightPicker = false },
                    onDone = { newVal ->
                        selectedPlates.clear()
                        var remaining = (newVal - barbellWeight) / 2.0
                        for ((plateWeight, _) in availablePlates) {
                            while (remaining >= plateWeight) {
                                selectedPlates.add(plateWeight)
                                remaining -= plateWeight
                            }
                        }
                        showTargetWeightPicker = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(IronSpacing.x16))

            // Total Weight Display (Most prominent)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val displayWeight = if (totalWeight % 1.0 == 0.0) totalWeight.toInt().toString() else String.format("%.2f", totalWeight)
                    Text(
                        text = displayWeight,
                        style = IronTypography.Display.copy(fontSize = 72.sp),
                        color = TextPrimaryColor
                    )
                    Text(
                        text = "TOTAL KILOGRAMS",
                        style = IronTypography.Caption.copy(color = TextSecondaryColor, letterSpacing = 2.sp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusFull))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${barbellWeight.toInt()}kg Bar",
                            style = IronTypography.Caption.copy(color = TextPrimaryColor)
                        )
                        Box(modifier = Modifier.padding(horizontal = 8.dp).size(2.dp).background(TextTertiaryColor))
                        Text(
                            text = "${perSideWeight}kg Per Side",
                            style = IronTypography.Caption.copy(color = TextSecondaryColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            // Barbell visualizer (One side only)
            Text("BARBELL LOAD (SINGLE SIDE)", style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 1.sp))
            Spacer(modifier = Modifier.height(IronSpacing.x12))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp)
                ) {
                    // Barbell shaft (Rod)
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .background(Color.DarkGray.copy(alpha = 0.8f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    )
                    
                    // Sleeve stopper
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(44.dp)
                            .background(Color.Gray, RoundedCornerShape(2.dp))
                    )

                    // Plates on one side
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedPlates.forEachIndexed { index, plateWeight ->
                            val (weight, color) = availablePlates.find { it.first == plateWeight } ?: (plateWeight to Color.Gray)
                            val heightDp = when {
                                weight >= 25.0 -> 120.dp
                                weight >= 20.0 -> 110.dp
                                weight >= 15.0 -> 100.dp
                                weight >= 10.0 -> 80.dp
                                weight >= 5.0 -> 60.dp
                                weight >= 2.5 -> 40.dp
                                else -> 30.dp
                            }
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(heightDp)
                                    .background(color, RoundedCornerShape(2.dp))
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                    .bouncyClick { selectedPlates.removeAt(index) }
                            )
                        }
                        
                        // Remaining sleeve
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        )
                    }
                }
                
                if (selectedPlates.isEmpty()) {
                    Text("TAP PLATES TO LOAD", style = IronTypography.Caption.copy(color = TextTertiaryColor, fontSize = 10.sp))
                } else {
                    Text(
                        "TAP PLATE TO REMOVE", 
                        style = IronTypography.Caption.copy(color = TextTertiaryColor.copy(alpha = 0.5f), fontSize = 10.sp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            // Bar Selection
            Text("CHOOSE ROD WEIGHT", style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 1.sp))
            Spacer(modifier = Modifier.height(IronSpacing.x12))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(20.0, 15.0, 10.0).forEach { weight ->
                    val isSelected = barbellWeight == weight
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) TextPrimaryColor else Color.Transparent,
                                RoundedCornerShape(IronCorner.RadiusSm)
                            )
                            .bouncyClick { barbellWeight = weight }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${weight.toInt()} KG",
                            style = IronTypography.Headline.copy(
                                color = if (isSelected) BgColor else TextPrimaryColor,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x24))

            // Plate Selection
            Text("ADD PLATES (PER SIDE)", style = IronTypography.Caption.copy(color = TextTertiaryColor, letterSpacing = 1.sp))
            Spacer(modifier = Modifier.height(IronSpacing.x12))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(availablePlates) { (weight, color) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.bouncyClick { selectedPlates.add(weight) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(color, RoundedCornerShape(IronCorner.RadiusFull))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(IronCorner.RadiusFull)),
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor = if (weight == 5.0) Color.Black else Color.White
                            Text(
                                text = if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString(),
                                style = IronTypography.Headline.copy(color = textColor),
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${weight}kg", style = IronTypography.Caption.copy(fontSize = 11.sp, color = TextSecondaryColor))
                    }
                }
            }
        }
    }
}
