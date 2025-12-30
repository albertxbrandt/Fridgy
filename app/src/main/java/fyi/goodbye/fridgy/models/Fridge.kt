package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

/**
 * Data model representing a physical fridge shared among users.
 * 
 * This class is designed for efficient Firestore storage and querying. It uses Maps
 * to store user relationships (members and invites) which allows for fast lookups
 * of a user's fridges without needing to store redundant data in the User collection.
 *
 * @property id The unique Firestore document ID for this fridge.
 * @property name The name of the fridge (e.g., "Home", "Office").
 * @property createdBy The User ID of the person who created the fridge.
 * @property members A map where keys are User IDs and values are their usernames. 
 *                   This allows displaying member names without extra database lookups.
 * @property pendingInvites A map where keys are User IDs and values are the usernames
 *                          of people who have been invited but not yet accepted.
 * @property createdAt The timestamp (ms) when the fridge was created.
 */
data class Fridge(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: Map<String, String> = mapOf(), // uid -> username
    val pendingInvites: Map<String, String> = mapOf(), // uid -> username
    val createdAt: Long = System.currentTimeMillis()
)
