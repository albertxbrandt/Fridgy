package fyi.goodbye.fridgy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Main application class for Fridgy.
 *
 * This class serves as the entry point for Hilt dependency injection,
 * enabling the entire application to use constructor injection for
 * ViewModels, Repositories, and other dependencies.
 *
 * The [@HiltAndroidApp] annotation triggers Hilt's code generation,
 * including a base class that serves as the application-level
 * dependency container.
 *
 * Timber is initialized here for debug builds to provide automatic
 * tag generation and cleaner logging throughout the app.
 *
 * @see MainActivity For the main activity entry point annotated with [@AndroidEntryPoint]
 */
@HiltAndroidApp
class FridgyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
