package fyi.goodbye.fridgy.ui.adminPanel.components.items

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.theme.FridgyPrimary
import fyi.goodbye.fridgy.ui.theme.FridgyWhite

/**
 * A generic list item component for displaying data in the admin panel.
 *
 * Provides a consistent layout with an optional leading icon, primary/secondary text,
 * and edit/delete actions. Used across Users, Products, and Categories sections.
 *
 * @param primaryText The main text to display (e.g., username, product name, category name)
 * @param secondaryText The subtitle text to display (e.g., email, brand/category, order)
 * @param leadingIcon Optional icon to display at the start of the item
 * @param onEdit Callback invoked when the edit button is tapped
 * @param onDelete Callback invoked when the delete button is tapped
 * @param editContentDescription Content description for the edit button
 * @param deleteContentDescription Content description for the delete button
 */
@Composable
fun AdminListItem(
    primaryText: String,
    secondaryText: String,
    leadingIcon: ImageVector? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editContentDescription: String = stringResource(R.string.cd_edit),
    deleteContentDescription: String = stringResource(R.string.cd_delete)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = FridgyWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = FridgyPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Text(
                        primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        secondaryText,
                        fontSize = if (leadingIcon != null) 14.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = editContentDescription,
                        tint = FridgyPrimary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = deleteContentDescription,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PreviewAdminListItem() {
    Column(modifier = Modifier.padding(16.dp)) {
        AdminListItem(
            primaryText = "John Doe",
            secondaryText = "john.doe@example.com",
            leadingIcon = Icons.Default.Person,
            onEdit = {},
            onDelete = {}
        )
    }
}
