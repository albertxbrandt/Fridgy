package fyi.goodbye.fridgy.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fyi.goodbye.fridgy.MainActivity
import fyi.goodbye.fridgy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for handling incoming push notifications.
 *
 * This service:
 * - Receives remote messages from FCM
 * - Displays notifications to the user
 * - Handles notification clicks
 * - Updates FCM token when it changes
 */
class FridgyMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FridgyMessagingService"
        private const val NOTIFICATION_ID_COUNTER_START = 1000
    }

    private val channelId: String
        get() = getString(R.string.default_notification_channel_id)
    
    private val channelName: String
        get() = getString(R.string.notification_channel_name)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated.
     * This happens on first app install, app data clear, or token refresh.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Save the token to Firestore via repository
        serviceScope.launch {
            try {
                val repository = fyi.goodbye.fridgy.repositories.NotificationRepository()
                repository.refreshFcmToken()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving new FCM token", e)
            }
        }
    }

    /**
     * Called when a message is received from FCM.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification payload: ${notification.title}")
            showNotification(
                title = notification.title ?: getString(R.string.app_name),
                body = notification.body ?: "",
                data = remoteMessage.data
            )
        }

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }
    }

    /**
     * Handle custom data payloads sent with the notification.
     */
    private fun handleDataPayload(data: Map<String, String>) {
        // Extract custom data
        val type = data["type"]
        val fridgeId = data["fridgeId"]
        val itemId = data["itemId"]

        Log.d(TAG, "Handling data: type=$type, fridgeId=$fridgeId, itemId=$itemId")

        // Show notification with custom data
        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["body"] ?: "You have a new notification"
        showNotification(title, body, data)
    }

    /**
     * Display a notification to the user.
     */
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            
            // Add custom data to intent for deep linking
            data["type"]?.let { putExtra("notificationType", it) }
            data["fridgeId"]?.let { putExtra("fridgeId", it) }
            data["itemId"]?.let { putExtra("itemId", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Show notification with unique ID
        val notificationId = NOTIFICATION_ID_COUNTER_START + System.currentTimeMillis().toInt() % 10000
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Create notification channel for Android O and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for fridge updates, invites, and more"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            // Cancel any ongoing coroutines
        }
    }
}
