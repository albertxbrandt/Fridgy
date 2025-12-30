package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UI-optimized representation of a [Fridge].
 * 
 * Unlike the base [Fridge] model, this class contains additional display-friendly information
 * such as the creator's username, which avoids the need for repeated database lookups
 * during UI rendering.
 *
 * @property id The unique identifier for the fridge.
 * @property name The display name of the fridge (e.g., "Kitchen Fridge").
 * @property createdByUid The User ID of the person who created this fridge.
 * @property creatorDisplayName The username of the person who created this fridge.
 * @property members A map of active member User IDs to their usernames.
 * @property pendingInvites A map of User IDs who have been invited but haven't accepted yet to their usernames.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class DisplayFridge(
    val id: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val creatorDisplayName: String = "Unknown",
    val members: Map<String, String> = mapOf(), // uid -> username
    val pendingInvites: Map<String, String> = mapOf(), // uid -> username
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
