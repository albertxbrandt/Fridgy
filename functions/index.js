const {onDocumentCreated} = require('firebase-functions/v2/firestore');
const {onCall} = require('firebase-functions/v2/https');
const {defineSecret} = require('firebase-functions/params');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {getMessaging} = require('firebase-admin/messaging');
const {getAuth} = require('firebase-admin/auth');
const {Resend} = require('resend');

initializeApp();

// Define the secret parameter
const resendApiKey = defineSecret('RESEND_API_KEY');

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

/**
 * Cloud Function to send magic link email via Resend
 * Callable function for passwordless authentication
 */
exports.sendMagicLink = onCall({secrets: [resendApiKey]}, async (request) => {
  const email = request.data.email;

  // Validate input
  if (!email || typeof email !== 'string') {
    throw new Error('Email address is required');
  }

  // Validate email format
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    throw new Error('Invalid email format');
  }

  // Initialize Resend with the secret value
  const resend = new Resend(resendApiKey.value());

  try {
    // Generate magic link using Firebase Admin SDK
    const actionCodeSettings = {
      url: 'https://fridgyapp.com/auth',
      handleCodeInApp: true,
      android: {
        packageName: 'fyi.goodbye.fridgy',
        installApp: true,
      },
    };

    const link = await getAuth().generateSignInWithEmailLink(email, actionCodeSettings);
    console.log('Generated magic link for:', email);

    // Create a clean link that hides the Firebase domain typo
    // Encode the Firebase link as base64 and use our domain
    const encodedLink = Buffer.from(link).toString('base64');
    const cleanLink = `https://fridgyapp.com/signin?token=${encodedLink}`;

    // Send email via Resend with the clean link
    const {data, error} = await resend.emails.send({
      from: 'Fridgy <noreply@fridgyapp.com>',
      to: email,
      subject: 'Sign in to Fridgy',
      html: generateEmailHTML(cleanLink), // Use clean link in email
    });

    if (error) {
      console.error('Resend error:', error);
      throw new Error(`Failed to send email: ${error.message}`);
    }

    console.log('Magic link email sent successfully to:', email, 'ID:', data.id);

    return {
      success: true,
      message: 'Magic link sent successfully',
    };
  } catch (error) {
    console.error('Error sending magic link:', error);

    if (error.code === 'auth/invalid-email') {
      throw new Error('Invalid email address');
    }

    throw new Error(error.message || 'Failed to send magic link. Please try again.');
  }
});

/**
 * Generate custom HTML email template for magic link
 */
function generateEmailHTML(link) {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sign in to Fridgy</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 
                   Helvetica, Arial, sans-serif;
      background-color: #F8FAFC;
    }
    .container {
      max-width: 600px;
      margin: 0 auto;
      background-color: #ffffff;
      border-radius: 12px;
      overflow: hidden;
    }
    .header {
      background: linear-gradient(135deg, #2563EB 0%, #1E40AF 100%);
      padding: 40px 20px;
      text-align: center;
    }
    .logo {
      font-size: 36px;
      font-weight: bold;
      color: #ffffff;
      margin: 0;
      letter-spacing: -0.5px;
    }
    .content {
      padding: 40px 30px;
    }
    .title {
      font-size: 24px;
      font-weight: 600;
      color: #0F172A;
      margin: 0 0 20px 0;
    }
    .text {
      font-size: 16px;
      line-height: 1.6;
      color: #475569;
      margin: 0 0 30px 0;
    }
    .button-container {
      text-align: center;
      margin: 40px 0;
    }
    .button {
      display: inline-block;
      background-color: #2563EB;
      color: #ffffff !important;
      text-decoration: none;
      padding: 16px 40px;
      font-size: 16px;
      font-weight: 600;
      border-radius: 0;
      border: none;
      box-shadow: 0 4px 6px rgba(37, 99, 235, 0.2);
    }
    .button:hover {
      background-color: #1D4ED8;
      box-shadow: 0 6px 8px rgba(37, 99, 235, 0.3);
    }
    .footer {
      padding: 30px;
      text-align: center;
      font-size: 14px;
      color: #64748B;
      background-color: #F8FAFC;
      border-top: 1px solid #E2E8F0;
    }
    .footer-text {
      margin: 10px 0;
    }
    .link {
      color: #2563EB;
      word-break: break-all;
    }
    .expiry {
      font-size: 14px;
      color: #64748B;
      margin: 20px 0 0 0;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1 class="logo">Fridgy</h1>
    </div>
    
    <div class="content">
      <h2 class="title">Welcome back! 👋</h2>
      
      <p class="text">
        Click the button below to securely sign in to your Fridgy account. 
        No password needed!
      </p>
      
      <div class="button-container">
        <a href="${link}" class="button">Sign In to Fridgy</a>
      </div>
      
      <p class="expiry">
        This link will expire in <strong>1 hour</strong> for your security.
      </p>
      
      <p class="text">
        If the button doesn't work, copy and paste this link into your browser:
      </p>
      
      <p class="link">
        ${link}
      </p>
    </div>
    
    <div class="footer">
      <p class="footer-text">
        <strong>Didn't request this email?</strong><br>
        You can safely ignore this message. No action is required.
      </p>
      <p class="footer-text">
        © ${new Date().getFullYear()} Fridgy - Keep your fridge organized
      </p>
    </div>
  </div>
</body>
</html>
  `.trim();
}
