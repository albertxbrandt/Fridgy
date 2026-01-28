package fyi.goodbye.fridgy.models.entities

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data model representing a single item instance in a fridge inventory.
 *
 * Items are now stored as individual instances rather than aggregated by UPC,
 * allowing each instance to have its own expiration date. This enables tracking
 * multiple units of the same product with different expiration dates.
 *
 * Items are stored in a subcollection under fridges: /fridges/{fridgeId}/items/{itemId}
 * Firebase security rules fetch the householdId from the parent fridge document.
 *
 * @property id Unique identifier for this item instance (Firestore auto-generated)
 * @property upc Product barcode/UPC for linking to product information
 * @property expirationDate Expiration date in milliseconds since epoch (null = no expiration)
 * @property addedBy The User ID of the person who originally added this item
 * @property addedAt Server timestamp when the item was first created
 * @property lastUpdatedBy The User ID of the person who last modified this item
 * @property lastUpdatedAt Server timestamp when the item was last modified
 */
@Parcelize
data class Item(
    @DocumentId
    val id: String = "",
    val upc: String = "",
    val expirationDate: Long? = null,
    val addedBy: String = "",
    @ServerTimestamp
    val addedAt: Date? = null,
    val lastUpdatedBy: String = "",
    @ServerTimestamp
    val lastUpdatedAt: Date? = null
) : Parcelable {
    companion object {
        private const val EXPIRING_SOON_THRESHOLD_DAYS = 3
        private const val MS_PER_DAY = 86400000L

        /**
         * Check if this item is expired.
         */
        fun isExpired(expirationDate: Long?): Boolean {
            return expirationDate != null && expirationDate < System.currentTimeMillis()
        }

        /**
         * Check if this item is expiring soon (within threshold days).
         */
        fun isExpiringSoon(expirationDate: Long?): Boolean {
            val now = System.currentTimeMillis()
            return expirationDate != null &&
                expirationDate > now &&
                expirationDate - now < (EXPIRING_SOON_THRESHOLD_DAYS * MS_PER_DAY)
        }
    }
}
