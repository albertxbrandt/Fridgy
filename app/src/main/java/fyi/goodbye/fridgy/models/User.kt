package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId // This annotation tells Firestore to map the document ID to this field
    val uid: String = "", // The Firebase Authentication User ID
    val email: String = "",
    val username: String = "",
    val memberOfFridges: List<String> = listOf(), // Array of fridge IDs user is a member of
    val ownerOfFridges: List<String> = listOf(),  // Array of fridge IDs user owns
    val createdAt: Long = System.currentTimeMillis()
)
