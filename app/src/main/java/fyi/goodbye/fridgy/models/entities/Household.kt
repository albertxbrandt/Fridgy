package fyi.goodbye.fridgy.models.entities

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data model representing a household that contains fridges, members, and a shared shopping list.
 *
 * Households are the top-level organizational entity in the app. Users belong to households,
 * and each household can have multiple fridges and a unified shopping list.
 *
 * @property id The unique Firestore document ID for this household.
 * @property name The name of the household (e.g., "Smith Family", "Our Apartment").
 * @property createdBy The User ID of the person who created and owns the household.
 * @property members A list of User IDs who are members of this household (for querying).
 * @property memberRoles A map of User IDs to their role in the household (OWNER, MANAGER, or MEMBER).
 * @property createdAt Server timestamp when the household was created.
 */
@Parcelize
@IgnoreExtraProperties
data class Household(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: List<String> = listOf(),
    val memberRoles: Map<String, String> = mapOf(),
    @ServerTimestamp
    val createdAt: Date? = null
) : Parcelable {
    /**
     * Gets the role of a specific user in this household.
     * Returns MEMBER by default for backwards compatibility.
     */
    fun getRoleForUser(userId: String): HouseholdRole {
        return HouseholdRole.fromString(memberRoles[userId])
    }

    /**
     * Checks if a user is the owner of this household.
     */
    fun isOwner(userId: String): Boolean {
        return userId == createdBy
    }
}
