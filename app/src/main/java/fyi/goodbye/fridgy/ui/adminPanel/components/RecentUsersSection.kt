package fyi.goodbye.fridgy.ui.adminPanel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme

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
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FridgyDarkBlue,
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
                users = listOf(
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
