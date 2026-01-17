package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a household that contains fridges, members, and a shared shopping list.
 *
 * Households are the top-level organizational entity in the app. Users belong to households,
 * and each household can have multiple fridges and a unified shopping list.
 *
 * @property id The unique Firestore document ID for this household.
 * @property name The name of the household (e.g., "Smith Family", "Our Apartment").
 * @property createdBy The User ID of the person who created and owns the household.
 * @property members A list of User IDs who are members of this household.
 * @property createdAt The timestamp (ms) when the household was created.
 */
@Parcelize
data class Household(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: List<String> = listOf(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
