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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
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
    val (fridgeIcon, iconGradient) = when (fridge.type.lowercase()) {
        "freezer" -> Icons.Default.AcUnit to listOf(Color(0xFF81D4FA), Color(0xFF4FC3F7), Color(0xFF29B6F6)) // Light blue gradient
        "pantry" -> Icons.Default.Inventory to listOf(Color(0xFFFFB74D), Color(0xFFFF9800), Color(0xFFF57C00)) // Orange gradient
        else -> Icons.Default.Kitchen to listOf(Color(0xFF42A5F5), Color(0xFF1E88E5), Color(0xFF1565C0)) // Blue gradient
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick(fridge) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .padding(end = 44.dp) // Make room for icon
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
                    // Items chip
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                    ) {
                        Text(
                            text = "${fridge.memberUsers.size} ${if (fridge.memberUsers.size == 1) "member" else "members"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    
                    // Divider
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.shapes.small
                            )
                    )
                    
                    // Creator info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = fridge.creatorDisplayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormatter.format(Date(fridge.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            }
            
            // Type icon on the right with shadow and gradient
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(64.dp)
                    .shadow(8.dp, CircleShape)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.linearGradient(
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
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
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
