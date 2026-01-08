const {onDocumentCreated} = require('firebase-functions/v2/firestore');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {getMessaging} = require('firebase-admin/messaging');

initializeApp();

/**
 * Cloud Function (2nd Gen) that sends push notifications when a notification document is created.
 * Triggers on: /notifications/{notificationId}
 * 
 * 2nd Gen functions have better default permissions and don't require manual IAM role grants.
 */
exports.sendNotificationOnCreate = onDocumentCreated('notifications/{notificationId}', async (event) => {
  const notification = event.data.data();
  const userId = notification.userId;
  const notificationId = event.params.notificationId;

  console.log(`Processing notification ${notificationId} for user: ${userId}`);

  try {
    // Get user's FCM token from fcmTokens collection
    const tokenDoc = await getFirestore()
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
        type: notification.type || 'GENERAL',
        notificationId: notificationId,
        fridgeId: notification.relatedFridgeId || '',
        itemId: notification.relatedItemId || '',
      },
      token: fcmToken,
    };

    // Send the notification
    const response = await getMessaging().send(message);
    console.log(`Successfully sent notification to user ${userId}:`, response);
    
    return response;

  } catch (error) {
    console.error(`Error sending notification to user ${userId}:`, error);
    throw error;
  }
});
