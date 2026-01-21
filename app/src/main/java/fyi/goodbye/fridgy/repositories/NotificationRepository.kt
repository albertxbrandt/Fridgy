package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import fyi.goodbye.fridgy.models.FcmToken
import fyi.goodbye.fridgy.models.Notification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing push notifications and FCM tokens.
 *
 * Handles:
 * - FCM token retrieval and storage in Firestore
 * - User notification subscriptions
 * - Fetching and marking notifications as read
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for user identification.
 * @param messaging The Messaging instance for FCM operations.
 */
class NotificationRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    companion object {
        private const val TAG = "NotificationRepository"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
        private const val COLLECTION_FCM_TOKENS = "fcmTokens"
    }

    /**
     * Get the current FCM token and save it to Firestore for the authenticated user.
     * Call this when user logs in or app starts.
     */
    suspend fun refreshFcmToken(): Result<String> {
        return try {
            val userId =
                auth.currentUser?.uid
                    ?: return Result.failure(Exception("User not authenticated"))

            val token = messaging.token.await()
            Log.d(TAG, "FCM Token retrieved: $token")

            // Save token to Firestore
            val fcmToken =
                FcmToken(
                    userId = userId,
                    token = token
                )

            firestore.collection(COLLECTION_FCM_TOKENS)
                .document(userId)
                .set(fcmToken)
                .await()

            Log.d(TAG, "FCM Token saved to Firestore")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Subscribe to a topic for receiving notifications.
     * For example, subscribe to "fridge_{fridgeId}" to get notifications for a specific fridge.
     */
    suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return try {
            messaging.subscribeToTopic(topic).await()
            Log.d(TAG, "Subscribed to topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * Unsubscribe from a topic.
     */
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            messaging.unsubscribeFromTopic(topic).await()
            Log.d(TAG, "Unsubscribed from topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from topic: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * Get a real-time stream of notifications for the current user.
     * Sorted by creation time (newest first).
     */
    fun getNotificationsFlow(): Flow<List<Notification>> =
        callbackFlow {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                close(Exception("User not authenticated"))
                return@callbackFlow
            }

            val listenerRegistration =
                firestore.collection(COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50) // Limit to 50 most recent notifications
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening to notifications", error)
                            close(error)
                            return@addSnapshotListener
                        }

                        val notifications =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Notification::class.java)
                            } ?: emptyList()

                        trySend(notifications)
                    }

            awaitClose { listenerRegistration.remove() }
        }

    /**
     * Get unread notification count for the current user.
     */
    fun getUnreadCountFlow(): Flow<Int> =
        callbackFlow {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                close(Exception("User not authenticated"))
                return@callbackFlow
            }

            val listenerRegistration =
                firestore.collection(COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isRead", false)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening to unread count", error)
                            close(error)
                            return@addSnapshotListener
                        }

                        val count = snapshot?.size() ?: 0
                        trySend(count)
                    }

            awaitClose { listenerRegistration.remove() }
        }

    /**
     * Mark a notification as read.
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("isRead", true)
                .await()

            Log.d(TAG, "Notification marked as read: $notificationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read", e)
            Result.failure(e)
        }
    }

    /**
     * Mark all notifications as read for the current user.
     */
    suspend fun markAllAsRead(): Result<Unit> {
        return try {
            val userId =
                auth.currentUser?.uid
                    ?: return Result.failure(Exception("User not authenticated"))

            val unreadNotifications =
                firestore.collection(COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("isRead", false)
                    .get()
                    .await()

            val batch = firestore.batch()
            unreadNotifications.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()

            Log.d(TAG, "All notifications marked as read")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all notifications as read", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a notification.
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .delete()
                .await()

            Log.d(TAG, "Notification deleted: $notificationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification", e)
            Result.failure(e)
        }
    }
}
