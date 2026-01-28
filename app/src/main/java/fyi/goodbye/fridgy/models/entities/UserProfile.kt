package fyi.goodbye.fridgy.models.entities

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Public user profile data that can be read by all authenticated users.
 * Used for displaying usernames in fridge members, item history, etc.
 *
 * @property uid The unique identifier for the user (matches User.uid).
 * @property username The display name of the user.
 */
@Parcelize
data class UserProfile(
    @DocumentId
    val uid: String = "",
    val username: String = ""
) : Parcelable
