package fyi.goodbye.fridgy.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fyi.goodbye.fridgy.models.Notification
import fyi.goodbye.fridgy.repositories.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing notification state and operations.
 *
 * Follows MVVM pattern:
 * - Exposes UI state via StateFlow
 * - Handles business logic for notification operations
 * - Uses [NotificationRepository] for data access
 *
 * @param repository The notification repository for data operations.
 */
@HiltViewModel
class NotificationViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository
    ) : ViewModel() {
        companion object {
            private const val TAG = "NotificationViewModel"
        }

        /**
         * Sealed interface representing the UI state for notifications.
         */
        sealed interface NotificationUiState {
            data object Loading : NotificationUiState

            data class Success(val notifications: List<Notification>) : NotificationUiState

            data class Error(val message: String) : NotificationUiState
        }

        /**
         * UI state for the notifications screen.
         */
        val uiState: StateFlow<NotificationUiState> =
            repository.getNotificationsFlow()
                .map<List<Notification>, NotificationUiState> { notifications ->
                    NotificationUiState.Success(notifications)
                }
                .catch { error ->
                    Log.e(TAG, "Error loading notifications", error)
                    emit(
                        NotificationUiState.Error(error.message ?: "Failed to load notifications")
                    ) // TODO: Pass context for string resources
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = NotificationUiState.Loading
                )

        /**
         * Unread notification count for badge display.
         */
        val unreadCount: StateFlow<Int> =
            repository.getUnreadCountFlow()
                .catch { error ->
                    Log.e(TAG, "Error loading unread count", error)
                    emit(0)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0
                )

        private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
        val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

        sealed interface OperationState {
            data object Idle : OperationState

            data object Processing : OperationState

            data class Success(val message: String) : OperationState

            data class Error(val message: String) : OperationState
        }

        /**
         * Mark a notification as read.
         */
        fun markAsRead(notificationId: String) {
            viewModelScope.launch {
                _operationState.value = OperationState.Processing
                repository.markAsRead(notificationId)
                    .onSuccess {
                        _operationState.value = OperationState.Success("Marked as read")
                        Log.d(TAG, "Notification $notificationId marked as read")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to mark as read")
                        Log.e(TAG, "Error marking notification as read", error)
                    }
            }
        }

        /**
         * Mark all notifications as read.
         */
        fun markAllAsRead() {
            viewModelScope.launch {
                _operationState.value = OperationState.Processing
                repository.markAllAsRead()
                    .onSuccess {
                        _operationState.value = OperationState.Success("All marked as read")
                        Log.d(TAG, "All notifications marked as read")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to mark all as read")
                        Log.e(TAG, "Error marking all notifications as read", error)
                    }
            }
        }

        /**
         * Delete a notification.
         */
        fun deleteNotification(notificationId: String) {
            viewModelScope.launch {
                _operationState.value = OperationState.Processing
                repository.deleteNotification(notificationId)
                    .onSuccess {
                        _operationState.value = OperationState.Success("Notification deleted")
                        Log.d(TAG, "Notification $notificationId deleted")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to delete notification")
                        Log.e(TAG, "Error deleting notification", error)
                    }
            }
        }

        /**
         * Reset operation state to idle.
         */
        fun resetOperationState() {
            _operationState.value = OperationState.Idle
        }
    }
