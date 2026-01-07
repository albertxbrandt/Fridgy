package fyi.goodbye.fridgy.ui.adminPanel.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.ui.adminPanel.components.items.UserListItem
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

/**
 * Section displaying recent user registrations in the admin panel.
 *
 * Shows up to 10 of the most recent users with their username and email.
 * Each user has edit and delete actions available.
 *
 * @param users The list of users to display (sorted by most recent)
 * @param onEditUser Callback invoked when edit is tapped for a user
 * @param onDeleteUser Callback invoked when delete is tapped for a user
 */
@Composable
fun RecentUsersSection(
    users: List<AdminUserDisplay>,
    onEditUser: (AdminUserDisplay) -> Unit,
    onDeleteUser: (AdminUserDisplay) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.recent_users),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )

        users.take(10).forEach { user ->
            UserListItem(
                user = user,
                onEdit = { onEditUser(user) },
                onDelete = { onDeleteUser(user) }
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun RecentUsersSectionPreview() {
    FridgyTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RecentUsersSection(
                users =
                    listOf(
                        AdminUserDisplay(
                            uid = "user1",
                            username = "johndoe",
                            email = "john.doe@example.com",
                            createdAt = System.currentTimeMillis()
                        ),
                        AdminUserDisplay(
                            uid = "user2",
                            username = "janedoe",
                            email = "jane.doe@example.com",
                            createdAt = System.currentTimeMillis() - 86400000
                        ),
                        AdminUserDisplay(
                            uid = "user3",
                            username = "bobsmith",
                            email = "bob.smith@example.com",
                            createdAt = System.currentTimeMillis() - 172800000
                        ),
                        AdminUserDisplay(
                            uid = "user4",
                            username = "alicejones",
                            email = "alice.jones@example.com",
                            createdAt = System.currentTimeMillis() - 259200000
                        )
                    ),
                onEditUser = {},
                onDeleteUser = {}
            )
        }
    }
}
