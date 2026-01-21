package fyi.goodbye.fridgy.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Centralized date formatting utilities for consistent date display throughout the app.
 *
 * All formatters use [Locale.getDefault()] to respect user's locale settings.
 * Formatters are cached as companion object properties to avoid repeated allocations.
 */
object DateFormatters {

    /**
     * Format: "MMM dd, yyyy" (e.g., "Jan 15, 2024")
     *
     * Used for: Created dates, expiration dates, general date displays
     */
    val dateOnly: SimpleDateFormat
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /**
     * Format: "MMM dd, yyyy HH:mm" (e.g., "Jan 15, 2024 14:30")
     *
     * Used for: Detailed timestamps, activity logs, item detail screens
     */
    val dateTime: SimpleDateFormat
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Format: "MMM dd" (e.g., "Jan 15")
     *
     * Used for: Compact date displays, fridge cards, list items
     */
    val shortDate: SimpleDateFormat
        get() = SimpleDateFormat("MMM dd", Locale.getDefault())

    /**
     * Format: "MMM d, yyyy" (e.g., "Jan 5, 2024")
     *
     * Used for: Notification timestamps (single digit days without leading zero)
     */
    val notificationDate: SimpleDateFormat
        get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    /**
     * Format: "HH:mm" (e.g., "14:30")
     *
     * Used for: Time-only displays
     */
    val timeOnly: SimpleDateFormat
        get() = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Formats a timestamp as a relative time string (e.g., "2 hours ago", "Yesterday").
     *
     * @param timestamp The timestamp in milliseconds since epoch.
     * @return A human-readable relative time string.
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours ${if (hours == 1L) "hour" else "hours"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days days ago"
            }
            else -> notificationDate.format(Date(timestamp))
        }
    }

    /**
     * Formats a timestamp to a date string using the default date format.
     *
     * @param timestamp The timestamp in milliseconds since epoch.
     * @return Formatted date string (e.g., "Jan 15, 2024").
     */
    fun formatDate(timestamp: Long): String = dateOnly.format(Date(timestamp))

    /**
     * Formats a timestamp to a date-time string.
     *
     * @param timestamp The timestamp in milliseconds since epoch.
     * @return Formatted date-time string (e.g., "Jan 15, 2024 14:30").
     */
    fun formatDateTime(timestamp: Long): String = dateTime.format(Date(timestamp))

    /**
     * Formats a timestamp to a short date string.
     *
     * @param timestamp The timestamp in milliseconds since epoch.
     * @return Formatted short date string (e.g., "Jan 15").
     */
    fun formatShortDate(timestamp: Long): String = shortDate.format(Date(timestamp))
}
