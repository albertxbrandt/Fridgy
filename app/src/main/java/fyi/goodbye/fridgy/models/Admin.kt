package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

/**
 * Data model representing an admin user.
 * 
 * @property uid The user ID of the admin (matches User.uid).
 * @property email The email address of the admin.
 * @property grantedAt The timestamp when admin privileges were granted.
 * @property grantedBy The user ID of the admin who granted these privileges (or "system").
 */
data class Admin(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val grantedAt: Long = System.currentTimeMillis(),
    val grantedBy: String = "system"
)
