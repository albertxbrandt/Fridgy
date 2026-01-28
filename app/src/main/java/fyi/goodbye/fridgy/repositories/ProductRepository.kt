package fyi.goodbye.fridgy.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import fyi.goodbye.fridgy.constants.FirestoreCollections
import fyi.goodbye.fridgy.constants.FirestoreFields
import fyi.goodbye.fridgy.constants.StoragePaths
import fyi.goodbye.fridgy.models.Product
import fyi.goodbye.fridgy.utils.LruCache
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Repository for managing a private, crowdsourced barcode database.
 *
 * Handles product information retrieval, caching, image uploads, and
 * crowdsourced product database management.
 *
 * @param context Application context for file operations. Must be Application context
 *                to avoid memory leaks.
 * @param firestore The Firestore instance for database operations.
 * @param storage The Storage instance for image uploads.
 */
class ProductRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val productsCollection = firestore.collection(FirestoreCollections.PRODUCTS)

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
        return try {
            // Read EXIF orientation first
            val exifOrientation = getExifOrientation(uri)

            // Decode bitmap
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Timber.e("Failed to decode bitmap from URI")
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

            Timber.d("Image compressed: ${compressedBytes.size / 1024}KB (${scaledWidth}x$scaledHeight)")
            compressedBytes
        } catch (e: Exception) {
            Timber.e("Image compression failed: ${e.message}")
            null
        }
    }

    /**
     * Reads EXIF orientation from image URI.
     */
    private fun getExifOrientation(uri: Uri): Int {
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
            Timber.e("Failed to read EXIF orientation: ${e.message}")
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
            Timber.d("Applied EXIF rotation: $orientation")
            rotated
        } catch (e: Exception) {
            Timber.e("Failed to rotate bitmap: ${e.message}")
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
     * Tries Firestore cache first for instant loading, then falls back to server.
     */
    suspend fun getProductInfo(upc: String): Product? {
        productCache[upc]?.let { return it }

        return try {
            // Try cache first for instant loading
            var doc = productsCollection.document(upc).get(Source.CACHE).await()

            // If not in cache, fetch from server
            if (!doc.exists()) {
                doc = productsCollection.document(upc).get(Source.SERVER).await()
            }

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
            Timber.e("Error fetching product $upc: ${e.message}")
            null
        }
    }

    /**
     * Fetches product metadata from server, bypassing cache to get latest data.
     * Use this when you need guaranteed fresh data (e.g., after product updates).
     * Includes retry logic for eventual consistency.
     */
    suspend fun getProductInfoFresh(upc: String): Product? {
        return try {
            // First try: fetch from server
            var doc = productsCollection.document(upc).get(Source.SERVER).await()

            // If not found on server, retry with DEFAULT source (includes cache)
            // This helps with Firestore's eventual consistency
            if (!doc.exists()) {
                Timber.d("Product $upc not found on server, trying default source")
                doc = productsCollection.document(upc).get(Source.DEFAULT).await()
            }

            if (doc.exists()) {
                val product = doc.toObject(Product::class.java)?.copy(upc = doc.id)
                if (product != null) {
                    // Update cache with fresh data
                    productCache[upc] = product
                }
                product
            } else {
                // Remove from cache if it no longer exists
                productCache.remove(upc)
                null
            }
        } catch (e: Exception) {
            Timber.e("Error fetching fresh product $upc: ${e.message}")
            // Fall back to cached version on network error
            productCache[upc]
        }
    }

    /**
     * Injects a product into the cache manually. Useful for optimistic UI updates.
     */
    fun injectToCache(product: Product) {
        productCache[product.upc] = product
    }

    /**
     * Fetches multiple products by their UPCs in batches.
     * Uses cache first, then batches Firestore queries (max 10 per query due to 'in' limit).
     * Returns a map of UPC to Product for easy lookup.
     *
     * PERFORMANCE: Reduces N queries to ceil(N/10) queries.
     *
     * @param upcs List of UPCs to fetch
     * @return Map of UPC to Product, empty map if all fail
     */
    suspend fun getProductsByUpcs(upcs: List<String>): Map<String, Product> {
        if (upcs.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Product>()
        val missingUpcs = mutableListOf<String>()

        // Check cache first
        upcs.distinct().forEach { upc ->
            val cached = productCache[upc]
            if (cached != null) {
                result[upc] = cached
            } else {
                missingUpcs.add(upc)
            }
        }

        // Batch fetch missing products (max 10 per query)
        if (missingUpcs.isNotEmpty()) {
            try {
                missingUpcs.chunked(10).forEach { chunk ->
                    val snapshot =
                        productsCollection
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                            .get()
                            .await()

                    snapshot.documents.forEach { doc ->
                        doc.toObject(Product::class.java)?.let { product ->
                            val productWithUpc = product.copy(upc = doc.id)
                            result[doc.id] = productWithUpc
                            // Cache for future use
                            productCache[doc.id] = productWithUpc
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error batch fetching products: ${e.message}")
            }
        }

        Timber.d(
            "Batch fetched ${result.size} products (${result.size - missingUpcs.size} from cache, ${missingUpcs.size} from network)"
        )
        return result
    }

    /**
     * Saves a product to the global database.
     * Optimistically updates the cache before hitting the network.
     * Automatically generates searchTokens for efficient searching.
     */
    suspend fun saveProductWithImage(
        product: Product,
        imageUri: Uri?
    ): Product {
        // Generate search tokens if not already present
        val searchTokens =
            if (product.searchTokens.isEmpty()) {
                Product.generateSearchTokens(product.name, product.brand)
            } else {
                product.searchTokens
            }

        val productWithTokens = product.copy(searchTokens = searchTokens)

        // Update cache immediately for optimistic UI
        productCache[product.upc] = productWithTokens

        var finalImageUrl = productWithTokens.imageUrl
        var productToSave = productWithTokens

        // 1. Upload Image to Firebase Storage FIRST if provided
        if (imageUri != null) {
            try {
                val storageRef = storage.reference.child(StoragePaths.productImage(product.upc))

                // Compress image before upload
                val compressedBytes = compressImage(imageUri)

                if (compressedBytes != null) {
                    // Upload compressed bytes instead of raw file
                    storageRef.putBytes(compressedBytes).await()
                    finalImageUrl = storageRef.downloadUrl.await().toString()

                    // Create updated product with Storage URL
                    productToSave = productWithTokens.copy(imageUrl = finalImageUrl)
                    productCache[product.upc] = productToSave
                    Timber.d("Compressed product image uploaded: ${product.upc}")
                } else {
                    Timber.e("Image compression returned null, skipping upload")
                }
            } catch (e: Exception) {
                Timber.e("Image upload failed for ${product.upc}: ${e.message}")
                // Continue with empty imageUrl on upload failure
            }
        }

        // 2. Save metadata to Firestore with proper Storage URL (or empty string)
        try {
            productsCollection.document(product.upc).set(productToSave).await()
            Timber.d("Product metadata saved with ${searchTokens.size} search tokens: ${product.upc}")
        } catch (e: Exception) {
            Timber.e("Failed to save product metadata: ${e.message}")
        }

        return productCache[product.upc] ?: productToSave
    }

    suspend fun saveProductInfo(product: Product) {
        saveProductWithImage(product, null)
    }

    /**
     * Search products by name using a three-tiered approach:
     * 1. Array-contains query on searchTokens (most efficient for products with tokens)
     * 2. Firestore range query for prefix matching on name field
     * 3. Fallback to recent products with client-side filtering
     *
     * Note: Firestore doesn't support full-text search natively. This implementation:
     * - Uses searchTokens array-contains for efficient word/prefix matching
     * - Falls back to range queries for older products without tokens
     * - Client-side filters only a limited subset (last 100 products)
     *
     * For production-grade search with typo tolerance, consider:
     * - Algolia (https://www.algolia.com/)
     * - Typesense (https://typesense.org/)
     * - Meilisearch (https://www.meilisearch.com/)
     *
     * @param query The search query (case insensitive)
     * @return List of matching products, limited to 20 results
     */
    suspend fun searchProductsByName(query: String): List<Product> {
        if (query.isBlank()) return emptyList()

        return try {
            val queryLower = query.trim().lowercase()

            // Approach 1: Use searchTokens array-contains (best for products with tokens)
            // This requires a composite index: searchTokens (ARRAY) + lastUpdated (DESCENDING)
            val tokenResults =
                productsCollection
                    .whereArrayContains(FirestoreFields.SEARCH_TOKENS, queryLower)
                    .orderBy(FirestoreFields.LAST_UPDATED, Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toObject(Product::class.java)?.copy(upc = doc.id)
                    }

            // If we found results with tokens, return them
            if (tokenResults.isNotEmpty()) {
                Timber.d("Search found ${tokenResults.size} products via searchTokens")
                return tokenResults
            }

            // Approach 2: Firestore range query for prefix matching
            // Works only for queries that start with the product name
            // Requires composite index: name (ASCENDING) + lastUpdated (DESCENDING)
            // Example: "choc" matches "Chocolate Milk" but not "Dark Chocolate"
            val prefixResults =
                productsCollection
                    .orderBy(FirestoreFields.NAME)
                    .startAt(queryLower)
                    .endAt(queryLower + "\uf8ff") // Unicode high character for range end
                    .limit(20)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toObject(Product::class.java)?.copy(upc = doc.id)
                    }
                    .sortedByDescending { it.lastUpdated } // Sort by recency

            if (prefixResults.isNotEmpty()) {
                Timber.d("Search found ${prefixResults.size} products via prefix matching")
                return prefixResults
            }

            // Approach 3: Fallback - query recent 100 products, filter client-side
            // This is safe because we're limiting the query size
            Timber.d("Falling back to recent products search")
            val recentResults =
                productsCollection
                    .orderBy(FirestoreFields.LAST_UPDATED, Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        doc.toObject(Product::class.java)?.copy(upc = doc.id)
                    }
                    .filter { product ->
                        product.name.lowercase().contains(queryLower) ||
                            product.brand.lowercase().contains(queryLower) ||
                            product.upc.contains(query, ignoreCase = true)
                    }
                    .take(20)

            Timber.d("Search completed: ${recentResults.size} results")
            recentResults
        } catch (e: Exception) {
            Timber.e("Search failed: ${e.message}")
            emptyList()
        }
    }
}
