package fyi.goodbye.fridgy.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Utility class for storing user preferences using SharedPreferences.
 * 
 * Stores persistent user settings like:
 * - Last selected household ID (for quick app startup)
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    /**
     * Gets the ID of the last selected household.
     * @return The household ID, or null if none was selected.
     */
    fun getLastSelectedHouseholdId(): String? {
        return prefs.getString(KEY_LAST_HOUSEHOLD_ID, null)
    }
    
    /**
     * Saves the ID of the selected household for quick access on next app launch.
     * @param householdId The ID of the household to save.
     */
    fun setLastSelectedHouseholdId(householdId: String) {
        prefs.edit {
            putString(KEY_LAST_HOUSEHOLD_ID, householdId)
        }
    }
    
    /**
     * Clears the last selected household (e.g., when user leaves or household is deleted).
     */
    fun clearLastSelectedHouseholdId() {
        prefs.edit {
            remove(KEY_LAST_HOUSEHOLD_ID)
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
