const admin = require('firebase-admin');

// Since we cannot package the private key in source control, 
// we will initialize from the environment variables or a default path.
// The user should set GOOGLE_APPLICATION_CREDENTIALS in their .env
// or we can initialize it via path to serviceAccountKey.json

let db = null;
let bucket = null;

try {
  // Initialize Firebase Admin
  // Assumes you have downloaded the service account JSON file from Firebase
  // and placed it in the backend folder as 'serviceAccountKey.json'
  // Or provided GOOGLE_APPLICATION_CREDENTIALS in .env
  
  const hasEnvCreds = process.env.FIREBASE_PROJECT_ID && 
                      process.env.FIREBASE_CLIENT_EMAIL && 
                      process.env.FIREBASE_PRIVATE_KEY;

  if (hasEnvCreds) {
    admin.initializeApp({
      credential: admin.credential.cert({
        projectId: process.env.FIREBASE_PROJECT_ID,
        clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
        // Replace \\n with \n if it's passed from .env
        privateKey: process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n'),
      }),
      storageBucket: process.env.FIREBASE_STORAGE_BUCKET
    });
    db = admin.firestore();
    bucket = admin.storage().bucket();
    console.log("Firebase initialized successfully via ENV");
  } else {
    // Fallback to serviceAccountKey.json if it exists
    const fs = require('fs');
    const path = require('path');
    const serviceAccountPath = path.join(__dirname, '../../serviceAccountKey.json');

    if (fs.existsSync(serviceAccountPath)) {
      admin.initializeApp({
          credential: admin.credential.cert(require(serviceAccountPath)),
          storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'qr-photoshare.firebasestorage.app'
      });
      db = admin.firestore();
      bucket = admin.storage().bucket();
      console.log("Firebase initialized successfully via serviceAccountKey.json");
    } else {
      console.warn("Firebase credentials not found (ENV or serviceAccountKey.json). Firestore and Storage will be unavailable.");
    }
  }
} catch (error) {
  console.error("Firebase initialization error:", error);
}

module.exports = { db, bucket };
