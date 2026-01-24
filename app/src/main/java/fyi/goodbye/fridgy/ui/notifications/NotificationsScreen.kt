package fyi.goodbye.fridgy.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Notification
import fyi.goodbye.fridgy.ui.theme.FridgyPrimary
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying user notifications with real-time updates.
 *
 * Features:
 * - Real-time notification list from Firestore
 * - Swipe to delete functionality
 * - Mark as read on tap
 * - Mark all as read button
 * - Unread indicator with blue dot
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onNotificationClick: (Notification) -> Unit = {},
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(context.getString(R.string.notifications))
                        if (unreadCount > 0) {
                            Text(
                                context.getString(R.string.unread_count, unreadCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, context.getString(R.string.back))
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(Icons.Default.DoneAll, context.getString(R.string.mark_all_as_read))
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = FridgyPrimary,
                        titleContentColor = FridgyWhite,
                        navigationIconContentColor = FridgyWhite,
                        actionIconContentColor = FridgyWhite
                    )
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is NotificationViewModel.NotificationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = FridgyPrimary)
                    }
                }

                is NotificationViewModel.NotificationUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                context.getString(R.string.error_loading_notifications),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                is NotificationViewModel.NotificationUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.padding(8.dp))
                                Text(
                                    context.getString(R.string.no_notifications_yet),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = state.notifications,
                                key = { it.id }
                            ) { notification ->
                                // OPTIMIZATION: Stable callback references
                                val onDelete = remember(notification.id) {
                                    { viewModel.deleteNotification(notification.id) }
                                }
                                val onClick = remember(notification.id, notification.type, notification.isRead) {
                                    {
                                        // Don't navigate for fridge invites - use Accept/Decline buttons instead
                                        if (notification.type.name != "FRIDGE_INVITE") {
                                            if (!notification.isRead) {
                                                viewModel.markAsRead(notification.id)
                                            }
                                            onNotificationClick(notification)
                                        }
                                    }
                                }
                                val onAcceptInvite = remember(notification.id) {
                                    { notif: Notification ->
                                        notif.relatedFridgeId?.let { fridgeId ->
                                            viewModel.acceptFridgeInvite(fridgeId, notif.id)
                                        }
                                    }
                                }
                                val onDeclineInvite = remember(notification.id) {
                                    { notif: Notification ->
                                        notif.relatedFridgeId?.let { fridgeId ->
                                            viewModel.declineFridgeInvite(fridgeId, notif.id)
                                        }
                                    }
                                }
                                SwipeToDeleteNotificationItem(
                                    notification = notification,
                                    onDelete = onDelete,
                                    onClick = onClick,
                                    onAcceptInvite = onAcceptInvite,
                                    onDeclineInvite = onDeclineInvite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteNotificationItem(
    notification: Notification,
    onDelete: (Notification) -> Unit,
    onClick: (Notification) -> Unit,
    onAcceptInvite: (Notification) -> Unit,
    onDeclineInvite: (Notification) -> Unit
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                    onDelete(notification)
                    true
                } else {
                    false
                }
            }
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        NotificationItem(
            notification = notification,
            onClick = { onClick(notification) },
            onAcceptInvite = { onAcceptInvite(notification) },
            onDeclineInvite = { onDeclineInvite(notification) }
        )
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit,
    onAcceptInvite: () -> Unit,
    onDeclineInvite: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (notification.isRead) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (notification.isRead) 0.dp else 2.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread indicator
            if (!notification.isRead) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(FridgyPrimary)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    formatTimestamp(notification.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // Show accept/decline buttons for fridge invites
                if (notification.type.name == "FRIDGE_INVITE") {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onAcceptInvite,
                            modifier = Modifier.weight(1f),
                            colors =
                                androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = FridgyPrimary
                                )
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept")
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = onDeclineInvite,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decline")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Date?): String {
    if (timestamp == null) return ""

    val now = System.currentTimeMillis()
    val time = timestamp.time
    val diff = now - time

    return when {
        diff < 60_000 -> "Just now" // Less than 1 minute
        diff < 3_600_000 -> "${diff / 60_000}m ago" // Less than 1 hour
        diff < 86_400_000 -> "${diff / 3_600_000}h ago" // Less than 24 hours
        diff < 604_800_000 -> "${diff / 86_400_000}d ago" // Less than 7 days
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(timestamp)
    }
}
