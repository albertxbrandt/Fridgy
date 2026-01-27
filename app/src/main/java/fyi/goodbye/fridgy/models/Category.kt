package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Represents a food category in the Fridgy application.
 *
 * Categories are stored in Firestore and can be managed through the admin panel.
 *
 * @property id The Firestore document ID (auto-generated).
 * @property name The display name of the category (e.g., "Dairy", "Meat", "Produce").
 * @property order The sort order for displaying categories (lower numbers appear first).
 * @property createdAt The timestamp when this category was created (managed by Firestore).
 */
@Parcelize
data class Category(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val order: Int = DEFAULT_ORDER,
    @ServerTimestamp
    val createdAt: Date? = null
) : Parcelable {
    companion object {
        /** Default sort order for new categories. */
        const val DEFAULT_ORDER = 999
    }
}
