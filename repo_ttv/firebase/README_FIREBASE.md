# Yashora Reel Generator - Firebase Firestore Setup & Schema Documentation

This directory contains resources to initialize, configure, and secure the Firestore Database collection layout for the Yashora Reel Generator application, specifically targeting the global caching structure of script-to-media mappings.

---

## 📂 1. The `project_cache` Collection Schema

The `project_cache` collection stores verified mappings of search query keywords and styling metadata to high-quality media asset URLs (e.g., Unsplash, Pexels, Pixabay, etc.). By caching resolved results in a shared global database, the application minimizes expensive third-party stock database API calls and guarantees consistent visual assets across users.

### Document Properties & Fields

| Field Name | Firestore Data Type | Description |
| :--- | :--- | :--- |
| `query` | `String` | The normalized, trimmed, and lowercased keyword term (e.g., `"coding computer"`). |
| `media_urls` | `Array <String>` | A chronological collection of direct visual asset URLs. First element represents the primary choice. |
| `timestamps` | `Array <Number>` | Epoch milliseconds logging cache updates or reads to evaluate cache expiration. |
| `style` | `String` | Visual aesthetic parameters (e.g., `"Cinematic"`, `"Minimalist"`, `"Anime"`). |
| `aspectRatio` | `String` | The target media aspect ratio (e.g., `"9:16"`, `"16:9"`). |
| `visualMedium` | `String` | Identifies the medium class: `"image"` or `"video"`. |
| `mediaUrl` | `String` | Backward compatibility field containing the single primary visual URL string. |
| `timestamp` | `Number` | Backward compatibility epoch timestamp of the initial document creation. |

### Document ID Strategy
To avoid duplicate mappings and achieve rapid querying without table scans, documents are generated using a deterministic `UUID v3 / MD5` hash computed from a composite key:
`hashKey = "${query}_${style}_${aspectRatio}_${visualMedium}"`

---

## 🔒 2. Firestore Security Rules

To enforce data integrity while keeping the query cache publicly consultable (highly recommended to save on API credits), deploy the security definitions in `firestore.rules`.

### Key Security Policies:
1. **Public Read Access**: Any app user can perform cache hits to immediately load visuals.
2. **Schema-Validated Writes**: Prevent junk entries by requiring `query` (string), `media_urls` (list), and `timestamps` (list) for creation or update requests.

Deploy these rules directly in your Firebase Console or deploy via Firebase CLI:
```bash
firebase deploy --only firestore:rules
```

---

## ⚡ 3. Schema Initialization & Seeding Script

The script `firestore_setup.js` can be executed to dry-run/preview the schema structure locally or live-seed your production Firestore instance.

### Setup Instructions:
1. Initialize a new Node.js workspace if needed, or install dependencies directly:
   ```bash
   npm install firebase-admin
   ```
2. Retrieve your service account private key from the Firebase Console:
   - **Settings** > **Project Settings** > **Service accounts**.
   - Click **Generate new private key** and download the JSON.
   - Save the file as `serviceAccountKey.json` in this directory.
3. Execute the setup script:
   ```bash
   node firestore_setup.js
   ```
