package com.example.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorScreen(
    onBack: () -> Unit
) {
    var targetWeight by remember { mutableStateOf(100.0) }
    var barbellWeight by remember { mutableStateOf(20.0) }
    
    // Custom Plate Calculator Logic
    // Valid plate colors:
    // Plate Red = #FF3B30
    // Plate Blue = #007AFF
    // Plate Yellow = #FFCC00
    // Plate Green = #34C759
    // Plate White = #FFFFFF
    // Plate Black = #333333
    // Sizes: 25(Red), 20(Blue), 15(Yellow), 10(Green), 5(White), 2.5(Black), 1.25(Black)
    
    val availablePlates = listOf(
        Pair(25.0, Color(0xFFFF3B30)),
        Pair(20.0, Color(0xFF007AFF)),
        Pair(15.0, Color(0xFFFFCC00)),
        Pair(10.0, Color(0xFF34C759)),
        Pair(5.0, Color(0xFFFFFFFF)),
        Pair(2.5, Color(0xFF333333)),
        Pair(1.25, Color(0xFF333333))
    )

    fun calculatePlates(weight: Double, bar: Double): List<Pair<Double, Color>> {
        var remainingWeightPerSide = (weight - bar) / 2.0
        val platesOnOneSide = mutableListOf<Pair<Double, Color>>()
        if (remainingWeightPerSide <= 0) return emptyList()

        for (plate in availablePlates) {
            while (remainingWeightPerSide >= plate.first) {
                platesOnOneSide.add(plate)
                remainingWeightPerSide -= plate.first
                // handle floating point precision
                remainingWeightPerSide = Math.round(remainingWeightPerSide * 100.0) / 100.0
            }
        }
        return platesOnOneSide
    }

    val plateList = calculatePlates(targetWeight, barbellWeight)

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("Plate Calculator", style = IronTypography.Title3) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
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
                .padding(IronSpacing.x16)
        ) {
            // Target Weight Stepper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg))
                    .padding(IronSpacing.x24),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronSpacing.x24)
                ) {
                    Box(modifier = Modifier.size(48.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)).bouncyClick { targetWeight = maxOf(barbellWeight, targetWeight - 2.5) }, contentAlignment = Alignment.Center) {
                        Text("-", style = IronTypography.LargeTitle)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${targetWeight}kg", style = IronTypography.LargeTitle)
                        Text("Target Weight", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    }

                    Box(modifier = Modifier.size(48.dp).glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)).bouncyClick { targetWeight += 2.5 }, contentAlignment = Alignment.Center) {
                        Text("+", style = IronTypography.LargeTitle)
                    }
                }
            }

            Spacer(modifier = Modifier.height(IronSpacing.x32))

            // Barbell visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .glassRecipe(RoundedCornerShape(IronCorner.RadiusLg)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Left sleeve
                    Box(modifier = Modifier.width(30.dp).height(20.dp).background(Color.Gray))
                    
                    // Plates
                    plateList.forEach { plate ->
                        val heightDp = when (plate.first) {
                            25.0 -> 140.dp
                            20.0 -> 130.dp
                            15.0 -> 110.dp
                            10.0 -> 90.dp
                            5.0 -> 70.dp
                            2.5 -> 50.dp
                            else -> 40.dp
                        }
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(heightDp)
                                .background(plate.second, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        )
                    }

                    // Barbell shaft
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .background(Color.DarkGray)
                    )
                    
                    // Right Plates (Reversed)
                    plateList.reversed().forEach { plate ->
                        val heightDp = when (plate.first) {
                            25.0 -> 140.dp
                            20.0 -> 130.dp
                            15.0 -> 110.dp
                            10.0 -> 90.dp
                            5.0 -> 70.dp
                            2.5 -> 50.dp
                            else -> 40.dp
                        }
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(heightDp)
                                .background(plate.second, RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        )
                    }
                    
                    // Right sleeve
                    Box(modifier = Modifier.width(30.dp).height(20.dp).background(Color.Gray))
                }
            }
            
            Spacer(modifier = Modifier.height(IronSpacing.x32))

            // Barbell weight segmented control
            Text("BARBELL WEIGHT", style = IronTypography.Caption.copy(color = TextTertiaryColor))
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
                            .bouncyClick {
                                barbellWeight = weight
                                if (targetWeight < weight) targetWeight = weight
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${weight.toInt()} kg",
                            style = IronTypography.Headline.copy(color = if (isSelected) BgColor else TextPrimaryColor)
                        )
                    }
                }
            }
        }
    }
}
