package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Represents an item in a fridge's shopping list subcollection.
 *
 * @property upc The product UPC code or generated ID for manual entries.
 * @property addedAt Timestamp when the item was added.
 * @property addedBy User ID who added the item.
 * @property customName Optional custom name for manual entries (when product not in database).
 */
@Parcelize
data class ShoppingListItem(
    @DocumentId
    val upc: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val addedBy: String = "",
    val quantity: Int = 1,
    val store: String = "",
    val checked: Boolean = false,
    val customName: String = "" // For manual entries without UPC
) : Parcelable
