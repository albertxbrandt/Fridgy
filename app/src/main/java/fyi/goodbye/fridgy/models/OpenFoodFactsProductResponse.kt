package fyi.goodbye.fridgy.data.api.model

import com.google.gson.annotations.SerializedName

/**
 * Top-level response structure from the Open Food Facts product API endpoint.
 */
data class OpenFoodFactsProductResponse(
    // The barcode code sent in the request.
    val code: String,

    // Status: 1 if product found, 0 if product not found.
    val status: Int,

    // Detailed status message (e.g., "product found").
    @SerializedName("status_verbose")
    val statusVerbose: String,

    // The actual product object, nullable if status is 0.
    val product: Product?
)

/**
 * Nested data class for the actual product information.
 * Fields often use snake_case in the JSON, requiring @SerializedName annotations.
 */
data class Product(
    // The common name of the product (e.g., "Whole Milk").
    @SerializedName("product_name")
    val productName: String?,

    // Brands (e.g., "Great Value").
    @SerializedName("brands")
    val brands: String?,

    // URL of the main product image.
    @SerializedName("image_url")
    val imageUrl: String?,

    // Quantity text (e.g., "1 L", "300 g").
    @SerializedName("quantity")
    val quantityText: String?,

    // User-friendly serving size text.
    @SerializedName("serving_size")
    val servingSize: String?

    // We omit many other fields (nutrition, ingredients) for simplicity.
)