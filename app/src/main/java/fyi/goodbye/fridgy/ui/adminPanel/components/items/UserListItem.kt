package fyi.goodbye.fridgy.ui.adminPanel.components.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.display.AdminUserDisplay

/**
 * A list item component for displaying user information in the admin panel.
 *
 * Shows the user's profile icon, username, email, and provides edit/delete actions.
 * Used in the "Recent Users" section of the admin dashboard.
 *
 * @param user The user data to display (username, email, etc.)
 * @param onEdit Callback invoked when the edit button is tapped
 * @param onDelete Callback invoked when the delete button is tapped
 */
@Composable
fun UserListItem(
    user: AdminUserDisplay,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AdminListItem(
        primaryText = user.username,
        secondaryText = user.email,
        leadingIcon = Icons.Default.Person,
        onEdit = onEdit,
        onDelete = onDelete,
        editContentDescription = stringResource(R.string.cd_edit_user),
        deleteContentDescription = stringResource(R.string.cd_delete_user)
    )
}

@Preview
@Composable
fun UserListItemPreview() {
    UserListItem(
        user =
            AdminUserDisplay(
                uid = "11",
                username = "john_doe",
                email = "john_doe@example.com",
                createdAt = null
            ),
        onEdit = {},
        onDelete = {}
    )
}
