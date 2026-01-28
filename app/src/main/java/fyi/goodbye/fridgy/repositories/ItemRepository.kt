package fyi.goodbye.fridgy.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.display.DisplayItem
import fyi.goodbye.fridgy.models.entities.Item
import fyi.goodbye.fridgy.models.entities.ShoppingListItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Repository for managing item instances within fridges.
 *
 * This repository handles all data operations related to:
 * - Item instance CRUD operations (create, read, update, delete)
 * - Item queries (by UPC, by fridge)
 * - Moving items between fridges
 * - Item expiration date management
 * - Shopping session completion (adding items from shopping list to fridges)
 *
 * ## Architecture Notes
 * - Items are stored as individual instances in a subcollection (`fridges/{fridgeId}/items`)
 * - Each item instance can have its own expiration date and tracking metadata
 * - Real-time updates are provided via Kotlin Flows using Firestore snapshot listeners
 * - Uses FieldValue.serverTimestamp() for timestamp fields to comply with security rules
 *
 * ## Thread Safety
 * - All operations use coroutines for async operations
 * - Batch operations ensure atomicity for multi-step processes (e.g., moveItem)
 *
 * @param firestore The Firestore instance for database operations
 * @param auth The Auth instance for user identification
 * @param fridgeRepository The FridgeRepository for fridge-level validations
 * @param householdRepository The HouseholdRepository for permission checks
 * @see Item For item instance data model
 * @see FridgeRepository For fridge-level operations
 */
class ItemRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val fridgeRepository: FridgeRepository,
    private val householdRepository: HouseholdRepository
) {
    // ========================================
    // ITEM QUERIES
    // ========================================

    /**
     * Returns a Flow of all item instances in a fridge that match a specific UPC.
     *
     * Listens for real-time updates to item instances with the given barcode.
     * Each item represents a separate physical unit with its own expiration date.
     *
     * @param fridgeId Target fridge ID
     * @param upc The Universal Product Code (barcode) to filter by
     * @return Flow emitting lists of Item instances matching the UPC
     */
    fun getItemsByUpc(
        fridgeId: String,
        upc: String
    ): Flow<List<Item>> =
        callbackFlow {
            val listenerRegistration =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .whereEqualTo(FirestoreFields.UPC, upc)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Error listening to items for UPC: $upc in fridge $fridgeId")
                            return@addSnapshotListener
                        }

                        val items =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(Item::class.java)?.copy(id = doc.id)
                            } ?: emptyList()

                        trySend(items)
                    }

            awaitClose { listenerRegistration.remove() }
        }.distinctUntilChanged()

    /**
     * Gets the count of item instances in a fridge.
     * Uses Firestore cache first to minimize network reads.
     *
     * @param fridgeId Target fridge ID
     * @return Total count of item instances, or 0 if an error occurs
     */
    suspend fun getItemCount(fridgeId: String): Int {
        return try {
            // Try cache first for instant response
            val cacheSnapshot =
                try {
                    firestore.collection(FirestoreCollections.FRIDGES)
                        .document(fridgeId)
                        .collection(FirestoreCollections.ITEMS)
                        .get(Source.CACHE)
                        .await()
                } catch (e: Exception) {
                    null
                }

            if (cacheSnapshot != null) {
                cacheSnapshot.size()
            } else {
                // Fallback to network if cache unavailable
                val snapshot =
                    firestore.collection(FirestoreCollections.FRIDGES)
                        .document(fridgeId)
                        .collection(FirestoreCollections.ITEMS)
                        .get(Source.DEFAULT)
                        .await()
                snapshot.size()
            }
        } catch (e: Exception) {
            // Don't log cancellation exceptions (normal when navigating away)
            if (e !is kotlinx.coroutines.CancellationException) {
                Timber.e("Error getting item count for fridge $fridgeId: ${e.message}")
            }
            0
        }
    }

    /**
     * Preloads items for a specific fridge from Firestore cache to memory.
     * Should be called when opening a fridge inventory to enable instant display.
     * This reads from Firestore's local persistence layer without network access.
     *
     * @param fridgeId The ID of the fridge to preload items for.
     * @return List of preloaded items, or empty list if cache unavailable.
     */
    suspend fun preloadItemsFromCache(fridgeId: String): List<Item> {
        return try {
            val snapshot =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .get(Source.CACHE)
                    .await()

            val items = snapshot.documents.mapNotNull { it.toObject(Item::class.java) }
            Timber.d("Preloaded ${items.size} items from cache for fridge $fridgeId")
            items
        } catch (e: Exception) {
            Timber.w("Could not preload items from cache: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets all items for a fridge with expiration status.
     *
     * Returns a flow that emits whenever items change. Product info is NOT included here -
     * the ViewModel layer should handle fetching product data using ProductRepository.
     *
     * @param fridgeId Target fridge ID
     * @return Flow of DisplayItem list (with null products), sorted by expiration date
     */
    fun getItemsForFridge(fridgeId: String): Flow<List<DisplayItem>> =
        callbackFlow {
            // Use SnapshotListener with metadata changes enabled for immediate updates
            val listener =
                firestore.collection(
                    FirestoreCollections.FRIDGES
                ).document(fridgeId).collection(FirestoreCollections.ITEMS)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                        if (e != null) {
                            // Check if this is a permission error (fridge deleted or access revoked)
                            val isPermissionError = e.message?.contains("PERMISSION_DENIED") == true
                            if (isPermissionError) {
                                // Fridge was likely deleted or access revoked - close the flow silently
                                Timber.d("Access to fridge $fridgeId revoked or fridge deleted, closing items listener")
                                close()
                            } else {
                                // Log other errors but send empty list to prevent app crash
                                Timber.e(e, "Error fetching items for fridge $fridgeId: ${e.message}")
                                trySend(emptyList()).isSuccess
                            }
                            return@addSnapshotListener
                        }

                        val items =
                            snapshot?.documents?.mapNotNull {
                                it.toObject(Item::class.java)?.copy(id = it.id)
                            } ?: emptyList()

                        Timber.d("Items snapshot received for fridge $fridgeId: ${items.size} items")

                        // Create DisplayItems without product info (ViewModel will fetch products)
                        val displayItems =
                            items.map { item ->
                                DisplayItem.from(item, null)
                            }.sortedWith(
                                compareBy<DisplayItem> {
                                    // Sort expired first, then expiring soon, then by date
                                    when {
                                        it.isExpired -> 0
                                        it.isExpiringSoon -> 1
                                        it.item.expirationDate != null -> 2
                                        else -> 3
                                    }
                                }.thenBy { it.item.expirationDate ?: Long.MAX_VALUE }
                            )

                        trySend(displayItems).isSuccess
                    }
            awaitClose { listener.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    // ========================================
    // ITEM CRUD OPERATIONS
    // ========================================

    /**
     * Adds a new item instance to a fridge.
     *
     * Items are now stored as individual instances rather than aggregated quantities,
     * allowing each instance to have its own expiration date.
     *
     * @param fridgeId Target fridge ID
     * @param upc The Universal Product Code (barcode) of the item to add
     * @param expirationDate Optional expiration date in milliseconds since epoch
     * @return The created Item instance with its generated ID
     * @throws IllegalStateException if user is not logged in or fridge has no householdId
     * @throws Exception if Firestore operations fail
     */
    suspend fun addItemToFridge(
        fridgeId: String,
        upc: String,
        expirationDate: Long? = null
    ): Item {
        Timber.d("Adding item instance for UPC: $upc with expiration: $expirationDate")
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        // Use HashMap with FieldValue.serverTimestamp() to match security rules
        val newItemData =
            hashMapOf<String, Any?>(
                FirestoreFields.UPC to upc,
                FirestoreFields.EXPIRATION_DATE to expirationDate,
                FirestoreFields.ADDED_BY to currentUser.uid,
                FirestoreFields.LAST_UPDATED_BY to currentUser.uid,
                FirestoreFields.ADDED_AT to FieldValue.serverTimestamp(),
                FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
            )

        try {
            val docRef =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .add(newItemData)
                    .await()

            Timber.d("Added item instance: ${docRef.id} (UPC: $upc)")
            return Item(
                id = docRef.id,
                upc = upc,
                expirationDate = expirationDate,
                addedBy = currentUser.uid
            )
        } catch (e: Exception) {
            Timber.e(e, "Error adding item to fridge")
            throw e
        }
    }

    /**
     * Updates the expiration date of a specific item instance.
     *
     * @param fridgeId Target fridge ID
     * @param itemId The unique item instance ID
     * @param expirationDate New expiration date in milliseconds (null to remove expiration)
     */
    suspend fun updateItemExpirationDate(
        fridgeId: String,
        itemId: String,
        expirationDate: Long?
    ) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        try {
            firestore.collection(FirestoreCollections.FRIDGES)
                .document(fridgeId)
                .collection(FirestoreCollections.ITEMS)
                .document(itemId)
                .update(
                    mapOf(
                        FirestoreFields.EXPIRATION_DATE to expirationDate,
                        FirestoreFields.LAST_UPDATED_BY to currentUser.uid,
                        FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                )
                .await()

            Timber.d("Updated expiration date for item: $itemId")
        } catch (e: Exception) {
            Timber.e(e, "Error updating expiration date")
            throw e
        }
    }

    /**
     * Deletes a specific item instance from a fridge.
     *
     * @param fridgeId Target fridge ID
     * @param itemId The unique item instance ID to delete
     */
    suspend fun deleteItem(
        fridgeId: String,
        itemId: String
    ) {
        try {
            firestore.collection(FirestoreCollections.FRIDGES)
                .document(fridgeId)
                .collection(FirestoreCollections.ITEMS)
                .document(itemId)
                .delete()
                .await()

            Timber.d("Deleted item: $itemId")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting item")
            throw e
        }
    }

    /**
     * Moves an item instance from one fridge to another within the same household.
     * Verifies permissions before performing the move operation.
     *
     * @param sourceFridgeId Source fridge ID
     * @param targetFridgeId Target fridge ID
     * @param itemId The unique item instance ID to move
     * @throws IllegalStateException if user is not logged in, item not found, fridges are in different households, or user lacks permission
     */
    suspend fun moveItem(
        sourceFridgeId: String,
        targetFridgeId: String,
        itemId: String
    ) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        try {
            // Verify both fridges exist and belong to the same household
            val sourceFridge =
                fridgeRepository.getRawFridgeById(sourceFridgeId)
                    ?: throw IllegalStateException("Source fridge not found")
            val targetFridge =
                fridgeRepository.getRawFridgeById(targetFridgeId)
                    ?: throw IllegalStateException("Target fridge not found")

            if (sourceFridge.householdId != targetFridge.householdId) {
                throw IllegalStateException("Cannot move items between fridges in different households")
            }

            // Verify user is a member of the household
            val household =
                householdRepository.getHouseholdById(sourceFridge.householdId)
                    ?: throw IllegalStateException("Household not found")

            if (!household.memberRoles.containsKey(currentUser.uid)) {
                throw IllegalStateException("You don't have permission to move items in this household")
            }

            // Get the item from source fridge
            val itemDoc =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(sourceFridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .document(itemId)
                    .get()
                    .await()

            val item =
                itemDoc.toObject(Item::class.java)
                    ?: throw IllegalStateException("Item not found")

            // Create new item in target fridge with same data but updated metadata
            // Use FieldValue.serverTimestamp() to set fresh timestamp values
            val newItem =
                hashMapOf<String, Any?>(
                    FirestoreFields.UPC to item.upc,
                    FirestoreFields.EXPIRATION_DATE to item.expirationDate,
                    FirestoreFields.ADDED_BY to currentUser.uid,
                    FirestoreFields.LAST_UPDATED_BY to currentUser.uid,
                    FirestoreFields.ADDED_AT to FieldValue.serverTimestamp(),
                    FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                )

            // Use batch to ensure atomicity
            val batch = firestore.batch()

            // Add to target fridge
            val targetRef =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(targetFridgeId)
                    .collection(FirestoreCollections.ITEMS)
                    .document() // Auto-generate ID

            batch.set(targetRef, newItem)

            // Delete from source fridge
            batch.delete(itemDoc.reference)

            batch.commit().await()

            Timber.d("Moved item $itemId from $sourceFridgeId to $targetFridgeId")
        } catch (e: Exception) {
            Timber.e(e, "Error moving item")
            throw e
        }
    }

    // ========================================
    // SHOPPING SESSION OPERATIONS
    // ========================================

    /**
     * Completes a shopping session by moving items from shopping list to fridges.
     *
     * For each item in the shopping list that has user pickup data:
     * - Creates individual item instances in the target fridge (one per quantity obtained)
     * - Removes the item from the shopping list after successful creation
     *
     * This operation uses batched writes for atomicity within each fridge, but processes
     * fridges sequentially to avoid exceeding Firestore batch limits.
     *
     * @param fridgeId Target fridge ID where items will be added
     * @param shoppingListItems List of shopping list items to process
     * @param currentUserId ID of the user completing the shopping session
     * @throws IllegalStateException if user data is invalid or fridge has no householdId
     * @throws Exception if Firestore operations fail
     */
    suspend fun completeShoppingSession(
        fridgeId: String,
        shoppingListItems: List<ShoppingListItem>,
        currentUserId: String
    ) {
        try {
            val fridge =
                fridgeRepository.getFridgeById(fridgeId)
                    ?: throw IllegalStateException("Fridge not found")

            val householdId = fridge.householdId

            val batch = firestore.batch()
            val shoppingListRef =
                firestore.collection(FirestoreCollections.HOUSEHOLDS)
                    .document(householdId)
                    .collection(FirestoreCollections.SHOPPING_LIST)

            val itemsRef =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .document(fridgeId)
                    .collection(FirestoreCollections.ITEMS)

            shoppingListItems.forEach { item ->
                val userObtained = item.obtainedBy[currentUserId]?.toInt() ?: 0
                val userTargetFridge = item.targetFridgeId[currentUserId]

                // Only process if user has picked up items for this fridge
                if (userObtained > 0 && userTargetFridge == fridgeId) {
                    // Create individual item instances for each unit obtained
                    repeat(userObtained) {
                        val newItemRef = itemsRef.document() // Auto-generate ID
                        // Use HashMap with FieldValue.serverTimestamp() to match security rules
                        val newItemData =
                            hashMapOf<String, Any?>(
                                FirestoreFields.UPC to item.upc,
                                FirestoreFields.EXPIRATION_DATE to null,
                                FirestoreFields.ADDED_BY to currentUserId,
                                FirestoreFields.LAST_UPDATED_BY to currentUserId,
                                FirestoreFields.ADDED_AT to FieldValue.serverTimestamp(),
                                FirestoreFields.LAST_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        batch.set(newItemRef, newItemData)
                    }

                    Timber.d("Adding $userObtained instance(s) of ${item.upc} to fridge $fridgeId")

                    // Check if all users have obtained their full quantity
                    val totalObtained = item.obtainedBy.values.sumOf { it.toInt() }
                    if (totalObtained >= item.quantity) {
                        // Remove from shopping list if complete
                        batch.delete(shoppingListRef.document(item.upc))
                        Timber.d("Removing fully obtained item ${item.upc} from shopping list")
                    } else {
                        // Update shopping list to reflect this user's pickup
                        val updates =
                            mapOf(
                                "${FirestoreFields.OBTAINED_BY}.$currentUserId" to FieldValue.delete(),
                                "${FirestoreFields.TARGET_FRIDGE_ID}.$currentUserId" to FieldValue.delete()
                            )
                        batch.update(shoppingListRef.document(item.upc), updates)
                        Timber.d("Updated shopping list for partially obtained item ${item.upc}")
                    }
                }
            }

            batch.commit().await()
            Timber.d("Shopping session completed for fridge $fridgeId")
        } catch (e: Exception) {
            Timber.e(e, "Error completing shopping session")
            throw e
        }
    }
}
