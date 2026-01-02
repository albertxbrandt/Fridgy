package fyi.goodbye.fridgy.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.User
import fyi.goodbye.fridgy.repositories.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the admin panel.
 * Manages fetching and displaying system-wide statistics and data.
 */
class AdminPanelViewModel(
    application: Application,
    private val adminRepository: AdminRepository = AdminRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Loading)
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAdminData()
    }

    private fun loadAdminData() {
        viewModelScope.launch {
            try {
                _uiState.value = AdminUiState.Loading
                
                val isAdmin = adminRepository.isCurrentUserAdmin()
                if (!isAdmin) {
                    _uiState.value = AdminUiState.Unauthorized
                    return@launch
                }
                
                val users = adminRepository.getAllUsers()
                val products = adminRepository.getAllProducts()
                val fridges = adminRepository.getAllFridges()
                
                _uiState.value = AdminUiState.Success(
                    totalUsers = users.size,
                    totalProducts = products.size,
                    totalFridges = fridges.size,
                    users = users,
                    products = products,
                    fridges = fridges
                )
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error("Failed to load admin data: ${e.message}")
            }
        }
    }

    fun refresh() {
        loadAdminData()
    }
    
    /**
     * Deletes a user from the system.
     */
    fun deleteUser(userId: String) {
        viewModelScope.launch {
            try {
                val success = adminRepository.deleteUser(userId)
                if (success) {
                    refresh() // Reload data after deletion
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error("Failed to delete user: ${e.message}")
            }
        }
    }
    
    /**
     * Updates a user's information.
     */
    fun updateUser(userId: String, username: String, email: String) {
        viewModelScope.launch {
            try {
                val success = adminRepository.updateUser(userId, username, email)
                if (success) {
                    refresh() // Reload data after update
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error("Failed to update user: ${e.message}")
            }
        }
    }
    
    /**
     * Deletes a product from the database.
     */
    fun deleteProduct(upc: String) {
        viewModelScope.launch {
            try {
                val success = adminRepository.deleteProduct(upc)
                if (success) {
                    refresh() // Reload data after deletion
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error("Failed to delete product: ${e.message}")
            }
        }
    }
    
    /**
     * Updates a product's information.
     */
    fun updateProduct(upc: String, name: String, brand: String, category: String) {
        viewModelScope.launch {
            try {
                val success = adminRepository.updateProduct(upc, name, brand, category)
                if (success) {
                    refresh() // Reload data after update
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error("Failed to update product: ${e.message}")
            }
        }
    }

    sealed interface AdminUiState {
        data object Loading : AdminUiState
        data object Unauthorized : AdminUiState
        data class Success(
            val totalUsers: Int,
            val totalProducts: Int,
            val totalFridges: Int,
            val users: List<User>,
            val products: List<Product>,
            val fridges: List<Fridge>
        ) : AdminUiState
        data class Error(val message: String) : AdminUiState
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                AdminPanelViewModel(application)
            }
        }
    }
}
