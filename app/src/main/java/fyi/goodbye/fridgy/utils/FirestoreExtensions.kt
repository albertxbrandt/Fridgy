package fyi.goodbye.fridgy.utils

import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Firestore extension functions to reduce boilerplate code across repositories.
 */

/**
 * Converts a Firestore [Query] to a [Flow] that emits real-time updates.
 *
 * This extension wraps the Firestore snapshot listener pattern in a [callbackFlow],
 * providing a clean way to observe query results as a Kotlin Flow.
 *
 * @param T The type of objects to deserialize from Firestore documents.
 * @param clazz The class of [T] to use for deserialization.
 * @return A [Flow] that emits a list of [T] whenever the query results change.
 *
 * @throws Exception if there's a Firestore error (closes the flow with the error).
 *
 * Usage example:
 * ```kotlin
 * fun getCategories(): Flow<List<Category>> =
 *     categoriesCollection
 *         .orderBy("order")
 *         .asFlow(Category::class.java)
 * ```
 */
fun <T> Query.asFlow(clazz: Class<T>): Flow<List<T>> =
    callbackFlow {
        val listener = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(clazz)
            } ?: emptyList()
            trySend(items).isSuccess
        }
        awaitClose { listener.remove() }
    }

/**
 * Inline reified version of [asFlow] for cleaner syntax.
 *
 * Usage example:
 * ```kotlin
 * fun getCategories(): Flow<List<Category>> =
 *     categoriesCollection
 *         .orderBy("order")
 *         .asFlow<Category>()
 * ```
 */
inline fun <reified T> Query.asFlow(): Flow<List<T>> = asFlow(T::class.java)
