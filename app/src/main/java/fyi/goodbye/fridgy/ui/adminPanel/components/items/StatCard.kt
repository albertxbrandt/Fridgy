package fyi.goodbye.fridgy.ui.adminPanel.components.items

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.ui.theme.FridgyPrimary
import fyi.goodbye.fridgy.ui.theme.FridgyWhite

/**
 * A card component for displaying a single statistic in the admin panel.
 *
 * Displays an icon, numeric value, and label in a visually prominent card
 * with the app's primary color scheme.
 *
 * @param title The label describing the statistic (e.g., "Total Users")
 * @param value The numeric value to display (e.g., "1,234")
 * @param icon The icon representing this statistic type
 * @param modifier Optional modifier for the card container
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FridgyPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = FridgyWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyWhite
            )
            Text(
                title,
                fontSize = 12.sp,
                color = FridgyWhite
            )
        }
    }
}

@Preview
@Composable
fun StatCardPreview() {
    StatCard(
        title = "Total Products",
        value = "1,234",
        icon = Icons.Default.ShoppingCart,
        modifier = Modifier.padding(16.dp)
    )
}
