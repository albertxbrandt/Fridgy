package fyi.goodbye.fridgy.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.entities.FcmToken
import fyi.goodbye.fridgy.models.entities.Notification
import fyi.goodbye.fridgy.models.entities.NotificationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import timber.log.Timber

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
            Timber.d("FCM Token retrieved: $token")

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

            Timber.d("FCM Token saved to Firestore")
            Result.success(token)
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing FCM token")
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
            Timber.d("Subscribed to topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error subscribing to topic: $topic")
            Result.failure(e)
        }
    }

    /**
     * Unsubscribe from a topic.
     */
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            messaging.unsubscribeFromTopic(topic).await()
            Timber.d("Unsubscribed from topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error unsubscribing from topic: $topic")
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
                    .whereEqualTo(FirestoreFields.USER_ID, userId)
                    .orderBy(FirestoreFields.CREATED_AT, Query.Direction.DESCENDING)
                    .limit(50) // Limit to 50 most recent notifications
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Error listening to notifications")
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
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

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
                    .whereEqualTo(FirestoreFields.USER_ID, userId)
                    .whereEqualTo(FirestoreFields.IS_READ, false)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Error listening to unread count")
                            close(error)
                            return@addSnapshotListener
                        }

                        val count = snapshot?.size() ?: 0
                        trySend(count)
                    }

            awaitClose { listenerRegistration.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate count emissions

    /**
     * Mark a notification as read.
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update(FirestoreFields.IS_READ, true)
                .await()

            Timber.d("Notification marked as read: $notificationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error marking notification as read")
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
                    .whereEqualTo(FirestoreFields.USER_ID, userId)
                    .whereEqualTo(FirestoreFields.IS_READ, false)
                    .get()
                    .await()

            val batch = firestore.batch()
            unreadNotifications.documents.forEach { doc ->
                batch.update(doc.reference, FirestoreFields.IS_READ, true)
            }
            batch.commit().await()

            Timber.d("All notifications marked as read")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error marking all notifications as read")
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

            Timber.d("Notification deleted: $notificationId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting notification")
            Result.failure(e)
        }
    }

    /**
     * Send an in-app notification to a specific user.
     * This creates a notification document in Firestore.
     *
     * @param userId The user to send the notification to
     * @param title Notification title
     * @param body Notification body message
     * @param type Notification type
     * @param relatedFridgeId Optional related fridge ID
     * @param relatedItemId Optional related item ID
     */
    suspend fun sendInAppNotification(
        userId: String,
        title: String,
        body: String,
        type: NotificationType = NotificationType.GENERAL,
        relatedFridgeId: String? = null,
        relatedItemId: String? = null
    ): Result<Unit> {
        return try {
            // Firestore will set createdAt via @ServerTimestamp
            val notification =
                Notification(
                    userId = userId,
                    title = title,
                    body = body,
                    type = type,
                    relatedFridgeId = relatedFridgeId,
                    relatedItemId = relatedItemId,
                    isRead = false,
                    createdAt = null
                )

            firestore.collection(COLLECTION_NOTIFICATIONS)
                .add(notification)
                .await()

            Timber.d("In-app notification sent to user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error sending in-app notification")
            Result.failure(e)
        }
    }

    /**
     * Get FCM tokens for a list of user IDs.
     * Returns a map of userId to FCM token.
     */
    suspend fun getFcmTokensForUsers(userIds: List<String>): Map<String, String> {
        return try {
            if (userIds.isEmpty()) return emptyMap()

            // Firestore 'in' queries are limited to 10 items, so batch the requests
            val tokens = mutableMapOf<String, String>()

            userIds.chunked(10).forEach { batch ->
                val snapshot =
                    firestore.collection(COLLECTION_FCM_TOKENS)
                        .whereIn(FirestoreFields.USER_ID, batch)
                        .get()
                        .await()

                snapshot.documents.forEach { doc ->
                    doc.toObject(FcmToken::class.java)?.let { fcmToken ->
                        tokens[fcmToken.userId] = fcmToken.token
                    }
                }
            }

            Timber.d("Retrieved ${tokens.size} FCM tokens for ${userIds.size} users")
            tokens
        } catch (e: Exception) {
            Timber.e(e, "Error fetching FCM tokens")
            emptyMap()
        }
    }
}
