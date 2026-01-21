package fyi.goodbye.fridgy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

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
 * @see MainActivity For the main activity entry point annotated with [@AndroidEntryPoint]
 */
@HiltAndroidApp
class FridgyApplication : Application()
