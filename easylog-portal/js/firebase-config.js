// Firebase Configuration and Initialization
// This replaces the legacy API-based config

const firebaseConfig = {
    apiKey: "AIzaSyAs7FIKoTB8uB7NQQm1jOMAAjoP9qunX2g",
    authDomain: "easylog-483511.firebaseapp.com",
    projectId: "easylog-483511",
    storageBucket: "ezl_bucket_01",
    messagingSenderId: "843408162982"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);

// Initialize Firebase services
const auth = firebase.auth();
const db = firebase.firestore();
const storage = firebase.storage();

// Enable Firestore offline persistence
db.enablePersistence({ synchronizeTabs: true })
    .catch((err) => {
        if (err.code === 'failed-precondition') {
            console.warn('Multiple tabs open, persistence enabled only in first tab');
        } else if (err.code === 'unimplemented') {
            console.warn('Browser does not support offline persistence');
        }
    });

// Configure auth persistence
auth.setPersistence(firebase.auth.Auth.Persistence.LOCAL);
