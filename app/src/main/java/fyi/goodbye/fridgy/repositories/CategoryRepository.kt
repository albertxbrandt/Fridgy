package fyi.goodbye.fridgy.repositories

import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.Category
import fyi.goodbye.fridgy.utils.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing food categories in Firestore.
 *
 * Handles CRUD operations for categories and provides real-time updates via Flow.
 */
class CategoryRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesCollection = firestore.collection("categories")

    /**
     * Returns a real-time Flow of all categories, ordered by their sort order.
     */
    fun getCategories(): Flow<List<Category>> =
        categoriesCollection
            .orderBy("order")
            .asFlow<Category>()

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
                "name" to name,
                "order" to order,
                "createdAt" to System.currentTimeMillis()
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
                "name" to name,
                "order" to order
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
                .whereEqualTo("name", name)
                .get()
                .await()
        return !result.isEmpty
    }
}
