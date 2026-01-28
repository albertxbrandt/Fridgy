package fyi.goodbye.fridgy.ui.adminPanel.components.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.entities.Category

/**
 * A list item component for displaying category information in the admin panel.
 *
 * Shows the category's name, sort order, and provides edit/delete actions.
 * Used in the "Categories" section of the admin dashboard.
 *
 * @param category The category data to display (name, order)
 * @param onEdit Callback invoked when the edit button is tapped
 * @param onDelete Callback invoked when the delete button is tapped
 */
@Composable
fun CategoryListItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AdminListItem(
        primaryText = category.name,
        secondaryText = stringResource(R.string.order_label, category.order),
        onEdit = onEdit,
        onDelete = onDelete,
        editContentDescription = stringResource(R.string.cd_edit_category),
        deleteContentDescription = stringResource(R.string.cd_delete_category)
    )
}

@Preview
@Composable
fun CategoryListItemPreview() {
    val sampleCategory =
        Category(
            id = "1",
            name = "Fruits",
            order = 1
        )
    CategoryListItem(
        category = sampleCategory,
        onEdit = {},
        onDelete = {}
    )
}
