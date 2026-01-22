package fyi.goodbye.fridgy.ui.shared

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.repositories.CategoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing category data and UI state.
 *
 * Provides a reactive stream of categories and handles CRUD operations.
 *
 * @param context Application context for accessing string resources.
 * @param categoryRepository Repository for category operations.
 */
@HiltViewModel
class CategoryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val categoryRepository: CategoryRepository
    ) : ViewModel() {
        sealed interface CategoryUiState {
            data object Loading : CategoryUiState

            data class Success(val categories: List<Category>) : CategoryUiState

            data class Error(val message: String) : CategoryUiState
        }

        private val _uiState = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
        val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

        // Job reference to cancel previous collector if loadCategories() is called again
        private var categoriesJob: Job? = null

        init {
            loadCategories()
        }

        private fun loadCategories() {
            // Cancel any existing collection to prevent multiple collectors
            categoriesJob?.cancel()
            categoriesJob =
                viewModelScope.launch {
                    try {
                        categoryRepository.getCategories().collect { categories ->
                            _uiState.value = CategoryUiState.Success(categories)
                        }
                    } catch (e: Exception) {
                        _uiState.value = CategoryUiState.Error(e.message ?: context.getString(R.string.error_failed_to_load_categories))
                    }
                }
        }

        /**
         * Creates a new category.
         *
         * @param name The display name for the category.
         * @param order The sort order (defaults to [Category.DEFAULT_ORDER]).
         */
        fun createCategory(
            name: String,
            order: Int = Category.DEFAULT_ORDER
        ) {
            viewModelScope.launch {
                try {
                    categoryRepository.createCategory(name, order)
                } catch (e: Exception) {
                    _uiState.value = CategoryUiState.Error(e.message ?: context.getString(R.string.error_failed_to_create_category))
                }
            }
        }

        /**
         * Updates an existing category.
         */
        fun updateCategory(
            categoryId: String,
            name: String,
            order: Int
        ) {
            viewModelScope.launch {
                try {
                    categoryRepository.updateCategory(categoryId, name, order)
                } catch (e: Exception) {
                    _uiState.value = CategoryUiState.Error(e.message ?: context.getString(R.string.error_failed_to_update_category))
                }
            }
        }

        /**
         * Deletes a category.
         */
        fun deleteCategory(categoryId: String) {
            viewModelScope.launch {
                try {
                    categoryRepository.deleteCategory(categoryId)
                } catch (e: Exception) {
                    _uiState.value = CategoryUiState.Error(e.message ?: context.getString(R.string.error_failed_to_delete_category))
                }
            }
        }
    }
