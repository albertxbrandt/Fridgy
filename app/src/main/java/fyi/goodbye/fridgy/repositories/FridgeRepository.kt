package fyi.goodbye.fridgy.repositories

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.User
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FridgeRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // function to get real-time stream of fridges for the current user
//    fun getFridgesForCurrentUser(): Flow<List<Fridge>> = callbackFlow {
//        val currentUserId = auth.currentUser?.uid
//        if (currentUserId == null) {
//            Log.e("FridgeRepo", "No authenticated user to fetch fridges for.")
//            send(emptyList()) // Emit empty list if no user
//            awaitClose {  }
//            return@callbackFlow
//        }
//
//        val userDocRef = firestore.collection("users").document(currentUserId)
//
//        val userListenerRegistration = userDocRef.addSnapshotListener { userSnapshot, userError ->
//            if (userError != null) {
//                Log.w("FridgeRepo", "Listen failed for user document.", userError)
//                cancel(userError.message ?: "Failed to load user data.", userError)
//                return@addSnapshotListener
//            }
//
//            launch {
//                Log.d("FridgeRepo", "User document snapshot received. Exists: ${userSnapshot?.exists()}") // Check if user doc exists
//
//                if (userSnapshot != null && userSnapshot.exists()) {
//                    val user = userSnapshot.toObject(User::class.java)
//
//                    if (user == null) {
//                        Log.w("FridgeRepo", "User document exists but failed to parse User data class.")
//                        trySend(emptyList()).isSuccess
//                        return@launch
//                    }
//
//                    Log.d("FridgeRepo", "Parsed user: ${user.username}, MemberOfFridges: ${user.memberOfFridges}, OwnerOfFridges: ${user.ownerOfFridges}")
//
//                    val allFridgeIds = (user.memberOfFridges + user.ownerOfFridges).distinct()
//
//                    if (allFridgeIds.isEmpty()) {
//                        trySend(emptyList()).isSuccess
//                        return@launch
//                    }
//
//                    val batchedFridgeIds = allFridgeIds.chunked(10)
//
//                    try {
//                        val allFridgesAcrossBatches = coroutineScope {
//                            val deferredFridges = batchedFridgeIds.map { batch ->
//                                async {
//                                    firestore.collection("fridges")
//                                        .whereIn(FieldPath.documentId(), batch)
//                                        .get()
//                                        .await()
//                                        .documents
//                                        .mapNotNull { it.toObject(Fridge::class.java)?.copy(id = it.id) }
//                                }
//                            }
//                            deferredFridges.awaitAll().flatten()
//                        }
//                        trySend(allFridgesAcrossBatches).isSuccess
//                    } catch (e: Exception) {
//                        Log.w("FridgeRepo", "Error fetching fridges from batches.")
//                        cancel(e.message ?: "Failed to load fridges from batches.")
//                    }
//                } else {
//                    trySend(emptyList()).isSuccess
//                }
//            }
//
//        }
//
//        awaitClose {
//            Log.d("FridgeRepo", "Stopping fridge listener.")
//            userListenerRegistration.remove()
//        }
//    }

    fun getFridgesForCurrentUser(): Flow<List<Fridge>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e("FridgeRepo", "No authenticated user to fetch fridges for.")
            send(emptyList()) // Emit empty list if no user
            awaitClose { } // Close the flow immediately
            return@callbackFlow
        }

        // --- THE KEY CHANGE: Listen directly to the 'fridges' collection ---
        // This listener will trigger whenever a fridge document changes (e.g., name, quantity, members list)
        val fridgesListenerRegistration = firestore.collection("fridges")
            .whereArrayContains("members", currentUserId) // Filter by current user's membership
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FridgeRepo", "Listen failed for fridges by membership.", e)
                    // Propagate error to the flow. This will be caught by .catch in ViewModel.
                    cancel(e.message ?: "Failed to load fridges.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fridgesList = snapshot.documents.mapNotNull { document ->
                        document.toObject(Fridge::class.java)?.copy(id = document.id)
                    }
                    trySend(fridgesList).isSuccess // Send the updated list
                } else {
                    trySend(emptyList()).isSuccess
                }
            }

        // Clean up the listener when the flow is no longer collected
        awaitClose {
            Log.d("FridgeRepo", "Stopping fridges by membership listener.")
            fridgesListenerRegistration.remove()
        }
    }


    suspend fun createFridge(fridgeName: String): Fridge {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            throw IllegalStateException("User not logged in to create a fridge.")
        }

        // Get a new document reference first to get its id
        val newFridgeDocRef = firestore.collection("fridges").document()
        val fridgeId = newFridgeDocRef.id

        // Create the fridge object
        val newFridge = Fridge(
            id = fridgeId,
            name = fridgeName,
            createdBy = currentUser.uid, // Store the UID of the creator
            members = listOf(currentUser.uid), // Creator is automatically a member
            createdAt = System.currentTimeMillis()
        )

        // Add fridge doc to firestore
        newFridgeDocRef.set(newFridge).await()
        Log.d("FridgeRepo", "New fridge created with ID: $fridgeId")

        val userDocRef = firestore.collection("users").document(currentUser.uid)
        userDocRef.update("ownerOfFridges", FieldValue.arrayUnion(fridgeId)).await()

        Log.d("FridgeRepo", "User ${currentUser.uid} updated with new fridge ID: $fridgeId")

        return newFridge;
    }

    suspend fun getUserById(userId: String): User? {
        return try {
            val userDocument = firestore.collection("users").document(userId).get().await()
            if (userDocument.exists()) {
                userDocument.toObject(User::class.java)?.copy(uid = userDocument.id)
            } else {
                Log.d("FridgeRepo", "User document with ID $userId does not exist.")
                null
            }
        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching user with ID $userId: ${e.message}", e)
            null
        }
    }

    suspend fun getFridgeById(fridgeId: String): DisplayFridge? {
        return try {
            val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
            if (fridgeDoc.exists()) {
                val fridge = fridgeDoc.toObject(Fridge::class.java)?.copy(id = fridgeDoc.id)
                if (fridge != null) {
                    val creatorDisplayName = getUserById(fridge.createdBy)?.username ?: "Unknown"

                    DisplayFridge(
                        id = fridge.id,
                        name = fridge.name,
                        createdByUid = fridge.createdBy,
                        creatorDisplayName = creatorDisplayName,
                        members = fridge.members,
                        createdAt = fridge.createdAt
                    )
                } else {
                    null
                }
            } else {
                Log.d("FridgeRepo", "Fridge document with ID $fridgeId does not exist.")
                null
            }

        } catch (e: Exception) {
            Log.e("FridgeRepo", "Error fetching fridge with ID $fridgeId: ${e.message}", e)
            null
        }
    }

    // NEW FUNCTION: getItemsForFridge (real-time stream of items in a specific fridge)
    fun getItemsForFridge(fridgeId: String): Flow<List<Item>> = callbackFlow {
        val itemsListenerRegistration = firestore.collection("fridges")
            .document(fridgeId)
            .collection("items") // Assuming items are a subcollection of fridges
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FridgeRepo", "Listen failed for items in fridge $fridgeId.", e)
                    cancel(e.message ?: "Failed to load items.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val itemsList = snapshot.documents.mapNotNull { document ->
                        document.toObject(Item::class.java)?.copy(id = document.id)
                    }
                    trySend(itemsList).isSuccess
                } else {
                    trySend(emptyList()).isSuccess
                }
            }

        awaitClose {
            Log.d("FridgeRepo", "Stopping items listener for fridge $fridgeId.")
            itemsListenerRegistration.remove()
        }
    }

    // NEW FUNCTION: addItemToFridge
    suspend fun addItemToFridge(fridgeId: String, item: Item): Item {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            throw IllegalStateException("User not logged in to add item.")
        }

        // Get a new document reference for the item within the fridge's subcollection
        val newItemDocRef = firestore.collection("fridges")
            .document(fridgeId)
            .collection("items")
            .document() // Auto-generate ID for the new item

        val itemId = newItemDocRef.id
        val currentTime = System.currentTimeMillis()
        val currentUserId = currentUser.uid // Use UID for addedBy/lastUpdatedBy

        // Create the Item object with auto-generated ID and current user/time
        val itemToAdd = item.copy(
            id = itemId,
            addedBy = currentUserId,
            addedAt = currentTime,
            lastUpdatedBy = currentUserId,
            lastUpdatedAt = currentTime
        )

        // Save the item document to Firestore
        newItemDocRef.set(itemToAdd).await()
        Log.d("FridgeRepo", "New item added to fridge $fridgeId: ${itemToAdd.upc} with ID: $itemId")

        return itemToAdd // Return the created item
    }
}

