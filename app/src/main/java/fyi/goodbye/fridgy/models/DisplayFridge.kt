package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UI-optimized representation of a [Fridge].
 *
 * This class provides a display-friendly view of fridge data for UI rendering.
 * Management permissions are controlled at the household level.
 *
 * @property id The unique identifier for the fridge.
 * @property name The display name of the fridge (e.g., "Kitchen Fridge").
 * @property type The type of storage (fridge, freezer, pantry).
 * @property householdId The ID of the household this fridge belongs to.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class DisplayFridge(
    val id: String = "",
    val name: String = "",
    val type: String = "fridge",
    val householdId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
