package fyi.goodbye.fridgy.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fyi.goodbye.fridgy.repositories.AdminRepository
import fyi.goodbye.fridgy.repositories.CategoryRepository
import fyi.goodbye.fridgy.repositories.FridgeRepository
import fyi.goodbye.fridgy.repositories.HouseholdRepository
import fyi.goodbye.fridgy.repositories.NotificationRepository
import fyi.goodbye.fridgy.repositories.ProductRepository
import fyi.goodbye.fridgy.repositories.UserRepository
import javax.inject.Singleton

/**
 * Hilt module providing repository instances.
 *
 * This module provides singleton instances of all data repositories used in Fridgy.
 * Repositories are the data layer that abstracts Firestore and Storage operations
 * from ViewModels, following the MVVM architecture pattern.
 *
 * ## Singleton Scope
 * All repositories are scoped as singletons to ensure:
 * - Consistent state across the application
 * - Efficient caching (each repository maintains its own cache)
 * - Proper lifecycle management of Firestore listeners
 *
 * ## Dependencies
 * Repositories receive their Firebase dependencies from [FirebaseModule],
 * ensuring proper dependency injection and testability.
 *
 * @see FirebaseModule For Firebase service providers
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    /**
     * Provides the fridge repository for fridge and item operations.
     *
     * @param firestore The Firestore instance for database operations.
     * @param auth The Auth instance for user identification.
     * @param householdRepository The HouseholdRepository for permission checks.
     * @return A singleton [FridgeRepository] instance.
     */
    @Provides
    @Singleton
    fun provideFridgeRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        householdRepository: HouseholdRepository
    ): FridgeRepository = FridgeRepository(firestore, auth, householdRepository)

    /**
     * Provides the household repository for household management.
     *
     * @param firestore The Firestore instance for database operations.
     * @param auth The Auth instance for user identification.
     * @return A singleton [HouseholdRepository] instance.
     */
    @Provides
    @Singleton
    fun provideHouseholdRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): HouseholdRepository = HouseholdRepository(firestore, auth)

    /**
     * Provides the product repository for barcode/product operations.
     *
     * @param context Application context for file operations.
     * @param firestore The Firestore instance for database operations.
     * @param storage The Storage instance for image uploads.
     * @return A singleton [ProductRepository] instance.
     */
    @Provides
    @Singleton
    fun provideProductRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        storage: FirebaseStorage
    ): ProductRepository = ProductRepository(context, firestore, storage)

    /**
     * Provides the user repository for user profile operations.
     *
     * @param firestore The Firestore instance for database operations.
     * @param auth The Auth instance for authentication.
     * @return A singleton [UserRepository] instance.
     */
    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(firestore, auth)

    /**
     * Provides the notification repository for push notifications.
     *
     * @param firestore The Firestore instance for database operations.
     * @param auth The Auth instance for user identification.
     * @param messaging The Messaging instance for FCM operations.
     * @return A singleton [NotificationRepository] instance.
     */
    @Provides
    @Singleton
    fun provideNotificationRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        messaging: FirebaseMessaging
    ): NotificationRepository = NotificationRepository(firestore, auth, messaging)

    /**
     * Provides the category repository for category operations.
     *
     * @param firestore The Firestore instance for database operations.
     * @return A singleton [CategoryRepository] instance.
     */
    @Provides
    @Singleton
    fun provideCategoryRepository(firestore: FirebaseFirestore): CategoryRepository = CategoryRepository(firestore)

    /**
     * Provides the admin repository for admin operations.
     *
     * @param firestore The Firestore instance for database operations.
     * @param auth The Auth instance for user identification.
     * @param storage The Storage instance for cleanup operations.
     * @return A singleton [AdminRepository] instance.
     */
    @Provides
    @Singleton
    fun provideAdminRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        storage: FirebaseStorage
    ): AdminRepository = AdminRepository(firestore, auth, storage)
}
