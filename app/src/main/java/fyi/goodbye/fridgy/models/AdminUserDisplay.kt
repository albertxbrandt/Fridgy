package fyi.goodbye.fridgy.models

import java.util.Date

/**
 * Data Transfer Object for admin panel display.
 * Combines private User data with public UserProfile data.
 * Only used by admin panel to show complete user information.
 */
data class AdminUserDisplay(
    val uid: String,
    val username: String,
    val email: String,
    val createdAt: Date?
)
