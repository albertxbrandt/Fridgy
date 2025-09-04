package fyi.goodbye.fridgy

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import kotlinx.coroutines.tasks.await

class FridgeRepository {
    private val db = FirebaseFirestore.getInstance()

    // Get all fridges
    suspend fun getFridges(): List<Fridge> {
        return try {
            val snapshot = db.collection("fridges")
                .orderBy("name")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Fridge::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Get items for a specific fridge
    suspend fun getItemsForFridge(fridgeId: String): List<Item> {
        return try {
            val snapshot = db.collection("items")
                .whereEqualTo("fridgeId", fridgeId)
                .orderBy("addedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Item::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Add a new item
    suspend fun addItem(item: Item): Boolean {
        return try {
            val itemMap = mapOf(
                "upc" to item.upc,
                "quantity" to item.quantity,
                "addedBy" to item.addedBy,
                "addedAt" to item.addedAt,
                "lastUpdatedBy" to item.lastUpdatedBy,
                "lastUpdatedAt" to item.lastUpdatedAt
            )
            db.collection("items").add(itemMap).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Update item quantity
    suspend fun updateItemQuantity(itemId: String, newQuantity: Int, updatedBy: String): Boolean {
        return try {
            db.collection("items").document(itemId)
                .update(
                    "quantity", newQuantity,
                    "lastUpdatedBy", updatedBy,
                    "lastUpdatedAt", System.currentTimeMillis()
                ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Delete item
    suspend fun deleteItem(itemId: String): Boolean {
        return try {
            db.collection("items").document(itemId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}