// C:\Users\Owner\graphhopper\backend\index.js
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  storageBucket: 'your-project-id.appspot.com'
});

const bucket = admin.storage().bucket();
const db = admin.firestore();

// 여기서 runId를 받아 GeoJSON 업로드·메타저장 로직을 작성하세요.
