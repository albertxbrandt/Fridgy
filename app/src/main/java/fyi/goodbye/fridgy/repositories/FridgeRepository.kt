package fyi.goodbye.fridgy.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item
import fyi.goodbye.fridgy.models.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository class responsible for handling all data operations related to Fridges and Items.
 * 
 * This class abstracts the Firestore database logic, providing a clean API for ViewModels
 * to interact with fridge and inventory data. It uses Kotlin Coroutines and Flows for
 * asynchronous and real-time data updates.
 */
class FridgeRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Returns a real-time stream of all fridges where the current user is a member.
     * 
     * Uses [callbackFlow] to listen for Firestore snapshot updates and emits a new list
     * whenever membership or fridge data changes.
     */
    fun getFridgesForCurrentUser(): Flow<List<Fridge>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: return@callbackFlow
        
        val fridgesListenerRegistration = firestore.collection("fridges")
            .whereNotEqualTo("members.$currentUserId", null)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                val fridgesList = snapshot?.documents?.mapNotNull { it.toObject(Fridge::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(fridgesList).isSuccess
            }
        awaitClose { fridgesListenerRegistration.remove() }
    }

    /**
     * Returns a real-time stream of fridges where the current user has a pending invitation.
     */
    fun getInvitesForCurrentUser(): Flow<List<Fridge>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: return@callbackFlow
        
        val invitesListener = firestore.collection("fridges")
            .whereNotEqualTo("pendingInvites.$currentUserId", null)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                val invites = snapshot?.documents?.mapNotNull { it.toObject(Fridge::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(invites).isSuccess
            }
        awaitClose { invitesListener.remove() }
    }

    /**
     * Creates a new fridge in Firestore and adds the current user as the owner and first member.
     * 
     * @param fridgeName The display name for the new fridge.
     * @return The newly created [Fridge] object.
     */
    suspend fun createFridge(fridgeName: String): Fridge {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val userDoc = firestore.collection("users").document(currentUser.uid).get().await()
        val username = userDoc.getString("username") ?: "Unknown"

        val newFridgeDocRef = firestore.collection("fridges").document()
        val fridgeId = newFridgeDocRef.id

        val newFridge = Fridge(
            id = fridgeId,
            name = fridgeName,
            createdBy = currentUser.uid,
            members = mapOf(currentUser.uid to username),
            createdAt = System.currentTimeMillis()
        )

        newFridgeDocRef.set(newFridge).await()
        return newFridge
    }

    /**
     * Invites a user to a fridge using their email address.
     * 
     * @param fridgeId The ID of the fridge to invite the user to.
     * @param email The email address of the user to invite.
     * @throws Exception if user is not found or is already a member/invited.
     */
    suspend fun inviteUserByEmail(fridgeId: String, email: String) {
        val snapshot = firestore.collection("users").whereEqualTo("email", email).get().await()
        val userDoc = snapshot.documents.firstOrNull() ?: throw Exception("User not found.")
        val userUid = userDoc.id
        val username = userDoc.getString("username") ?: "Unknown"
        
        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val fridge = fridgeDoc.toObject(Fridge::class.java) ?: throw Exception("Fridge not found.")
        
        if (fridge.members.containsKey(userUid)) throw Exception("User is already a member.")
        if (fridge.pendingInvites.containsKey(userUid)) throw Exception("Invitation already sent.")
        
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites.$userUid", username)
            .await()
    }

    /**
     * Accepts a pending invitation for the current user.
     * 
     * Uses a transaction to atomically move the user from 'pendingInvites' to 'members'.
     */
    suspend fun acceptInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")
        val userDoc = firestore.collection("users").document(currentUserId).get().await()
        val username = userDoc.getString("username") ?: "Unknown"

        val fridgeRef = firestore.collection("fridges").document(fridgeId)
        
        firestore.runTransaction { transaction ->
            transaction.update(fridgeRef, "members.$currentUserId", username)
            transaction.update(fridgeRef, "pendingInvites.$currentUserId", FieldValue.delete())
        }.await()
    }

    /**
     * Declines a pending invitation for the current user.
     */
    suspend fun declineInvite(fridgeId: String) {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in.")
        firestore.collection("fridges").document(fridgeId)
            .update("pendingInvites.$currentUserId", FieldValue.delete()).await()
    }

    /**
     * Fetches detailed information about a specific fridge, optimized for UI display.
     */
    suspend fun getFridgeById(fridgeId: String): DisplayFridge? {
        val fridgeDoc = firestore.collection("fridges").document(fridgeId).get().await()
        val fridge = fridgeDoc.toObject(Fridge::class.java)?.copy(id = fridgeDoc.id) ?: return null
        
        return DisplayFridge(
            id = fridge.id,
            name = fridge.name,
            createdByUid = fridge.createdBy,
            creatorDisplayName = fridge.members[fridge.createdBy] ?: "Unknown",
            members = fridge.members,
            pendingInvites = fridge.pendingInvites,
            createdAt = fridge.createdAt
        )
    }

    /**
     * Removes the current user from a fridge's membership.
     */
    suspend fun leaveFridge(fridgeId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("fridges").document(fridgeId)
            .update("members.$uid", FieldValue.delete())
            .await()
    }

    /**
     * Returns a real-time stream of all items currently stored in a specific fridge.
     */
    fun getItemsForFridge(fridgeId: String): Flow<List<Item>> = callbackFlow {
        val listener = firestore.collection("fridges").document(fridgeId).collection("items")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                val items = snapshot?.documents?.mapNotNull { it.toObject(Item::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(items).isSuccess
            }
        awaitClose { listener.remove() }
    }

    /**
     * Adds a new grocery item to a specific fridge's inventory.
     */
    suspend fun addItemToFridge(fridgeId: String, item: Item): Item {
        val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in.")
        val ref = firestore.collection("fridges").document(fridgeId).collection("items").document()
        val itemToAdd = item.copy(id = ref.id, addedBy = currentUser.uid, addedAt = System.currentTimeMillis(), lastUpdatedBy = currentUser.uid, lastUpdatedAt = System.currentTimeMillis())
        ref.set(itemToAdd).await()
        return itemToAdd
    }

    /**
     * Permanently deletes a fridge and all its contained items.
     */
    suspend fun deleteFridge(fridgeId: String) {
        val fridgeRef = firestore.collection("fridges").document(fridgeId)
        val items = fridgeRef.collection("items").get().await()
        val batch = firestore.batch()
        items.documents.forEach { batch.delete(it.reference) }
        batch.delete(fridgeRef)
        batch.commit().await()
    }

    /**
     * Fetches basic user information by their unique ID.
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.toObject(User::class.java)?.copy(uid = doc.id)
        } catch (e: Exception) { null }
    }
}
