package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A UI-optimized representation of a [Fridge].
 *
 * Unlike the base [Fridge] model, this class contains additional display-friendly information
 * including resolved User objects for members and invites.
 *
 * @property id The unique identifier for the fridge.
 * @property name The display name of the fridge (e.g., "Kitchen Fridge").
 * @property createdByUid The User ID of the person who created this fridge.
 * @property creatorDisplayName The username of the person who created this fridge.
 * @property memberUsers A list of User objects for active members.
 * @property pendingInviteUsers A list of User objects for pending invites.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class DisplayFridge(
    val id: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val creatorDisplayName: String = "Unknown",
    val memberUsers: List<User> = listOf(),
    val pendingInviteUsers: List<User> = listOf(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
