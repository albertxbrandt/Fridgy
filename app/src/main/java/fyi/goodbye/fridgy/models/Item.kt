package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

/**
 * Data model representing a grocery item stored within a fridge.
 *
 * @property id The unique Firestore document ID for this item.
 * @property upc The Universal Product Code (barcode) scanned for this item.
 * @property name The display name of the product (e.g., "Whole Milk").
 * @property imageUrl The URL of the product's image.
 * @property quantity The current number of units of this item in the fridge.
 * @property addedBy The User ID of the person who originally added this item.
 * @property addedAt The timestamp (ms) when the item was first created.
 * @property lastUpdatedBy The User ID of the person who last modified this item.
 * @property lastUpdatedAt The timestamp (ms) when the item was last modified.
 */
data class Item(
    @DocumentId
    val id: String = "",
    val upc: String = "",
    val name: String = "Unknown Product",
    val imageUrl: String? = null,
    val quantity: Int = 1,
    val addedBy: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastUpdatedBy: String = "",
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
