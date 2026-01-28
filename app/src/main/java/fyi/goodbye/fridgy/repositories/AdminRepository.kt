package fyi.goodbye.fridgy.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.constants.StoragePaths
import fyi.goodbye.fridgy.models.Admin
import fyi.goodbye.fridgy.models.AdminUserDisplay
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.models.User
import fyi.goodbye.fridgy.models.UserProfile
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Represents a paginated result set.
 * @property items The list of items in this page
 * @property lastDocument The last document in this page, used as cursor for next page
 * @property hasMore Whether there are more items available
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val lastDocument: DocumentSnapshot?,
    val hasMore: Boolean
)

/**
 * Repository for managing admin user privileges.
 *
 * @param firestore The Firestore instance for database operations.
 * @param auth The Auth instance for user identification.
 * @param storage The Storage instance for cleanup operations.
 */
class AdminRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) {
    private val adminsCollection = firestore.collection(FirestoreCollections.ADMINS)

    /**
     * Checks if the current user is an admin.
     */
    suspend fun isCurrentUserAdmin(): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false

        return try {
            val doc = adminsCollection.document(currentUserId).get().await()
            doc.exists()
        } catch (e: Exception) {
            Timber.e("Error checking admin status: ${e.message}")
            false
        }
    }

    /**
     * Gets the admin document for the current user if they are an admin.
     */
    suspend fun getCurrentAdminInfo(): Admin? {
        val currentUserId = auth.currentUser?.uid ?: return null

        return try {
            val doc = adminsCollection.document(currentUserId).get().await()
            if (doc.exists()) {
                doc.toObject(Admin::class.java)?.copy(uid = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e("Error fetching admin info: ${e.message}")
            null
        }
    }

    /**
     * Gets all users from the users and userProfiles collections.
     * Combines data for admin panel display.
     *
     * WARNING: Loads all users into memory. Use getAllUsersPaginated() for large datasets.
     */
    suspend fun getAllUsers(): List<AdminUserDisplay> {
        return try {
            val usersSnapshot = firestore.collection(FirestoreCollections.USERS).get().await()
            val profilesSnapshot = firestore.collection(FirestoreCollections.USER_PROFILES).get().await()

            // Create maps for easy lookup, manually parsing to handle Long/Date migration
            val users =
                usersSnapshot.documents.mapNotNull { doc ->
                    try {
                        val createdAtValue = doc.get(FirestoreFields.CREATED_AT)
                        val createdAt: java.util.Date? =
                            when (createdAtValue) {
                                is Long -> java.util.Date(createdAtValue)
                                is java.util.Date -> createdAtValue
                                is com.google.firebase.Timestamp -> createdAtValue.toDate()
                                else -> null
                            }

                        User(
                            uid = doc.id,
                            email = doc.getString(FirestoreFields.EMAIL) ?: "",
                            createdAt = createdAt
                        )
                    } catch (e: Exception) {
                        Timber.e("Error parsing user ${doc.id}: ${e.message}")
                        null
                    }
                }.associateBy { it.uid }

            val profiles =
                profilesSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }.associateBy { it.uid }

            // Combine data
            users.map { (uid, user) ->
                AdminUserDisplay(
                    uid = uid,
                    username = profiles[uid]?.username ?: "Unknown",
                    email = user.email,
                    createdAt = user.createdAt
                )
            }
        } catch (e: Exception) {
            Timber.e("Error fetching users: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets users with pagination support.
     * PERFORMANCE FIX: Limits memory usage and Firestore reads for admin panel.
     *
     * @param pageSize Number of users to fetch per page (default: 50)
     * @param startAfter Last document from previous page, null for first page
     * @return PaginatedResult containing users and pagination info
     */
    suspend fun getAllUsersPaginated(
        pageSize: Int = 50,
        startAfter: DocumentSnapshot? = null
    ): PaginatedResult<AdminUserDisplay> {
        return try {
            // Build query with pagination
            var usersQuery =
                firestore.collection(FirestoreCollections.USERS)
                    .orderBy(FirestoreFields.CREATED_AT)
                    .limit(pageSize.toLong() + 1) // Fetch one extra to check if there are more

            if (startAfter != null) {
                usersQuery = usersQuery.startAfter(startAfter)
            }

            val usersSnapshot = usersQuery.get().await()
            val hasMore = usersSnapshot.documents.size > pageSize

            // Take only requested page size
            val userDocuments = usersSnapshot.documents.take(pageSize)
            val lastDocument = if (hasMore) userDocuments.lastOrNull() else null

            // Parse users from documents
            val users =
                userDocuments.mapNotNull { doc ->
                    try {
                        val createdAtValue = doc.get(FirestoreFields.CREATED_AT)
                        val createdAt: java.util.Date? =
                            when (createdAtValue) {
                                is Long -> java.util.Date(createdAtValue)
                                is java.util.Date -> createdAtValue
                                is com.google.firebase.Timestamp -> createdAtValue.toDate()
                                else -> null
                            }

                        User(
                            uid = doc.id,
                            email = doc.getString(FirestoreFields.EMAIL) ?: "",
                            createdAt = createdAt
                        )
                    } catch (e: Exception) {
                        Timber.e("Error parsing user ${doc.id}: ${e.message}")
                        null
                    }
                }

            // Batch fetch profiles for these users
            val userIds = users.map { it.uid }
            val profilesSnapshot =
                firestore.collection(FirestoreCollections.USER_PROFILES)
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), userIds)
                    .get()
                    .await()

            val profiles =
                profilesSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }.associateBy { it.uid }

            // Combine data
            val adminUsers =
                users.map { user ->
                    AdminUserDisplay(
                        uid = user.uid,
                        username = profiles[user.uid]?.username ?: "Unknown",
                        email = user.email,
                        createdAt = user.createdAt
                    )
                }

            PaginatedResult(
                items = adminUsers,
                lastDocument = lastDocument,
                hasMore = hasMore
            )
        } catch (e: Exception) {
            Timber.e("Error fetching paginated users: ${e.message}")
            PaginatedResult(emptyList(), null, false)
        }
    }

    /**
     * Gets all products from the products collection, sorted by most recently updated first.
     *
     * WARNING: Loads all products into memory. Use getAllProductsPaginated() for large datasets.
     */
    suspend fun getAllProducts(): List<Product> {
        return try {
            val snapshot =
                firestore.collection(FirestoreCollections.PRODUCTS)
                    .get()
                    .await()

            Timber.d("Fetched ${snapshot.documents.size} product documents from Firestore")

            val products =
                snapshot.documents.mapNotNull { doc ->
                    try {
                        val lastUpdatedValue = doc.get(FirestoreFields.LAST_UPDATED)
                        val lastUpdated: java.util.Date? =
                            when (lastUpdatedValue) {
                                is Long -> java.util.Date(lastUpdatedValue)
                                is java.util.Date -> lastUpdatedValue
                                is com.google.firebase.Timestamp -> lastUpdatedValue.toDate()
                                else -> null
                            }

                        Product(
                            upc = doc.id,
                            name = doc.getString(FirestoreFields.NAME) ?: "",
                            brand = doc.getString(FirestoreFields.BRAND) ?: "",
                            category = doc.getString(FirestoreFields.CATEGORY) ?: "Other",
                            imageUrl = doc.getString(FirestoreFields.IMAGE_URL),
                            size = doc.getDouble(FirestoreFields.SIZE),
                            unit = doc.getString(FirestoreFields.UNIT),
                            searchTokens = (doc.get(FirestoreFields.SEARCH_TOKENS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            lastUpdated = lastUpdated
                        )
                    } catch (e: Exception) {
                        Timber.e("Failed to deserialize product ${doc.id}: ${e.message}")
                        null
                    }
                }.sortedByDescending { it.lastUpdated }

            Timber.d("Successfully deserialized ${products.size} products")
            products
        } catch (e: Exception) {
            Timber.e(e, "Error fetching products: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets products with pagination support, sorted by most recently updated first.
     * PERFORMANCE FIX: Limits memory usage and Firestore reads for admin panel.
     *
     * @param pageSize Number of products to fetch per page (default: 100)
     * @param startAfter Last document from previous page, null for first page
     * @return PaginatedResult containing products and pagination info
     */
    suspend fun getAllProductsPaginated(
        pageSize: Int = 100,
        startAfter: DocumentSnapshot? = null
    ): PaginatedResult<Product> {
        return try {
            // Build query with pagination (ordered by lastUpdated descending)
            var query =
                firestore.collection(FirestoreCollections.PRODUCTS)
                    .orderBy(FirestoreFields.LAST_UPDATED, com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(pageSize.toLong() + 1) // Fetch one extra to check if there are more

            if (startAfter != null) {
                query = query.startAfter(startAfter)
            }

            val snapshot = query.get().await()
            val hasMore = snapshot.documents.size > pageSize

            // Take only requested page size
            val productDocuments = snapshot.documents.take(pageSize)
            val lastDocument = if (hasMore) productDocuments.lastOrNull() else null

            Timber.d("Fetched ${productDocuments.size} products (hasMore: $hasMore)")

            val products =
                productDocuments.mapNotNull { doc ->
                    try {
                        val lastUpdatedValue = doc.get(FirestoreFields.LAST_UPDATED)
                        val lastUpdated: java.util.Date? =
                            when (lastUpdatedValue) {
                                is Long -> java.util.Date(lastUpdatedValue)
                                is java.util.Date -> lastUpdatedValue
                                is com.google.firebase.Timestamp -> lastUpdatedValue.toDate()
                                else -> null
                            }

                        Product(
                            upc = doc.id,
                            name = doc.getString(FirestoreFields.NAME) ?: "",
                            brand = doc.getString(FirestoreFields.BRAND) ?: "",
                            category = doc.getString(FirestoreFields.CATEGORY) ?: "Other",
                            imageUrl = doc.getString(FirestoreFields.IMAGE_URL),
                            size = doc.getDouble(FirestoreFields.SIZE),
                            unit = doc.getString(FirestoreFields.UNIT),
                            searchTokens = (doc.get(FirestoreFields.SEARCH_TOKENS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            lastUpdated = lastUpdated
                        )
                    } catch (e: Exception) {
                        Timber.e("Failed to deserialize product ${doc.id}: ${e.message}")
                        null
                    }
                }

            PaginatedResult(
                items = products,
                lastDocument = lastDocument,
                hasMore = hasMore
            )
        } catch (e: Exception) {
            Timber.e(e, "Error fetching paginated products: ${e.message}")
            PaginatedResult(emptyList(), null, false)
        }
    }

    /**
     * Gets all fridges from the fridges collection.
     */
    suspend fun getAllFridges(): List<Fridge> {
        return try {
            val snapshot = firestore.collection(FirestoreCollections.FRIDGES).get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Fridge::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Timber.e("Error fetching fridges: ${e.message}")
            emptyList()
        }
    }

    /**
     * Deletes a user and all their associated data.
     * WARNING: This is a destructive operation.
     */
    suspend fun deleteUser(userId: String): Boolean {
        return try {
            // Delete user document (private data)
            firestore.collection(FirestoreCollections.USERS).document(userId).delete().await()

            // Delete user profile (public data)
            firestore.collection(FirestoreCollections.USER_PROFILES).document(userId).delete().await()

            // Note: In a production app, you'd also want to:
            // - Delete user's Firebase Auth account
            // - Remove user from all fridge members
            // - Handle orphaned data

            true
        } catch (e: Exception) {
            Timber.e("Error deleting user: ${e.message}")
            false
        }
    }

    /**
     * Updates a user's information.
     */
    suspend fun updateUser(
        userId: String,
        username: String,
        email: String
    ): Boolean {
        return try {
            // Update private data (email)
            val userUpdates = mapOf(FirestoreFields.EMAIL to email)
            firestore.collection(FirestoreCollections.USERS).document(userId).update(userUpdates).await()

            // Update public data (username)
            val profileUpdates = mapOf(FirestoreFields.USERNAME to username)
            firestore.collection(FirestoreCollections.USER_PROFILES).document(userId).update(profileUpdates).await()

            true
        } catch (e: Exception) {
            Timber.e("Error updating user: ${e.message}")
            false
        }
    }

    /**
     * Also deletes the associated product image from Firebase Storage.
     */
    suspend fun deleteProduct(upc: String): Boolean {
        return try {
            // Delete product document from Firestore
            firestore.collection(FirestoreCollections.PRODUCTS).document(upc).delete().await()

            // Delete product image from Storage
            try {
                val imageRef = storage.reference.child(StoragePaths.productImage(upc))
                imageRef.delete().await()
            } catch (e: Exception) {
                // Log but don't fail if image doesn't exist
                Timber.w("Could not delete product image: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Timber.e("Error deleting product: ${e.message}")
            false
        }
    }

    /**
     * Updates a product's information.
     */
    suspend fun updateProduct(
        upc: String,
        name: String,
        brand: String,
        category: String
    ): Boolean {
        return try {
            val updates =
                mapOf(
                    FirestoreFields.NAME to name,
                    FirestoreFields.BRAND to brand,
                    FirestoreFields.CATEGORY to category,
                    FirestoreFields.LAST_UPDATED to System.currentTimeMillis()
                )
            firestore.collection(FirestoreCollections.PRODUCTS).document(upc).update(updates).await()
            true
        } catch (e: Exception) {
            Timber.e("Error updating product: ${e.message}")
            false
        }
    }
}
