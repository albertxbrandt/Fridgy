package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UI-optimized representation of a [Household].
 *
 * Unlike the base [Household] model, this class contains resolved UserProfile objects
 * for members, making it ready for direct UI display without additional lookups.
 *
 * @property id The unique identifier for the household.
 * @property name The display name of the household (e.g., "Smith Family").
 * @property createdByUid The User ID of the person who created/owns this household.
 * @property ownerDisplayName The username of the household owner.
 * @property memberUsers A list of UserProfile objects for all members (including owner).
 * @property fridgeCount The number of fridges in this household.
 * @property createdAt The timestamp (ms) when the household was created.
 */
@Parcelize
data class DisplayHousehold(
    val id: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val ownerDisplayName: String = "Unknown",
    val memberUsers: List<UserProfile> = listOf(),
    val fridgeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
