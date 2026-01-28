package fyi.goodbye.fridgy.models.entities

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a push notification stored in Firestore.
 *
 * @property id Firestore document ID
 * @property userId The recipient user ID
 * @property title Notification title
 * @property body Notification body message
 * @property type Type of notification (INVITE, ITEM_ADDED, ITEM_REMOVED, etc.)
 * @property relatedFridgeId Optional fridge ID related to this notification
 * @property relatedItemId Optional item ID related to this notification
 * @property isRead Whether the user has read this notification
 * @property createdAt Timestamp when notification was created
 */
data class Notification(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: NotificationType = NotificationType.GENERAL,
    val relatedFridgeId: String? = null,
    val relatedItemId: String? = null,
    val isRead: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null
)

/**
 * Types of notifications in the app.
 */
enum class NotificationType {
    GENERAL,
    FRIDGE_INVITE,
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_LOW_STOCK,
    MEMBER_JOINED,
    MEMBER_LEFT
}

/**
 * Represents a user's FCM token stored in Firestore.
 *
 * @property userId The user ID this token belongs to
 * @property token The FCM registration token
 * @property updatedAt Last time the token was updated
 */
data class FcmToken(
    val userId: String = "",
    val token: String = "",
    @ServerTimestamp
    val updatedAt: Date? = null
)
