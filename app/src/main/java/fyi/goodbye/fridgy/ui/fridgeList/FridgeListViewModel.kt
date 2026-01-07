package fyi.goodbye.fridgy.ui.fridgeList

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.FridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the main list of fridges and user invitations.
 *
 * It coordinates with the [FridgeRepository] to provide real-time updates for both
 * the fridges the user belongs to and any pending invites they have received.
 */
class FridgeListViewModel(
    application: Application,
    private val fridgeRepository: FridgeRepository = FridgeRepository(),
    private val adminRepository: AdminRepository = AdminRepository()
) : AndroidViewModel(application) {
    private val _fridgesUiState = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)

    /** The current state of the fridges list (Loading, Success, or Error). */
    val fridgesUiState: StateFlow<FridgeUiState> = _fridgesUiState.asStateFlow()

    private val _invites = MutableStateFlow<List<DisplayFridge>>(emptyList())

    /** A list of pending fridge invitations received by the current user. */
    val invites: StateFlow<List<DisplayFridge>> = _invites.asStateFlow()

    private val auth = FirebaseAuth.getInstance()

    private val _isAdmin = MutableStateFlow(false)

    /** Indicates if the current user has admin privileges. */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isCreatingFridge = MutableStateFlow(false)

    /** Indicates if a new fridge is currently being created. */
    val isCreatingFridge: StateFlow<Boolean> = _isCreatingFridge.asStateFlow()

    private val createdFridgeError = MutableStateFlow<String?>(null)

    /** Any error message resulting from a failed fridge creation attempt. */
    val createFridgeError: StateFlow<String?> = createdFridgeError.asStateFlow()

    init {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _fridgesUiState.value = FridgeUiState.Error(getApplication<Application>().getString(R.string.error_user_not_logged_in))
        } else {
            // Preload fridges from cache for instant display
            viewModelScope.launch {
                fridgeRepository.preloadFridgesFromCache()
            }

            // Check admin status
            viewModelScope.launch {
                _isAdmin.value = adminRepository.isCurrentUserAdmin()
            }

            // Collect real-time stream of fridges the user is a member of
            viewModelScope.launch {
                fridgeRepository.getFridgesForCurrentUser().collectLatest { fridges ->
                    // Fetch all user data for all fridges
                    val allUserIds = fridges.flatMap { it.members + it.pendingInvites + listOf(it.createdBy) }.distinct()
                    val usersMap = fridgeRepository.getUsersByIds(allUserIds)

                    val displayFridges =
                        fridges.map { fridge ->
                            val memberUsers = fridge.members.mapNotNull { usersMap[it] }
                            val inviteUsers = fridge.pendingInvites.mapNotNull { usersMap[it] }
                            val creatorName = usersMap[fridge.createdBy]?.username ?: getApplication<Application>().getString(R.string.unknown)

                            DisplayFridge(
                                id = fridge.id,
                                name = fridge.name,
                                createdByUid = fridge.createdBy,
                                creatorDisplayName = creatorName,
                                memberUsers = memberUsers,
                                pendingInviteUsers = inviteUsers,
                                createdAt = fridge.createdAt,
                                type = fridge.type
                            )
                        }
                    _fridgesUiState.value = FridgeUiState.Success(displayFridges)
                }
            }

            // Collect real-time stream of pending invites for the current user
            viewModelScope.launch {
                fridgeRepository.getInvitesForCurrentUser().collectLatest { pendingInvites ->
                    // Fetch all user data for invites
                    val allUserIds = pendingInvites.flatMap { it.members + it.pendingInvites + listOf(it.createdBy) }.distinct()
                    val usersMap = fridgeRepository.getUsersByIds(allUserIds)

                    val displayInvites =
                        pendingInvites.map { fridge ->
                            val memberUsers = fridge.members.mapNotNull { usersMap[it] }
                            val inviteUsers = fridge.pendingInvites.mapNotNull { usersMap[it] }
                            val creatorName = usersMap[fridge.createdBy]?.username ?: getApplication<Application>().getString(R.string.unknown)

                            DisplayFridge(
                                id = fridge.id,
                                name = fridge.name,
                                createdByUid = fridge.createdBy,
                                creatorDisplayName = creatorName,
                                memberUsers = memberUsers,
                                pendingInviteUsers = inviteUsers,
                                createdAt = fridge.createdAt,
                                type = fridge.type
                            )
                        }
                    _invites.value = displayInvites
                }
            }
        }
    }

    /**
     * Creates a new fridge with the given name and adds it to the user's list.
     *
     * @param name The name of the new fridge to create.
     * @param type The type of storage (fridge, freezer, pantry).
     * @param location Optional physical location description.
     */
    fun createNewFridge(name: String, type: String = "fridge", location: String = "") {
        createdFridgeError.value = null
        _isCreatingFridge.value = true
        viewModelScope.launch {
            try {
                fridgeRepository.createFridge(name, type, location)
            } catch (e: Exception) {
                createdFridgeError.value = e.message ?: getApplication<Application>().getString(R.string.error_failed_to_create_fridge)
            } finally {
                _isCreatingFridge.value = false
            }
        }
    }

    /**
     * Accepts a pending invitation to join a fridge.
     *
     * @param fridgeId The ID of the fridge to join.
     */
    fun acceptInvite(fridgeId: String) {
        viewModelScope.launch {
            try {
                fridgeRepository.acceptInvite(fridgeId)
            } catch (e: Exception) {
                Log.e("FridgeListVM", "Error accepting invite: ${e.message}")
            }
        }
    }

    /**
     * Declines a pending invitation to join a fridge.
     *
     * @param fridgeId The ID of the fridge invitation to reject.
     */
    fun declineInvite(fridgeId: String) {
        viewModelScope.launch {
            try {
                fridgeRepository.declineInvite(fridgeId)
            } catch (e: Exception) {
                Log.e("FridgeListVM", "Error declining invite: ${e.message}")
            }
        }
    }

    /** Sealed interface representing the UI states for the fridge list. */
    sealed interface FridgeUiState {
        data object Loading : FridgeUiState

        data class Success(val fridges: List<DisplayFridge>) : FridgeUiState

        data class Error(val message: String) : FridgeUiState
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY]!!
                    FridgeListViewModel(app)
                }
            }
    }
}
