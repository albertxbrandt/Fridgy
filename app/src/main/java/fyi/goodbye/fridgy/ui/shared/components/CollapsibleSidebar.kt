package fyi.goodbye.fridgy.ui.shared.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Data class representing a menu item in the sidebar.
 *
 * @param icon The icon to display for this menu item
 * @param label The text label for this menu item
 * @param onClick The action to perform when this item is clicked
 */
data class SidebarMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * A collapsible right-side navigation sidebar that pushes content to the left.
 *
 * @param isOpen Whether the sidebar is currently open
 * @param onDismiss Callback when the sidebar should be closed
 * @param menuItems List of menu items to display in the sidebar
 * @param content The main content that will be pushed left when sidebar opens
 */
@Composable
fun CollapsibleSidebar(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    menuItems: List<SidebarMenuItem>,
    content: @Composable () -> Unit
) {
    val sidebarWidth = 280.dp
    val contentOffset by animateDpAsState(
        targetValue = if (isOpen) -sidebarWidth else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "contentOffset"
    )
    val sidebarOffset by animateDpAsState(
        targetValue = if (isOpen) 0.dp else sidebarWidth,
        animationSpec = tween(durationMillis = 300),
        label = "sidebarOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content - pushed left when sidebar opens
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(x = contentOffset)
        ) {
            content()
        }

        // Sidebar - slides in from right
        Surface(
            modifier =
                Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .offset(x = sidebarOffset),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sidebar Header
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Menu",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Menu Items
                menuItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(text = item.label)
                        },
                        selected = false,
                        onClick = {
                            item.onClick()
                            onDismiss()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Scrim overlay - dismiss when clicked
        if (isOpen) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset(x = contentOffset)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(onClick = onDismiss)
            )
        }
    }
}
