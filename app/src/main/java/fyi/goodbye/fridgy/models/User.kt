package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Private user data that can only be read by the user themselves.
 * Public user information (username) is stored in UserProfile collection.
 *
 * @property uid The unique identifier for the user.
 * @property email The user's email address.
 * @property createdAt The timestamp when the account was created.
 */
@Parcelize
data class User(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
