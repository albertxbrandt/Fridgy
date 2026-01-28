package fyi.goodbye.fridgy.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.User
import fyi.goodbye.fridgy.models.UserProfile
import fyi.goodbye.fridgy.models.canManageFridges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Repository for managing fridge entities.
 *
 * This repository handles all data operations related to:
 * - Fridge CRUD operations (create, read, update, delete)
 * - Fridge queries (by household, by ID)
 *
 * ## Architecture Notes
 * - Item instance operations have been moved to ItemRepository for better separation of concerns
 * - Shopping list operations are handled by ShoppingListRepository at the household level
 * - User profile fetching is delegated to UserRepository for better separation of concerns
 * - Real-time updates are provided via Kotlin Flows using Firestore snapshot listeners
 *
 * ## Thread Safety
 * - Uses coroutines for async operations
 *
 * @param firestore The Firestore instance for database operations
 * @param auth The Auth instance for user identification
 * @param householdRepository The HouseholdRepository for permission checks
 * @param userRepository The UserRepository for user profile operations
 * @see ItemRepository For item instance operations (add, update, delete, move)
 * @see ShoppingListRepository For household-level shopping list operations
 * @see UserRepository For user profile fetching and caching
 * @see ProductRepository For product information lookup
 */
class FridgeRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val householdRepository: HouseholdRepository,
    private val userRepository: UserRepository
) {
    private var fridgeCache: List<Fridge> = emptyList()

    // ========================================
    // USER PROFILE FETCHING (Delegated to UserRepository)
    // ========================================

    /**
     * Fetches a user by ID from the users collection.
     * Delegates to UserRepository.
     *
     * @param userId The user's Firebase UID.
     * @return User object, or null if not found.
     */
    suspend fun getUserById(userId: String): User? = userRepository.getUserById(userId)

    /**
     * Fetches a user's public profile by ID.
     * Delegates to UserRepository.
     *
     * @param userId The user's Firebase UID.
     * @return UserProfile with public data (username), or null if not found.
     */
    suspend fun getUserProfileById(userId: String): UserProfile? = userRepository.getUserProfileById(userId)

    /**
     * Fetches multiple users' public profiles by their IDs.
     * Delegates to UserRepository.
     *
     * @param userIds List of user Firebase UIDs to fetch.
     * @return Map of userId to UserProfile, empty map if fetch fails.
     */
    suspend fun getUsersByIds(userIds: List<String>): Map<String, UserProfile> = userRepository.getUsersByIds(userIds)

    // ========================================
    // FRIDGE CRUD OPERATIONS
    // ========================================

    /**
     * Converts a Firestore document to a Fridge, handling both old and new formats.
     * This provides backward compatibility during data migration.
     * Note: createdBy field is ignored as fridges are now household-owned.
     */
    private fun DocumentSnapshot.toFridgeCompat(): Fridge? {
        return try {
            this.toObject(Fridge::class.java)?.copy(id = this.id)
        } catch (e: Exception) {
            Timber.e("Error parsing fridge document: ${e.message}")
            return null
        }
    }

    /**
     * Preloads user's fridges from Firestore cache to memory for instant cold-start access.
     * Should be called on app initialization to populate cache before UI loads.
     * This reads from Firestore's local persistence layer without network access.
     *
     * Uses current user's ID to filter fridges from Firestore cache.
     */
    suspend fun preloadFridgesFromCache() {
        try {
            val currentUserId = auth.currentUser?.uid ?: return
            val snapshot =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereArrayContains(FirestoreFields.MEMBERS, currentUserId)
                    .get(Source.CACHE)
                    .await()

            fridgeCache = snapshot.documents.mapNotNull { it.toFridgeCompat() }
            Timber.d("Preloaded ${fridgeCache.size} fridges from cache")
        } catch (e: Exception) {
            Timber.w("Could not preload from cache (likely first run): ${e.message}")
        }
    }

    /**
     * Returns a Flow of all fridges belonging to a household.
     *
     * Emits updates in real-time when fridges are added, modified, or removed.
     * Handles permission errors gracefully by emitting empty list.
     *
     * @param householdId The ID of the household.
     * @return Flow emitting list of fridges, updates in real-time.
     */
    fun getFridgesForHousehold(householdId: String): Flow<List<Fridge>> =
        callbackFlow {
            val fridgesListenerRegistration =
                firestore.collection(FirestoreCollections.FRIDGES)
                    .whereEqualTo(FirestoreFields.HOUSEHOLD_ID, householdId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            // Check if this is a permission error
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                Timber.w(
                                    "Permission denied for household $householdId - user likely removed. Clearing cache."
                                )
                                // Clear any cached fridges from this household
                                fridgeCache = fridgeCache.filter { it.householdId != householdId }
                            } else {
                                Timber.e(e, "Error listening to fridges for household: ${e.message}")
                            }
                            // Send empty list instead of closing with error to prevent app crash
                            trySend(emptyList()).isSuccess
                            return@addSnapshotListener
                        }
                        val fridgesList = snapshot?.documents?.mapNotNull { it.toFridgeCompat() } ?: emptyList()
                        fridgeCache = fridgesList
                        trySend(fridgesList).isSuccess
                    }
            awaitClose { fridgesListenerRegistration.remove() }
        }
            .distinctUntilChanged() // OPTIMIZATION: Prevent duplicate emissions

    /**
     * Fetches a raw Fridge object by ID without user profile resolution.
     * Uses in-memory cache -> Firestore cache -> network.
     *
     * @param fridgeId The ID of the fridge to fetch.
     * @return The Fridge object, or null if not found or on error.
     */
    suspend fun getRawFridgeById(fridgeId: String): Fridge? {
        // Check in-memory cache first
        fridgeCache.find { it.id == fridgeId }?.let { return it }

        return try {
            // Try Firestore cache first
            val cacheDoc =
                try {
                    firestore.collection(FirestoreCollections.FRIDGES)
                        .document(fridgeId)
                        .get(Source.CACHE)
                        .await()
                } catch (e: Exception) {
                    null
                }

            val doc =
                if (cacheDoc?.exists() == true) {
                    cacheDoc
                } else {
                    // Fallback to network
                    firestore.collection(FirestoreCollections.FRIDGES)
                        .document(fridgeId)
                        .get(Source.DEFAULT)
                        .await()
                }

            doc.toFridgeCompat()
        } catch (e: Exception) {
            Timber.e("Error fetching raw fridge: ${e.message}")
            null
        }
    }

    /**
     * Fetches a fridge by ID with optional user profile resolution for display.
     *
     * Uses multi-level caching: in-memory cache -> Firestore cache -> network.
     *
     * @param fridgeId The ID of the fridge to fetch.
     * @param fetchUserDetails Whether to fetch creator's username (default: true).
     * @return DisplayFridge with resolved user info, or null if not found.
     */
    suspend fun getFridgeById(
        fridgeId: String,
        fetchUserDetails: Boolean = true
    ): DisplayFridge? {
        val cachedFridge = fridgeCache.find { it.id == fridgeId }
        val fridge =
            if (cachedFridge != null) {
                cachedFridge
            } else {
                try {
                    // OPTIMIZATION: Try cache first for instant response
                    val cacheDoc =
                        try {
                            firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get(Source.CACHE).await()
                        } catch (e: Exception) {
                            null
                        }

                    if (cacheDoc?.exists() == true) {
                        cacheDoc.toFridgeCompat()
                    } else {
                        // Fallback to network if cache miss
                        val doc = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get(Source.DEFAULT).await()
                        doc.toFridgeCompat()
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: return null

        return DisplayFridge(
            id = fridge.id,
            name = fridge.name,
            type = fridge.type,
            householdId = fridge.householdId,
            createdAt = fridge.createdAt
        )
    }

    /**
     * Creates a new fridge within a household.
     *
     * @param fridgeName Display name for the fridge.
     * @param householdId The ID of the household this fridge belongs to.
     * @param fridgeType Type of storage ("fridge", "freezer", "pantry").
     * @param fridgeLocation Physical location description.
     * @return The newly created Fridge object with generated ID.
     * @throws IllegalStateException if user is not logged in.
     */
    suspend fun createFridge(
        fridgeName: String,
        householdId: String,
        fridgeType: String = "fridge",
        fridgeLocation: String = ""
    ): Fridge {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        val newFridgeDocRef = firestore.collection(FirestoreCollections.FRIDGES).document()
        val fridgeId = newFridgeDocRef.id

        val newFridge =
            Fridge(
                id = fridgeId,
                name = fridgeName,
                type = fridgeType,
                location = fridgeLocation,
                householdId = householdId
                // createdAt set via @ServerTimestamp
            )

        newFridgeDocRef.set(newFridge).await()
        return newFridge
    }

    /**
     * Deletes a fridge and all its items.
     *
     * Performs a batch delete of all items in the fridge's subcollection
     * followed by the fridge document itself.
     *
     * @param fridgeId The ID of the fridge to delete.
     * @throws IllegalStateException if user doesn't have permission.
     */
    suspend fun deleteFridge(fridgeId: String) {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")

        // Get fridge to check household
        val fridgeDoc = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId).get().await()
        val fridge =
            fridgeDoc.toObject(Fridge::class.java)
                ?: throw IllegalStateException("Fridge not found")

        // Check permission
        val household =
            householdRepository.getHouseholdById(fridge.householdId)
                ?: throw IllegalStateException("Household not found")

        val userRole = household.getRoleForUser(currentUser.uid)
        if (!userRole.canManageFridges()) {
            throw IllegalStateException("You don't have permission to delete fridges")
        }

        val fridgeRef = firestore.collection(FirestoreCollections.FRIDGES).document(fridgeId)

        Timber.d("Attempting to read items for fridge: $fridgeId")
        val items =
            try {
                fridgeRef.collection(FirestoreCollections.ITEMS).get().await()
            } catch (e: Exception) {
                Timber.e("Failed to read items subcollection: ${e.message}")
                throw e
            }

        Timber.d("Successfully read ${items.size()} items, creating batch delete")
        val batch = firestore.batch()
        items.documents.forEach { batch.delete(it.reference) }
        batch.delete(fridgeRef)

        Timber.d("Committing batch delete (${items.size()} items + 1 fridge)")
        batch.commit().await()
        Timber.d("Batch delete successful")
    }
}
