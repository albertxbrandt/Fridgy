package fyi.goodbye.fridgy.models

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a physical fridge shared among users.
 *
 * This class is designed for efficient Firestore storage and querying.
 * It stores only User IDs to maintain a single source of truth for usernames.
 * Usernames are fetched from the users collection when needed.
 *
 * @property id The unique Firestore document ID for this fridge.
 * @property name The name of the fridge (e.g., "Home", "Office").
 * @property type The type of storage (fridge, freezer, pantry).
 * @property location Optional physical location description (e.g., "Kitchen", "Garage").
 * @property createdBy The User ID of the person who created the fridge.
 * @property members A list of User IDs who are members of this fridge.
 * @property pendingInvites A list of User IDs who have been invited but not yet accepted.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
@Parcelize
data class Fridge(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val type: String = "fridge", // fridge, freezer, or pantry
    val location: String = "",
    val createdBy: String = "",
    val members: List<String> = listOf(),
    val pendingInvites: List<String> = listOf(),
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable
