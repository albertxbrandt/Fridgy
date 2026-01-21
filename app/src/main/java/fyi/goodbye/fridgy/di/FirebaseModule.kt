package fyi.goodbye.fridgy.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Firebase service instances.
 *
 * This module provides singleton instances of all Firebase services used in Fridgy:
 * - [FirebaseFirestore] for database operations
 * - [FirebaseAuth] for user authentication
 * - [FirebaseStorage] for file/image storage
 * - [FirebaseMessaging] for push notifications
 *
 * All instances are scoped as singletons to ensure consistent state across
 * the application and to leverage Firebase's internal connection pooling.
 *
 * ## Usage
 * These dependencies are automatically available for constructor injection
 * in any class managed by Hilt (ViewModels, Repositories, etc.).
 *
 * @see RepositoryModule For repository bindings that consume these Firebase instances
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provides the Firebase Firestore instance for database operations.
     *
     * @return The singleton [FirebaseFirestore] instance.
     */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Provides the Firebase Auth instance for authentication operations.
     *
     * @return The singleton [FirebaseAuth] instance.
     */
    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Provides the Firebase Storage instance for file operations.
     *
     * @return The singleton [FirebaseStorage] instance.
     */
    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Provides the Firebase Messaging instance for push notifications.
     *
     * @return The singleton [FirebaseMessaging] instance.
     */
    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
