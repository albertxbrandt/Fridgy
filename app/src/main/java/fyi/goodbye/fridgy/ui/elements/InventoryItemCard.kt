package fyi.goodbye.fridgy.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.fridgeInventory.InventoryItem

/**
 * A modern card representing a single inventory item in the grid.
 * Features elevated design with gradient overlay and clean typography.
 * Displays global product info (image, name, brand) and fridge-specific info (quantity).
 */
@Composable
fun InventoryItemCard(
    inventoryItem: InventoryItem,
    onClick: (String) -> Unit
) {
    val item = inventoryItem.item
    val product = inventoryItem.product

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable { onClick(item.upc) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Product Image as Background - prioritize local URI for optimistic updates
            val imageModel = inventoryItem.localImageUri ?: product.imageUrl
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback for items without images
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cd_placeholder_icon), 
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }

            // Enhanced Gradient Scrim
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.4f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
            )

            // Item Information Overlaid
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp)
            ) {
                if (product.brand.isNotBlank()) {
                    Text(
                        text = product.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.quantity_label, item.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
