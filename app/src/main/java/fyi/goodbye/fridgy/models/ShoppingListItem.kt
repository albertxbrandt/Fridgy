package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Represents an item in a household's shopping list subcollection.
 *
 * @property upc The product UPC code or generated ID for manual entries.
 * @property addedAt Timestamp when the item was added.
 * @property addedBy User ID who added the item.
 * @property quantity Total quantity needed to purchase.
 * @property store Optional store name where item should be purchased.
 * @property checked Whether the item has been checked off (fully or partially).
 * @property obtainedQuantity How many units were actually obtained (for partial pickup).
 * @property obtainedBy Map of userId to quantity obtained by that user (for multi-user shopping).
 * @property targetFridgeId Map of userId to the fridge ID where they want to store their obtained items.
 * @property lastUpdatedBy User ID who last updated the item.
 * @property lastUpdatedAt Timestamp of last update.
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
    val obtainedQuantity: Int? = null,
    val obtainedBy: Map<String, Int> = emptyMap(),
    val targetFridgeId: Map<String, String> = emptyMap(),
    val lastUpdatedBy: String = "",
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    // For manual entries without UPC
    val customName: String = ""
) : Parcelable
