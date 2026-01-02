package fyi.goodbye.fridgy.repositories

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.models.Product
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing a private, crowdsourced barcode database.
 */
class ProductRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val productsCollection = firestore.collection("products")

    companion object {
        // Shared cache across all repository instances to ensure data persistence
        private val productCache = mutableMapOf<String, Product>()
    }

    /**
     * Fetches product metadata from the internal database using a barcode.
     * Uses a local cache to speed up subsequent requests.
     */
    suspend fun getProductInfo(upc: String): Product? {
        productCache[upc]?.let { return it }

        return try {
            val doc = productsCollection.document(upc).get().await()
            if (doc.exists()) {
                val product = doc.toObject(Product::class.java)?.copy(upc = doc.id)
                if (product != null) {
                    productCache[upc] = product
                }
                product
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ProductRepo", "Error fetching product $upc: ${e.message}")
            null
        }
    }

    /**
     * Injects a product into the cache manually. Useful for optimistic UI updates.
     */
    fun injectToCache(product: Product) {
        productCache[product.upc] = product
    }

    /**
     * Saves a product to the global database.
     * Optimistically updates the cache before hitting the network.
     */
    suspend fun saveProductWithImage(product: Product, imageUri: Uri?): Product {
        // Update cache immediately
        productCache[product.upc] = product

        // 1. Save metadata (fire-and-forget for local cache benefit)
        val saveTask = productsCollection.document(product.upc).set(product)
        
        // We don't await the metadata save if we want instant UI, 
        // Firestore's local persistence will handle the immediate fetch.
        // However, we still want to handle the image upload in the background.

        var finalImageUrl = product.imageUrl

        // 2. Upload Image to Firebase Storage if provided
        if (imageUri != null) {
            try {
                val storageRef = storage.reference.child("products/${product.upc}.jpg")
                storageRef.putFile(imageUri).await()
                finalImageUrl = storageRef.downloadUrl.await().toString()
                
                // 3. Update metadata with the final image URL
                val updatedProduct = product.copy(imageUrl = finalImageUrl)
                productsCollection.document(product.upc).set(updatedProduct)
                productCache[product.upc] = updatedProduct
                Log.d("ProductRepo", "Product image uploaded and metadata updated: ${product.upc}")
                return updatedProduct
            } catch (e: Exception) {
                Log.e("ProductRepo", "Image upload failed for ${product.upc}: ${e.message}")
            }
        }

        return productCache[product.upc] ?: product
    }

    suspend fun saveProductInfo(product: Product) {
        saveProductWithImage(product, null)
    }
}
