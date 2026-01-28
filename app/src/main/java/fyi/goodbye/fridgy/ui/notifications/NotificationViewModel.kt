package fyi.goodbye.fridgy.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.entities.Notification
import fyi.goodbye.fridgy.repositories.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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
        @ApplicationContext private val context: Context,
        private val repository: NotificationRepository
    ) : ViewModel() {
        companion object {
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
                    Timber.e(error, "Error loading notifications")
                    emit(
                        NotificationUiState.Error(
                            error.message ?: context.getString(R.string.failed_to_load_notifications)
                        )
                    )
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
                    Timber.e(error, "Error loading unread count")
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
                        Timber.d("Notification $notificationId marked as read")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to mark as read")
                        Timber.e(error, "Error marking notification as read")
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
                        Timber.d("All notifications marked as read")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to mark all as read")
                        Timber.e(error, "Error marking all notifications as read")
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
                        Timber.d("Notification $notificationId deleted")
                    }
                    .onFailure { error ->
                        _operationState.value = OperationState.Error("Failed to delete notification")
                        Timber.e(error, "Error deleting notification")
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
