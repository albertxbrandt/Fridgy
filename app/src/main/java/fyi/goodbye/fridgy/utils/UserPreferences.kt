package fyi.goodbye.fridgy.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth

/**
 * Utility class for storing user preferences using SharedPreferences.
 *
 * Stores persistent user settings like:
 * - Last selected household ID (for quick app startup)
 *
 * Preferences are user-specific to avoid cross-user conflicts on shared devices.
 */
class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val auth = FirebaseAuth.getInstance()

    /**
     * Gets the preference key for the current user.
     * Makes preferences user-specific to avoid conflicts on shared devices.
     */
    private fun userKey(baseKey: String): String {
        val uid = auth.currentUser?.uid ?: return baseKey
        return "${baseKey}_$uid"
    }

    /**
     * Gets the ID of the last selected household for the current user.
     * @return The household ID, or null if none was selected.
     */
    fun getLastSelectedHouseholdId(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return prefs.getString(userKey(KEY_LAST_HOUSEHOLD_ID), null)
    }

    /**
     * Saves the ID of the selected household for quick access on next app launch.
     * @param householdId The ID of the household to save.
     */
    fun setLastSelectedHouseholdId(householdId: String) {
        val uid = auth.currentUser?.uid ?: return
        prefs.edit {
            putString(userKey(KEY_LAST_HOUSEHOLD_ID), householdId)
        }
    }

    /**
     * Clears the last selected household (e.g., when user leaves or household is deleted).
     */
    fun clearLastSelectedHouseholdId() {
        val uid = auth.currentUser?.uid ?: return
        prefs.edit {
            remove(userKey(KEY_LAST_HOUSEHOLD_ID))
        }
    }

    companion object {
        private const val PREFS_NAME = "fridgy_user_prefs"
        private const val KEY_LAST_HOUSEHOLD_ID = "last_household_id"

        @Volatile
        private var instance: UserPreferences? = null

        /**
         * Gets the singleton instance of UserPreferences.
         */
        fun getInstance(context: Context): UserPreferences {
            return instance ?: synchronized(this) {
                instance ?: UserPreferences(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
