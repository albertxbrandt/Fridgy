package fyi.goodbye.fridgy.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.theme.FridgeTypeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A modern card component that displays summary information for a single fridge.
 * Features clean typography, subtle elevation, and consistent Material 3 styling.
 *
 * @param fridge The [DisplayFridge] data to show.
 * @param onClick Callback triggered when the card is clicked.
 */
@Composable
fun FridgeCard(
    fridge: DisplayFridge,
    onClick: (DisplayFridge) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    // Determine icon and gradient colors based on fridge type
    val (fridgeIcon, iconGradient) =
        when (fridge.type.lowercase()) {
            "freezer" -> Icons.Default.AcUnit to FridgeTypeColors.FreezerGradient
            "pantry" -> Icons.Default.Inventory to FridgeTypeColors.PantryGradient
            else -> Icons.Default.Kitchen to FridgeTypeColors.FridgeGradient
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(fridge) },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        // Make room for icon
                        .padding(end = 44.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title section
                    Text(
                        text = fridge.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Bottom info section with chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Item count
                        Text(
                            text = "${fridge.itemCount} ${if (fridge.itemCount == 1) "item" else "items"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Divider
                        Box(
                            modifier =
                                Modifier
                                    .size(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.small
                                    )
                        )

                        // Created date
                        Text(
                            text = dateFormatter.format(Date(fridge.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Type icon on the right with shadow and gradient
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                        .drawBehind {
                            drawCircle(
                                brush =
                                    Brush.linearGradient(
                                        colors = iconGradient
                                    )
                            )
                        },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fridgeIcon,
                    contentDescription = "${fridge.type} icon",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Decorative accent bar
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                            Color.Transparent
                                        )
                                )
                        )
            )
        }
    }
}
