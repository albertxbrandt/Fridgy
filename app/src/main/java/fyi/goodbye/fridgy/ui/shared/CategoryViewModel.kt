package fyi.goodbye.fridgy.ui.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.repositories.CategoryRepository
import kotlinx.coroutines.Job
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
    application: Application,
    private val categoryRepository: CategoryRepository = CategoryRepository()
) : AndroidViewModel(application) {
    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    return CategoryViewModel(application) as T
                }
            }
    }

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
        categoriesJob = viewModelScope.launch {
            try {
                categoryRepository.getCategories().collect { categories ->
                    _uiState.value = CategoryUiState.Success(categories)
                }
            } catch (e: Exception) {
                _uiState.value = CategoryUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_load_categories))
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
                _uiState.value = CategoryUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_create_category))
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
                _uiState.value = CategoryUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_update_category))
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
                _uiState.value = CategoryUiState.Error(e.message ?: getApplication<Application>().getString(R.string.error_failed_to_delete_category))
            }
        }
    }
}
