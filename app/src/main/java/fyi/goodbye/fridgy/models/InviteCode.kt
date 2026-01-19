package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a shareable invite code for joining a household.
 *
 * Invite codes are short alphanumeric codes that can be shared via text, email, etc.
 * They have an optional expiration date and can be revoked by the household owner.
 *
 * @property code The unique 6-character alphanumeric invite code (also serves as document ID).
 * @property householdId The ID of the household this code grants access to.
 * @property householdName The name of the household (for display in join confirmation).
 * @property createdBy The User ID of the person who created the invite code.
 * @property createdAt The timestamp (ms) when the code was created.
 * @property expiresAt The timestamp (ms) when the code expires, or null for no expiration.
 * @property usedBy The User ID of the person who redeemed the code, or null if unused.
 * @property usedAt The timestamp (ms) when the code was redeemed, or null if unused.
 * @property isActive Whether the code is still valid (not revoked).
 */
@Parcelize
data class InviteCode(
    @DocumentId
    val code: String = "",
    val householdId: String = "",
    val householdName: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val usedBy: String? = null,
    val usedAt: Long? = null,
    val isActive: Boolean = true
) : Parcelable {
    /**
     * Checks if this invite code is currently valid for use.
     * A code is valid if it is active, has not been used, and has not expired.
     */
    fun isValid(): Boolean {
        if (!isActive) return false
        if (usedBy != null) return false
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) return false
        return true
    }

    /**
     * Checks if this invite code has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt != null && System.currentTimeMillis() > expiresAt
    }
}
