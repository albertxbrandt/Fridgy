package fyi.goodbye.fridgy.models

import com.google.firebase.firestore.DocumentId

data class Item(
    @DocumentId
    val id: String = "", // Firebase document ID (auto-generated)
    val upc: String = "", // barcode/UPC number from scanning
    val quantity: Int = 1,
    val addedBy: String = "", // email of person who added item
    val addedAt: Long = System.currentTimeMillis(),
    val lastUpdatedBy: String = "", // email of person who last modified
    val lastUpdatedAt: Long = System.currentTimeMillis()
)