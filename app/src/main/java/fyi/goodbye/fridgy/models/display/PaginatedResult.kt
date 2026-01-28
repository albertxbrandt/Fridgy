package fyi.goodbye.fridgy.models.display

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Represents a paginated result set.
 * @property items The list of items in this page
 * @property lastDocument The last document in this page, used as cursor for next page
 * @property hasMore Whether there are more items available
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val lastDocument: DocumentSnapshot?,
    val hasMore: Boolean
)
