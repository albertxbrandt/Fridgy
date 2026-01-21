package fyi.goodbye.fridgy.ui.adminPanel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.repositories.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the admin panel.
 * Manages fetching and displaying system-wide statistics and data.
 *
 * @param context Application context for accessing string resources.
 * @param adminRepository Repository for admin operations.
 */
@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminRepository: AdminRepository
) : ViewModel() {
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

                // Ensure we're not setting success state with all empty data
                // which could indicate a loading failure
                if (users.isEmpty() && products.isEmpty() && fridges.isEmpty()) {
                    Log.w("AdminPanelViewModel", "All collections returned empty - possible loading error")
                    _uiState.value = AdminUiState.Error("No data available. The system may be empty or there was a loading error.")
                    return@launch
                }

                _uiState.value =
                    AdminUiState.Success(
                        totalUsers = users.size,
                        totalProducts = products.size,
                        totalFridges = fridges.size,
                        users = users,
                        products = products,
                        fridges = fridges
                    )
            } catch (e: Exception) {
                Log.e("AdminPanelViewModel", "Error loading admin data", e)
                _uiState.value = AdminUiState.Error(context.getString(R.string.error_failed_to_load_admin_data, e.message ?: ""))
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
                _uiState.value = AdminUiState.Error(context.getString(R.string.error_failed_to_delete_user, e.message ?: ""))
            }
        }
    }

    /**
     * Updates a user's information.
     */
    fun updateUser(
        userId: String,
        username: String,
        email: String
    ) {
        viewModelScope.launch {
            try {
                val success = adminRepository.updateUser(userId, username, email)
                if (success) {
                    refresh() // Reload data after update
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error(context.getString(R.string.error_failed_to_update_user, e.message ?: ""))
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
                _uiState.value = AdminUiState.Error(context.getString(R.string.error_failed_to_delete_product, e.message ?: ""))
            }
        }
    }

    /**
     * Updates a product's information.
     */
    fun updateProduct(
        upc: String,
        name: String,
        brand: String,
        category: String
    ) {
        viewModelScope.launch {
            try {
                val success = adminRepository.updateProduct(upc, name, brand, category)
                if (success) {
                    refresh()
                }
            } catch (e: Exception) {
                _uiState.value = AdminUiState.Error(context.getString(R.string.error_failed_to_update_product, e.message ?: ""))
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
            val users: List<AdminUserDisplay>,
            val products: List<Product>,
            val fridges: List<Fridge>
        ) : AdminUiState

        data class Error(val message: String) : AdminUiState
    }
}
