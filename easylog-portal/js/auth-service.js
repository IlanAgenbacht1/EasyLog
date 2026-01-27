// Authentication Service
// Firebase Auth with Google Sign-in for portal admins

const authService = {
    /**
     * Sign in with Google (portal admin only)
     */
    async signInWithGoogle() {
        try {
            const provider = new firebase.auth.GoogleAuthProvider();
            provider.setCustomParameters({
                prompt: 'select_account'
            });

            const result = await auth.signInWithPopup(provider);
            const user = result.user;

            // Check if user is a portal admin
            const adminDoc = await db.collection('portal_admins')
                .doc(user.uid)
                .get();

            if (!adminDoc.exists) {
                // Not a portal admin - sign out and show helpful error with UID
                await auth.signOut();
                console.error('Access denied. User ID:', user.uid);
                console.error('Email:', user.email);
                console.error('To grant access, run: firebase firestore:add-admin', user.email);

                throw new Error(
                    `Access denied. You are not registered as a portal admin.\n\n` +
                    `Your User ID: ${user.uid}\n` +
                    `Email: ${user.email}\n\n` +
                    `Please share this User ID with your administrator to grant access.`
                );
            }

            const adminData = adminDoc.data();
            if (!adminData.isActive) {
                // Admin is deactivated
                await auth.signOut();
                throw new Error('Your admin account has been deactivated. Please contact an administrator.');
            }

            // Success - user is authenticated and authorized
            return {
                uid: user.uid,
                email: user.email,
                name: adminData.name || user.displayName,
                isActive: adminData.isActive
            };
        } catch (error) {
            console.error('Sign-in error:', error);
            throw error;
        }
    },

    /**
     * Sign out current user
     */
    async signOut() {
        try {
            await auth.signOut();
        } catch (error) {
            console.error('Sign-out error:', error);
            throw error;
        }
    },

    /**
     * Get current user
     */
    getCurrentUser() {
        return auth.currentUser;
    },

    /**
     * Check if user is signed in
     */
    isSignedIn() {
        return auth.currentUser !== null;
    },

    /**
     * Get current admin info from Firestore
     */
    async getCurrentAdminInfo() {
        const user = auth.currentUser;
        if (!user) return null;

        try {
            const adminDoc = await db.collection('portal_admins')
                .doc(user.uid)
                .get();

            if (!adminDoc.exists) return null;

            const data = adminDoc.data();
            return {
                uid: user.uid,
                email: user.email,
                name: data.name || user.displayName,
                isActive: data.isActive
            };
        } catch (error) {
            console.error('Error getting admin info:', error);
            return null;
        }
    },

    /**
     * Wait for auth state to be ready
     */
    async waitForAuthReady() {
        return new Promise((resolve) => {
            const unsubscribe = auth.onAuthStateChanged((user) => {
                unsubscribe();
                resolve(user);
            });
        });
    },

    /**
     * Require authentication - redirect to login if not authenticated
     */
    async requireAuth() {
        const user = await this.waitForAuthReady();

        if (!user) {
            window.location.href = 'login.html';
            return false;
        }

        // Verify user is portal admin
        try {
            const adminDoc = await db.collection('portal_admins')
                .doc(user.uid)
                .get();

            if (!adminDoc.exists || !adminDoc.data().isActive) {
                await auth.signOut();
                window.location.href = 'login.html';
                return false;
            }

            return true;
        } catch (error) {
            console.error('Auth verification error:', error);
            window.location.href = 'login.html';
            return false;
        }
    },

    /**
     * Redirect if already logged in (for login page)
     */
    async redirectIfLoggedIn() {
        const user = await this.waitForAuthReady();

        if (user) {
            // Check if portal admin
            try {
                const adminDoc = await db.collection('portal_admins')
                    .doc(user.uid)
                    .get();

                if (adminDoc.exists && adminDoc.data().isActive) {
                    window.location.href = 'index.html';
                    return true;
                }
            } catch (error) {
                console.error('Error checking admin status:', error);
            }
        }

        return false;
    },

    /**
     * Listen for auth state changes
     */
    onAuthStateChanged(callback) {
        return auth.onAuthStateChanged(callback);
    }
};
