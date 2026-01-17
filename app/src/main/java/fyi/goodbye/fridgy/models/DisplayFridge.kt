package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UI-optimized representation of a [Fridge].
 *
 * Unlike the base [Fridge] model, this class contains additional display-friendly information
 * including the resolved creator username.
 *
 * @property id The unique identifier for the fridge.
 * @property name The display name of the fridge (e.g., "Kitchen Fridge").
 * @property type The type of storage (fridge, freezer, pantry).
 * @property householdId The ID of the household this fridge belongs to.
 * @property createdByUid The User ID of the person who created this fridge.
 * @property creatorDisplayName The username of the person who created this fridge.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class DisplayFridge(
    val id: String = "",
    val name: String = "",
    val type: String = "fridge",
    val householdId: String = "",
    val createdByUid: String = "",
    val creatorDisplayName: String = "Unknown",
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
