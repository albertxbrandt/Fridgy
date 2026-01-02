package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

/**
 * Represents a food category in the Fridgy application.
 *
 * Categories are stored in Firestore and can be managed through the admin panel.
 *
 * @property id The Firestore document ID (auto-generated).
 * @property name The display name of the category (e.g., "Dairy", "Meat", "Produce").
 * @property order The sort order for displaying categories (lower numbers appear first).
 * @property createdAt The timestamp when this category was created.
 */
data class Category(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val order: Int = 999,
    val createdAt: Long = System.currentTimeMillis()
)
