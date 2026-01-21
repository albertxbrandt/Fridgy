package fyi.goodbye.fridgy.ui.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for magic link intents.
 *
 * This singleton class receives deep link intents from MainActivity and exposes them
 * as a StateFlow that MagicLinkViewModel can observe. This allows the ViewModel to
 * process the magic link when the user clicks on it from their email.
 *
 * The flow pattern allows the magic link to be handled even if the app was killed
 * and restarted from the deep link.
 */
@Singleton
class MagicLinkHandler @Inject constructor() {
    
    private val _pendingIntent = MutableStateFlow<Intent?>(null)
    
    /**
     * Flow of pending magic link intents.
     * MagicLinkViewModel observes this to handle incoming magic links.
     */
    val pendingIntent: StateFlow<Intent?> = _pendingIntent.asStateFlow()
    
    /**
     * Called by MainActivity when a new intent is received that could be a magic link.
     * 
     * @param intent The incoming intent from the deep link.
     */
    fun handleIntent(intent: Intent?) {
        _pendingIntent.value = intent
    }
    
    /**
     * Clears the pending intent after it has been processed.
     * This prevents the same magic link from being processed multiple times.
     */
    fun clearPendingIntent() {
        _pendingIntent.value = null
    }
}
