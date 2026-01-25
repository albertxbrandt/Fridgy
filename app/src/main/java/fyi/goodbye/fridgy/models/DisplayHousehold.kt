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
 * @property memberRoles A map of User IDs to their role in the household.
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
    val memberRoles: Map<String, String> = mapOf(),
    val fridgeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    /**
     * Gets the role of a specific user in this household.
     */
    fun getRoleForUser(userId: String): HouseholdRole {
        return HouseholdRole.fromString(memberRoles[userId])
    }

    /**
     * Checks if a user is the owner of this household.
     */
    fun isOwner(userId: String): Boolean {
        return userId == createdByUid
    }
}
