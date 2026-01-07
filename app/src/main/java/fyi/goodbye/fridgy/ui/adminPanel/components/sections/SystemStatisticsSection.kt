package fyi.goodbye.fridgy.ui.adminPanel.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.adminPanel.components.items.StatCard

/**
 * Section displaying system-wide statistics in the admin panel.
 *
 * Shows three stat cards for total users, products, and fridges in the system.
 * This provides a quick overview of the app's usage metrics.
 *
 * @param totalUsers The total number of registered users
 * @param totalProducts The total number of products in the crowdsourced database
 * @param totalFridges The total number of fridges created across all users
 */
@Composable
fun SystemStatisticsSection(
    totalUsers: Int,
    totalProducts: Int,
    totalFridges: Int
) {
    Column {
        Text(
            stringResource(R.string.system_statistics),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = stringResource(R.string.users),
                value = totalUsers.toString(),
                icon = Icons.Default.Person,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.products),
                value = totalProducts.toString(),
                icon = Icons.Default.ShoppingCart,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(R.string.fridges),
                value = totalFridges.toString(),
                icon = Icons.Default.Home,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun SystemStatisticsSectionPreview() {
    SystemStatisticsSection(
        totalUsers = 150,
        totalProducts = 1200,
        totalFridges = 75
    )
}
