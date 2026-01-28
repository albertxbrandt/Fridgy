package fyi.goodbye.fridgy.models.entities

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

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
 * @property searchTokens Lowercase word fragments for efficient search (auto-generated)
 * @property lastUpdated Timestamp of the last time this product info was modified (managed by Firestore).
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
    val searchTokens: List<String> = emptyList(),
    @ServerTimestamp
    val lastUpdated: Date? = null
) : Parcelable {
    companion object {
        /**
         * Generates search tokens from product name and brand for efficient Firestore queries.
         * Tokens include: whole words, prefixes (3+ chars), and normalized strings.
         * Filters out special characters and very short words for better search quality.
         */
        fun generateSearchTokens(
            name: String,
            brand: String
        ): List<String> {
            val tokens = mutableSetOf<String>()
            val text = "$name $brand".lowercase().trim()

            // Split into words and filter out non-alphanumeric words
            val words =
                text.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .filter { word -> word.any { char -> char.isLetterOrDigit() } }
                    .map { it.replace(Regex("[^a-z0-9]"), "") } // Remove special chars
                    .filter { it.length >= 2 } // Minimum 2 characters

            words.forEach { word ->
                // Add full word
                tokens.add(word)

                // Add prefixes (minimum 3 characters for efficiency)
                if (word.length >= 3) {
                    for (i in 3..word.length) {
                        tokens.add(word.substring(0, i))
                    }
                }

                // Add suffixes (for matching words like "milk" in "oatmilk")
                if (word.length >= 4) {
                    for (i in (word.length - 3) downTo 0) {
                        val suffix = word.substring(i)
                        if (suffix.length >= 3) {
                            tokens.add(suffix)
                        }
                    }
                }
            }

            return tokens.toList()
        }
    }
}
