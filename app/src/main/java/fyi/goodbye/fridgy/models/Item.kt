package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a grocery item stored within a specific fridge.
 * This model only stores fridge-specific metadata. Product details are fetched
 * separately using the document ID (which is the UPC).
 *
 * @property upc The unique Firestore document ID for this item (the UPC barcode).
 * @property quantity The current number of units of this item in the fridge.
 * @property addedBy The User ID of the person who originally added this item.
 * @property addedAt The timestamp (ms) when the item was first created.
 * @property lastUpdatedBy The User ID of the person who last modified this item.
 * @property lastUpdatedAt The timestamp (ms) when the item was last modified.
 */
@Parcelize
data class Item(
    @DocumentId
    val upc: String = "",
    val quantity: Int = 1,
    val addedBy: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastUpdatedBy: String = "",
    val lastUpdatedAt: Long = System.currentTimeMillis()
) : Parcelable
