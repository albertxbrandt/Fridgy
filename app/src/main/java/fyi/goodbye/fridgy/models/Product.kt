package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a global product entry in the crowdsourced database.
 *
 * @property upc The unique barcode/UPC for the product (used as Firestore Document ID).
 * @property name The display name of the product.
 * @property brand The brand of the product.
 * @property imageUrl The Firebase Storage URL for the product image.
 * @property category The food category (e.g., Dairy, Meat, Frozen).
 * @property size Numeric size/quantity (e.g., 1.0 for "1 gallon", 12 for "12 pack")
 * @property unit Unit of measurement (e.g., "GALLON", "QUART", "LITER", "DOZEN", etc.)
 * @property lastUpdated Timestamp of the last time this product info was modified.
 */
@Parcelize
data class Product(
    @DocumentId
    val upc: String = "",
    val name: String = "",
    val brand: String = "",
    val imageUrl: String? = null,
    val category: String = "Other",
    val size: Double? = null,
    val unit: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable
