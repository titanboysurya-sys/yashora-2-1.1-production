/**
 * Firebase Firestore Setup & Schema Initialization Script
 * 
 * Purpose:
 * This script initializes and documents the structure of the 'project_cache' collection.
 * It serves as a setup pipeline to configure indices, seed initial mappings,
 * and define the schema for storing script-to-media asset mappings.
 * 
 * Requirements:
 * 1. Node.js installed
 * 2. Firebase Admin SDK package installed (`npm install firebase-admin`)
 * 3. A Service Account private key file JSON downloaded from Firebase Console.
 * 
 * Usage:
 * Set the path to your service account key in the environment or directly below:
 * `node firestore_setup.js`
 */

const admin = require('firebase-admin');
const crypto = require('crypto');

// 1. Initialize Firebase Admin SDK
// Replace with path to your serviceAccountKey.json if running locally
const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_KEY || "./serviceAccountKey.json";

try {
  const serviceAccount = require(serviceAccountPath);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  console.log("✔ Firebase Admin SDK initialized successfully.");
} catch (error) {
  console.warn("⚠ Service account JSON not found. Running in local dry-run or mock mode.");
  console.warn("To run against live Firestore, please save serviceAccountKey.json to the current directory.");
}

const db = admin.apps.length > 0 ? admin.firestore() : null;

// 2. Define Schema & Structural Requirements
const SCHEMA_DEFINITION = {
  collectionName: "project_cache",
  description: "Caches high-quality resolved stock photos, video clips, and generated media mapping results globally based on search queries and style parameters.",
  fields: {
    query: {
      type: "String",
      description: "Cleaned, lowercase search keyword or query term extracted from the video script (e.g., 'coding computer').",
      required: true
    },
    media_urls: {
      type: "Array of Strings",
      description: "A list of verified direct media URLs (Pexels, Unsplash, Pixabay etc.) matching the query.",
      required: true
    },
    timestamps: {
      type: "Array of Numbers (Epoch Milliseconds)",
      description: "Chronological logging of cache insertions/updates to assist with expiration cycles.",
      required: true
    },
    style: {
      type: "String",
      description: "Visual design aesthetic style (e.g., 'Cinematic', 'Minimalist', 'Anime').",
      required: false
    },
    aspectRatio: {
      type: "String",
      description: "Intended aspect ratio representation (e.g., '9:16', '16:9').",
      required: false
    },
    visualMedium: {
      type: "String",
      description: "Type of content retrieved (e.g., 'image', 'video').",
      required: false
    },
    mediaUrl: {
      type: "String",
      description: "Primary, fallback direct URL matching the legacy query structure.",
      required: false
    },
    timestamp: {
      type: "Number (Epoch Milliseconds)",
      description: "Primary timestamp representing the creation event.",
      required: false
    }
  }
};

/**
 * Generates a clean, deterministic document ID from query metadata
 * to prevent duplicate mapping documents.
 */
function generateDocId(query, style, aspectRatio, visualMedium) {
  const cleanQuery = query.trim().toLowerCase();
  const hashKey = `${cleanQuery}_${style}_${aspectRatio}_${visualMedium}`;
  return crypto.createHash('md5').update(hashKey).digest('hex');
}

/**
 * Seeds initial caching data to help configure indexes and verify the collection.
 */
async function seedInitialCache() {
  if (!db) {
    console.log("\n--- [DRY-RUN SCHEMA SPECIFICATION] ---");
    console.log(JSON.stringify(SCHEMA_DEFINITION, null, 2));
    console.log("\nDry-run complete. Configure Firebase Service Account key to write to live Firestore.");
    return;
  }

  console.log("\nStarting live collection setup and database seeding...");

  const sampleMappings = [
    {
      query: "coding computer",
      style: "Cinematic",
      aspectRatio: "9:16",
      visualMedium: "image",
      mediaUrl: "https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=1080&q=80",
      media_urls: ["https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=1080&q=80"]
    },
    {
      query: "sad programmer",
      style: "Minimalist",
      aspectRatio: "9:16",
      visualMedium: "video",
      mediaUrl: "https://videos.pexels.com/video-files/3129654/3129654-uhd_1080_1920_25fps.mp4",
      media_urls: ["https://videos.pexels.com/video-files/3129654/3129654-uhd_1080_1920_25fps.mp4"]
    },
    {
      query: "gym workout",
      style: "Cinematic",
      aspectRatio: "16:9",
      visualMedium: "image",
      mediaUrl: "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=1920&q=80",
      media_urls: ["https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=1920&q=80"]
    }
  ];

  const batch = db.batch();

  for (const item of sampleMappings) {
    const docId = generateDocId(item.query, item.style, item.aspectRatio, item.visualMedium);
    const docRef = db.collection('project_cache').doc(docId);
    
    const currentTime = Date.now();
    const data = {
      ...item,
      timestamp: currentTime,
      timestamps: [currentTime]
    };

    batch.set(docRef, data);
    console.log(`+ Prepared project_cache document: ${docId} (Query: "${item.query}")`);
  }

  try {
    await batch.commit();
    console.log("\n✔ Live database setup completed! Seeding complete in collection 'project_cache'.");
  } catch (err) {
    console.error("❌ Error committing setup documents: ", err);
  }
}

// Execute seeding pipeline
seedInitialCache().catch(console.error);
