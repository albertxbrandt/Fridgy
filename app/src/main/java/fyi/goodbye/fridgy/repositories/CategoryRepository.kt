package fyi.goodbye.fridgy.repositories

import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date

/**
 * Repository for managing food categories in Firestore.
 *
 * Handles CRUD operations for categories and provides real-time updates via Flow.
 *
 * @param firestore The Firestore instance for database operations.
 */
class CategoryRepository(
    private val firestore: FirebaseFirestore
) {
    private val categoriesCollection = firestore.collection(FirestoreCollections.CATEGORIES)

    /**
     * Returns a real-time Flow of all categories, ordered by their sort order.
     * Handles both Long and Date types for createdAt during migration period.
     */
    fun getCategories(): Flow<List<Category>> =
        callbackFlow {
            val listener =
                categoriesCollection
                    .orderBy(FirestoreFields.ORDER)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e("Error listening to categories: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            val categories =
                                snapshot.documents.mapNotNull { doc ->
                                    try {
                                        // Get the raw createdAt value
                                        val createdAtValue = doc.get(FirestoreFields.CREATED_AT)
                                        val createdAt: Date? =
                                            when (createdAtValue) {
                                                is Long -> Date(createdAtValue)
                                                is Date -> createdAtValue
                                                is com.google.firebase.Timestamp -> createdAtValue.toDate()
                                                else -> null
                                            }

                                        Category(
                                            id = doc.id,
                                            name = doc.getString(FirestoreFields.NAME) ?: "",
                                            order = doc.getLong(FirestoreFields.ORDER)?.toInt() ?: Category.DEFAULT_ORDER,
                                            createdAt = createdAt
                                        )
                                    } catch (e: Exception) {
                                        Timber.e("Error parsing category ${doc.id}: ${e.message}")
                                        null
                                    }
                                }
                            trySend(categories)
                        }
                    }

            awaitClose { listener.remove() }
        }.distinctUntilChanged()

    /**
     * Creates a new category.
     *
     * @param name The name of the category.
     * @param order The sort order (optional, defaults to 999).
     * @return The ID of the newly created category.
     */
    suspend fun createCategory(
        name: String,
        order: Int = 999
    ): String {
        val category =
            hashMapOf(
                FirestoreFields.NAME to name,
                FirestoreFields.ORDER to order,
                FirestoreFields.CREATED_AT to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        val docRef = categoriesCollection.add(category).await()
        return docRef.id
    }

    /**
     * Updates an existing category.
     *
     * @param categoryId The ID of the category to update.
     * @param name The new name for the category.
     * @param order The new sort order for the category.
     */
    suspend fun updateCategory(
        categoryId: String,
        name: String,
        order: Int
    ) {
        categoriesCollection.document(categoryId).update(
            mapOf(
                FirestoreFields.NAME to name,
                FirestoreFields.ORDER to order
            )
        ).await()
    }

    /**
     * Deletes a category.
     *
     * Note: This does not update products that reference this category.
     * Consider updating those products to a default category before deletion.
     *
     * @param categoryId The ID of the category to delete.
     */
    suspend fun deleteCategory(categoryId: String) {
        categoriesCollection.document(categoryId).delete().await()
    }

    /**
     * Checks if a category name already exists (case-insensitive).
     *
     * @param name The category name to check.
     * @return True if the category exists, false otherwise.
     */
    suspend fun categoryExists(name: String): Boolean {
        val result =
            categoriesCollection
                .whereEqualTo(FirestoreFields.NAME, name)
                .get()
                .await()
        return !result.isEmpty
    }
}
