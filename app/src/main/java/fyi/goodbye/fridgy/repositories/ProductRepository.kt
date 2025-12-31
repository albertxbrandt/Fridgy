package fyi.goodbye.fridgy.repositories

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.models.Product
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing a private, crowdsourced barcode database.
 * 
 * It stores product metadata in Firestore and product images in Firebase Storage.
 */
class ProductRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val productsCollection = firestore.collection("products")

    /**
     * Fetches product metadata from the internal database using a barcode.
     */
    suspend fun getProductInfo(upc: String): Product? {
        return try {
            val doc = productsCollection.document(upc).get().await()
            if (doc.exists()) {
                doc.toObject(Product::class.java)?.copy(upc = doc.id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ProductRepo", "Error fetching product $upc: ${e.message}")
            null
        }
    }

    /**
     * Saves a product to the global database, including uploading its image to Storage.
     * 
     * @param product The product metadata.
     * @param imageUri The local URI of the captured image to upload.
     */
    suspend fun saveProductWithImage(product: Product, imageUri: Uri?): Product {
        var finalImageUrl = product.imageUrl

        // 1. Upload Image to Firebase Storage if provided
        if (imageUri != null) {
            try {
                val storageRef = storage.reference.child("products/${product.upc}.jpg")
                storageRef.putFile(imageUri).await()
                finalImageUrl = storageRef.downloadUrl.await().toString()
                Log.d("ProductRepo", "Image uploaded successfully: $finalImageUrl")
            } catch (e: Exception) {
                Log.e("ProductRepo", "Image upload failed for ${product.upc}: ${e.message}")
                // Continue saving without image if upload fails
            }
        }

        // 2. Save Product metadata to Firestore
        val updatedProduct = product.copy(imageUrl = finalImageUrl)
        try {
            productsCollection.document(product.upc).set(updatedProduct).await()
            Log.d("ProductRepo", "Product metadata saved: ${product.upc}")
        } catch (e: Exception) {
            Log.e("ProductRepo", "Error saving product ${product.upc}: ${e.message}")
            throw e
        }

        return updatedProduct
    }

    /**
     * Legacy save function for backward compatibility.
     */
    suspend fun saveProductInfo(product: Product) {
        saveProductWithImage(product, null)
    }
}
