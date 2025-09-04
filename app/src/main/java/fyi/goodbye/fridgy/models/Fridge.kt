package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

data class Fridge(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: List<String> = listOf(),
    val createdAt: Long = System.currentTimeMillis()
)
