package fyi.goodbye.fridgy.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.repositories.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing category data and UI state.
 *
 * Provides a reactive stream of categories and handles CRUD operations.
 */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository = CategoryRepository()
) : ViewModel() {
    sealed interface CategoryUiState {
        data object Loading : CategoryUiState

        data class Success(val categories: List<Category>) : CategoryUiState

        data class Error(val message: String) : CategoryUiState
    }

    private val _uiState = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                categoryRepository.getCategories().collect { categories ->
                    _uiState.value = CategoryUiState.Success(categories)
                }
            } catch (e: Exception) {
                _uiState.value = CategoryUiState.Error(e.message ?: "Failed to load categories")
            }
        }
    }

    /**
     * Creates a new category.
     */
    fun createCategory(
        name: String,
        order: Int = 999
    ) {
        viewModelScope.launch {
            try {
                categoryRepository.createCategory(name, order)
            } catch (e: Exception) {
                _uiState.value = CategoryUiState.Error(e.message ?: "Failed to create category")
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
                _uiState.value = CategoryUiState.Error(e.message ?: "Failed to update category")
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
                _uiState.value = CategoryUiState.Error(e.message ?: "Failed to delete category")
            }
        }
    }
}
