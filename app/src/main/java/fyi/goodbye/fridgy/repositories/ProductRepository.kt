package fyi.goodbye.fridgy.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.utils.LruCache
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Repository for managing a private, crowdsourced barcode database.
 * 
 * @param context Application context for file operations. Must be Application context
 *                to avoid memory leaks. Pass null for read-only operations.
 */
class ProductRepository(private val context: Context? = null) {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val productsCollection = firestore.collection("products")

    companion object {
        /** Maximum number of products to cache. */
        private const val PRODUCT_CACHE_SIZE = 200

        /**
         * LRU cache for products, shared across repository instances.
         * Evicts least recently used products when capacity is reached.
         */
        private val productCache = LruCache<String, Product>(PRODUCT_CACHE_SIZE)

        // Image optimization constants
        private const val MAX_IMAGE_WIDTH = 1024
        private const val MAX_IMAGE_HEIGHT = 1024
        private const val JPEG_QUALITY = 85 // 85% quality provides good balance
    }

    /**
     * Compresses an image from URI to optimized JPEG bytes.
     * Resizes to max 1024x1024 while maintaining aspect ratio.
     * Compresses to 85% JPEG quality.
     * Handles EXIF orientation to ensure correct image rotation.
     * Target: ~100-300KB per image (down from 1.5MB)
     */
    private fun compressImage(uri: Uri): ByteArray? {
        if (context == null) return null

        return try {
            // Read EXIF orientation first
            val exifOrientation = getExifOrientation(uri)

            // Decode bitmap
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Log.e("ProductRepo", "Failed to decode bitmap from URI")
                return null
            }

            // Apply EXIF rotation to bitmap
            val rotatedBitmap = rotateImageIfRequired(originalBitmap, exifOrientation)

            // Calculate scaled dimensions maintaining aspect ratio
            val (scaledWidth, scaledHeight) =
                calculateScaledDimensions(
                    rotatedBitmap.width,
                    rotatedBitmap.height
                )

            // Resize bitmap
            val scaledBitmap =
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    scaledWidth,
                    scaledHeight,
                    true
                )

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Cleanup
            originalBitmap.recycle()
            if (rotatedBitmap != originalBitmap) {
                rotatedBitmap.recycle()
            }
            if (scaledBitmap != rotatedBitmap) {
                scaledBitmap.recycle()
            }
            outputStream.close()

            Log.d("ProductRepo", "Image compressed: ${compressedBytes.size / 1024}KB (${scaledWidth}x$scaledHeight)")
            compressedBytes
        } catch (e: Exception) {
            Log.e("ProductRepo", "Image compression failed: ${e.message}")
            null
        }
    }

    /**
     * Reads EXIF orientation from image URI.
     */
    private fun getExifOrientation(uri: Uri): Int {
        if (context == null) return ExifInterface.ORIENTATION_NORMAL

        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ExifInterface.ORIENTATION_NORMAL
            val exif = ExifInterface(inputStream)
            val orientation =
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            inputStream.close()
            orientation
        } catch (e: Exception) {
            Log.e("ProductRepo", "Failed to read EXIF orientation: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Rotates bitmap according to EXIF orientation.
     */
    private fun rotateImageIfRequired(
        bitmap: Bitmap,
        orientation: Int
    ): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap // No rotation needed
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            Log.d("ProductRepo", "Applied EXIF rotation: $orientation")
            rotated
        } catch (e: Exception) {
            Log.e("ProductRepo", "Failed to rotate bitmap: ${e.message}")
            bitmap
        }
    }

    /**
     * Calculates scaled dimensions while maintaining aspect ratio.
     */
    private fun calculateScaledDimensions(
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return width to height
        }

        val aspectRatio = width.toFloat() / height.toFloat()

        return if (width > height) {
            // Landscape
            val scaledWidth = MAX_IMAGE_WIDTH
            val scaledHeight = (scaledWidth / aspectRatio).toInt()
            scaledWidth to scaledHeight
        } else {
            // Portrait
            val scaledHeight = MAX_IMAGE_HEIGHT
            val scaledWidth = (scaledHeight * aspectRatio).toInt()
            scaledWidth to scaledHeight
        }
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
    suspend fun saveProductWithImage(
        product: Product,
        imageUri: Uri?
    ): Product {
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

                // Compress image before upload
                val compressedBytes = compressImage(imageUri)

                if (compressedBytes != null) {
                    // Upload compressed bytes instead of raw file
                    storageRef.putBytes(compressedBytes).await()
                    finalImageUrl = storageRef.downloadUrl.await().toString()

                    // 3. Update metadata with the final image URL
                    val updatedProduct = product.copy(imageUrl = finalImageUrl)
                    productsCollection.document(product.upc).set(updatedProduct)
                    productCache[product.upc] = updatedProduct
                    Log.d("ProductRepo", "Compressed product image uploaded: ${product.upc}")
                    return updatedProduct
                } else {
                    Log.e("ProductRepo", "Image compression returned null, skipping upload")
                }
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
