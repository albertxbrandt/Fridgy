package fyi.goodbye.fridgy.ui.adminPanel.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.ui.adminPanel.components.items.CategoryListItem
import fyi.goodbye.fridgy.ui.shared.CategoryViewModel
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue

/**
 * Section for managing product categories in the admin panel.
 *
 * Displays the list of categories with their sort order, and provides
 * add, edit, and delete functionality. Shows loading/error states based
 * on the category UI state.
 *
 * @param categoryState The current state of category loading (Loading, Success, or Error)
 * @param onAddCategory Callback invoked when the add category button is tapped
 * @param onEditCategory Callback invoked when edit is tapped for a category
 * @param onDeleteCategory Callback invoked when delete is tapped for a category
 */
@Composable
fun CategoriesSection(
    categoryState: CategoryViewModel.CategoryUiState,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.food_categories),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = FridgyDarkBlue
            )
            IconButton(onClick = onAddCategory) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_category)
                )
            }
        }

        when (categoryState) {
            is CategoryViewModel.CategoryUiState.Success -> {
                categoryState.categories.forEach { category ->
                    CategoryListItem(
                        category = category,
                        onEdit = { onEditCategory(category) },
                        onDelete = { onDeleteCategory(category) }
                    )
                }
            }
            is CategoryViewModel.CategoryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is CategoryViewModel.CategoryUiState.Error -> {
                Text(
                    stringResource(R.string.error_loading_categories, categoryState.message),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
fun CategoriesSectionPreview() {
    CategoriesSection(
        categoryState =
            CategoryViewModel.CategoryUiState.Success(
                categories =
                    listOf(
                        Category(id = "1", name = "Dairy", order = 1),
                        Category(id = "2", name = "Vegetables", order = 2),
                        Category(id = "3", name = "Fruits", order = 3)
                    )
            ),
        onAddCategory = {},
        onEditCategory = {},
        onDeleteCategory = {}
    )
}
