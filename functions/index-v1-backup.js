const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Cloud Function that sends push notifications when a notification document is created.
 * Triggers on: /notifications/{notificationId}
 */
exports.sendNotificationOnCreate = functions.firestore
  .document('notifications/{notificationId}')
  .onCreate(async (snap, context) => {
    const notification = snap.data();
    const userId = notification.userId;

    console.log(`Processing notification for user: ${userId}`);

    try {
      // Get user's FCM token from fcmTokens collection
      const tokenDoc = await admin.firestore()
        .collection('fcmTokens')
        .doc(userId)
        .get();

      if (!tokenDoc.exists) {
        console.log(`No FCM token found for user: ${userId}`);
        return null;
      }

      const fcmToken = tokenDoc.data().token;

      // Prepare notification payload
      const message = {
        notification: {
          title: notification.title,
          body: notification.body,
        },
        data: {
          type: notification.type,
          notificationId: snap.id,
          fridgeId: notification.relatedFridgeId || '',
          itemId: notification.relatedItemId || '',
        },
        token: fcmToken,
      };

      // Send the notification
      const response = await admin.messaging().send(message);
      console.log(`Successfully sent notification to user ${userId}:`, response);

      return response;
    } catch (error) {
      console.error('Error sending notification:', error);
      return null;
    }
  });
