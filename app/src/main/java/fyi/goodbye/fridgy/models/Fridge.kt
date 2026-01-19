package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a physical fridge within a household.
 *
 * Fridges belong to households and store inventory items. Member management
 * is handled at the household level, not the fridge level.
 *
 * @property id The unique Firestore document ID for this fridge.
 * @property name The name of the fridge (e.g., "Kitchen Fridge", "Garage Freezer").
 * @property type The type of storage (fridge, freezer, pantry).
 * @property location Optional physical location description (e.g., "Kitchen", "Garage").
 * @property householdId The ID of the household this fridge belongs to.
 * @property createdBy The User ID of the person who created the fridge.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class Fridge(
    @DocumentId
    val id: String = "",
    val name: String = "",
    // fridge, freezer, or pantry
    val type: String = "fridge",
    val location: String = "",
    val householdId: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable
