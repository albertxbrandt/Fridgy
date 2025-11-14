package fyi.goodbye.fridgy.repositories

import android.util.Log
import fyi.goodbye.fridgy.data.api.model.OpenFoodFactsProductResponse
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import okhttp3.logging.HttpLoggingInterceptor

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): OpenFoodFactsProductResponse
}
class ProductRepository {

    private val api: OpenFoodFactsApi

    // Constants based on documentation
    private val BASE_URL_STAGING = "https://world.openfoodfacts.net/"
    private val USER_AGENT = "FridgyApp/1.0 (dev.fridgy@example.com)" // Use your app's info

    init {
        // Logging interceptor
        val logging = HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        // Custom OkHttpClient configuration
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Add interceptor to include User-Agent and Basic Auth headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    // 1. Add Custom User-Agent (Required for all requests)
                    .header("User-Agent", USER_AGENT)

                // 2. Add Basic Auth for the Staging environment (off:off)
                val credentials = Credentials.basic("off", "off")
                requestBuilder.header("Authorization", credentials)

                chain.proceed(requestBuilder.build())
            }
            .build()

        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            // Use STAGING URL for development
            .baseUrl(BASE_URL_STAGING)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()

        api = retrofit.create(OpenFoodFactsApi::class.java)
    }

    // Suspend function to fetch product details by UPC
    suspend fun getProductByUpc(upc: String): OpenFoodFactsProductResponse {
        return api.getProductByBarcode(upc)
    }
}