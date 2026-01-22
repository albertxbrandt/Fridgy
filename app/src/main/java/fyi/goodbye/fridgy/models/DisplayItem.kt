package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI model combining Item with Product information and expiration status.
 * Used for displaying items in inventory screens with computed properties.
 *
 * @property item The underlying item instance data
 * @property product Product information fetched from products collection (nullable if not found)
 * @property isExpiringSoon True if expiration is within 3 days
 * @property isExpired True if expiration date has passed
 */
@Parcelize
data class DisplayItem(
    val item: Item,
    val product: Product?,
    val isExpiringSoon: Boolean = false,
    val isExpired: Boolean = false
) : Parcelable {
    companion object {
        /**
         * Create a DisplayItem from an Item and optional Product.
         * Automatically calculates expiration status.
         */
        fun from(
            item: Item,
            product: Product?
        ): DisplayItem {
            val isExpired = Item.isExpired(item.expirationDate)
            val isExpiringSoon = Item.isExpiringSoon(item.expirationDate)

            return DisplayItem(
                item = item,
                product = product,
                isExpiringSoon = isExpiringSoon,
                isExpired = isExpired
            )
        }
    }
}
