package fyi.goodbye.fridgy.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FridgeListViewModel(
    private val fridgeRepository: FridgeRepository = FridgeRepository() // Dependency for fetching fridge data
) : ViewModel() {

    // MutableStateFlow to hold the current UI state of the fridge list (Loading, Success, Error)
    private val _fridgesUiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)
    // Exposed as an immutable StateFlow for the UI to observe
    val fridgesUiState: StateFlow<FridgeUiState> = _fridgesUiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance() // Firebase Auth instance to get current user ID

    private val _isCreatingFridge = MutableStateFlow(false)
    val isCreatingFridge: StateFlow<Boolean> = _isCreatingFridge.asStateFlow()

    private val _createdFridgeError = MutableStateFlow<String?>(null)
    val createFridgeError: StateFlow<String?> = _createdFridgeError.asStateFlow()

    init {
        // Launch a coroutine in the ViewModel's scope to collect fridge data
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                // If no user is logged in, immediately transition to an error state
                _fridgesUiState.value = FridgeUiState.Error("User not logged in. Please log in again.")
                return@launch // Exit the coroutine
            }

            // Collect the stream of fridges from the repository
            // collectLatest ensures that if a new emission happens, the previous collection is cancelled
            fridgeRepository.getFridgesForCurrentUser().collectLatest { fridges ->

                val displayFridges = fridges.map { fridge ->
                    val creatorDisplayName = fridgeRepository.getUserById(fridge.createdBy)?.username ?: "Unknown"

                    DisplayFridge(
                        id = fridge.id,
                        name = fridge.name,
                        createdByUid = fridge.createdBy,
                        creatorDisplayName = creatorDisplayName,
                        members = fridge.members,
                        createdAt = fridge.createdAt
                    )
                }

                // On successful data emission, update the UI state to Success
                _fridgesUiState.value = FridgeUiState.Success(displayFridges)
            }
        }.invokeOnCompletion { cause ->
            // This block is invoked if the coroutine completes or is cancelled with an exception
            if (cause != null) {
                // If an error occurred, update the UI state to Error
                _fridgesUiState.value = FridgeUiState.Error(cause.message ?: "An unexpected error occurred.")
                Log.e("FridgeListVM", "Error collecting fridges from repository: ${cause.message}", cause)
            }
        }
    }

    fun createNewFridge(name: String) {
        _createdFridgeError.value = null
        _isCreatingFridge.value = true

        viewModelScope.launch {
            try {
                fridgeRepository.createFridge(name)
                Log.d("FridgeListVM", "Successfully created new fridge: $name")
            } catch (e: Exception) {
                Log.e("FridgeListVM", "Failed to create fridge: ${e.message}", e)
                _createdFridgeError.value = e.message ?: "Failed to create fridge."
            } finally {
                _isCreatingFridge.value = false
            }
        }
    }

    suspend fun getUserById(id: String): User? {
        return fridgeRepository.getUserById(id)
    }


    /**
     * Sealed interface representing the various UI states for the fridge list.
     * This pattern makes it clear what data is available in each state (e.g., list of fridges in Success).
     */
    sealed interface FridgeUiState {
        // Represents the state where data is being loaded
        data object Loading : FridgeUiState
        // Represents the state where data has been successfully loaded
        data class Success(val fridges: List<DisplayFridge>) : FridgeUiState
        // Represents the state where an error occurred during data loading
        data class Error(val message: String) : FridgeUiState
    }
}