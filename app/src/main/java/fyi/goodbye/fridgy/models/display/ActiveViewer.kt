package fyi.goodbye.fridgy.models.display

/**
 * Data class representing an active viewer of the shopping list.
 *
 * @property userId The user's Firebase Auth UID
 * @property username The user's display username
 * @property lastSeenTimestamp The timestamp (milliseconds) when user last viewed the list
 */
data class ActiveViewer(
    val userId: String,
    val username: String,
    val lastSeenTimestamp: Long
)
