// Firestore Service
// Direct Firebase SDK access - no backend API
// Follows the same architecture as the Android app

const firestoreService = {
    // Collections
    COLLECTIONS: {
        ENGINEERS: 'engineers',
        CUSTOMERS: 'customers',
        TIMELOGS: 'timelogs',
        PORTAL_ADMINS: 'portal_admins'
    },

    // Dashboard methods
    dashboard: {
        /**
         * Get today's summary statistics
         */
        async getSummary() {
            try {
                const today = new Date();
                today.setHours(0, 0, 0, 0);
                const tomorrow = new Date(today);
                tomorrow.setDate(tomorrow.getDate() + 1);

                const startTimestamp = firebase.firestore.Timestamp.fromDate(today);
                const endTimestamp = firebase.firestore.Timestamp.fromDate(tomorrow);

                // Get today's timelogs
                const snapshot = await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .where('timeStarted', '>=', startTimestamp)
                    .where('timeStarted', '<', endTimestamp)
                    .get();

                let completedJobs = 0;
                let activeJobs = 0;
                let totalMinutes = 0;
                let onsiteMinutes = 0;
                const engineerMap = new Map();

                snapshot.docs.forEach(doc => {
                    const data = doc.data();

                    if (data.status === 'completed') {
                        completedJobs++;
                        const travel = data.travelTimeMinutes || 0;
                        const onsite = data.timeOnSiteMinutes || 0;
                        totalMinutes += travel + onsite;
                        onsiteMinutes += onsite;

                        // Engineer summary
                        const engineerName = data.engineerName || 'Unknown';
                        if (!engineerMap.has(engineerName)) {
                            engineerMap.set(engineerName, {
                                engineerName,
                                jobCount: 0,
                                totalMinutes: 0
                            });
                        }
                        const eng = engineerMap.get(engineerName);
                        eng.jobCount++;
                        eng.totalMinutes += travel + onsite;
                    } else if (data.status === 'in_progress') {
                        activeJobs++;
                    }
                });

                return {
                    completedJobs,
                    activeJobs,
                    totalMinutes,
                    onsiteMinutes,
                    engineerSummary: Array.from(engineerMap.values())
                        .sort((a, b) => b.totalMinutes - a.totalMinutes)
                };
            } catch (error) {
                console.error('Error fetching dashboard summary:', error);
                throw new Error('Failed to load dashboard data');
            }
        },

        /**
         * Get engineer hours report
         */
        async getEngineerHours(startDate, endDate) {
            try {
                let query = db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .where('status', '==', 'completed');

                if (startDate) {
                    const start = new Date(startDate);
                    start.setHours(0, 0, 0, 0);
                    query = query.where('timeStarted', '>=', firebase.firestore.Timestamp.fromDate(start));
                }

                if (endDate) {
                    const end = new Date(endDate);
                    end.setHours(23, 59, 59, 999);
                    query = query.where('timeStarted', '<=', firebase.firestore.Timestamp.fromDate(end));
                }

                const snapshot = await query.get();
                const engineerMap = new Map();

                snapshot.docs.forEach(doc => {
                    const data = doc.data();
                    const engineerId = data.engineerId;
                    const engineerName = data.engineerName || 'Unknown';

                    if (!engineerMap.has(engineerId)) {
                        engineerMap.set(engineerId, {
                            engineerId,
                            engineerName,
                            jobCount: 0,
                            totalHours: 0,
                            travelHours: 0,
                            onsiteHours: 0
                        });
                    }

                    const eng = engineerMap.get(engineerId);
                    const travel = (data.travelTimeMinutes || 0) / 60;
                    const onsite = (data.timeOnSiteMinutes || 0) / 60;

                    eng.jobCount++;
                    eng.travelHours += travel;
                    eng.onsiteHours += onsite;
                    eng.totalHours += travel + onsite;
                });

                return Array.from(engineerMap.values())
                    .sort((a, b) => b.totalHours - a.totalHours);
            } catch (error) {
                console.error('Error fetching engineer hours:', error);
                throw new Error('Failed to load engineer hours report');
            }
        },

        /**
         * Get customer jobs report
         */
        async getCustomerJobs(startDate, endDate) {
            try {
                let query = db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .where('status', '==', 'completed');

                if (startDate) {
                    const start = new Date(startDate);
                    start.setHours(0, 0, 0, 0);
                    query = query.where('timeStarted', '>=', firebase.firestore.Timestamp.fromDate(start));
                }

                if (endDate) {
                    const end = new Date(endDate);
                    end.setHours(23, 59, 59, 999);
                    query = query.where('timeStarted', '<=', firebase.firestore.Timestamp.fromDate(end));
                }

                const snapshot = await query.get();
                const customerMap = new Map();

                snapshot.docs.forEach(doc => {
                    const data = doc.data();
                    const customerId = data.customerId;
                    const customerName = data.customerName || 'Unknown';

                    if (!customerMap.has(customerId)) {
                        customerMap.set(customerId, {
                            customerId,
                            customerName,
                            jobCount: 0,
                            totalHours: 0
                        });
                    }

                    const cust = customerMap.get(customerId);
                    const travel = (data.travelTimeMinutes || 0) / 60;
                    const onsite = (data.timeOnSiteMinutes || 0) / 60;

                    cust.jobCount++;
                    cust.totalHours += travel + onsite;
                });

                return Array.from(customerMap.values())
                    .sort((a, b) => b.totalHours - a.totalHours);
            } catch (error) {
                console.error('Error fetching customer jobs:', error);
                throw new Error('Failed to load customer jobs report');
            }
        }
    },

    // Time logs methods
    timelogs: {
        /**
         * List time logs with optional filters
         */
        async list(filters = {}) {
            try {
                let query = db.collection(firestoreService.COLLECTIONS.TIMELOGS);

                if (filters.engineerId) {
                    query = query.where('engineerId', '==', filters.engineerId);
                }

                if (filters.customerId) {
                    query = query.where('customerId', '==', filters.customerId);
                }

                if (filters.status) {
                    query = query.where('status', '==', filters.status);
                }

                if (filters.startDate) {
                    const start = new Date(filters.startDate);
                    start.setHours(0, 0, 0, 0);
                    query = query.where('timeStarted', '>=', firebase.firestore.Timestamp.fromDate(start));
                }

                if (filters.endDate) {
                    const end = new Date(filters.endDate);
                    end.setHours(23, 59, 59, 999);
                    query = query.where('timeStarted', '<=', firebase.firestore.Timestamp.fromDate(end));
                }

                // Order by timeStarted descending
                query = query.orderBy('timeStarted', 'desc');

                if (filters.limit) {
                    query = query.limit(filters.limit);
                }

                const snapshot = await query.get();
                return snapshot.docs.map(doc => ({
                    id: doc.id,
                    ...doc.data(),
                    // Convert timestamps to ISO strings for easier handling
                    timeStarted: doc.data().timeStarted?.toDate().toISOString(),
                    timeEnded: doc.data().timeEnded?.toDate().toISOString(),
                    signedAt: doc.data().signedAt?.toDate().toISOString(),
                    uploadQueuedAt: doc.data().uploadQueuedAt?.toDate().toISOString(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                }));
            } catch (error) {
                console.error('Error listing timelogs:', error);
                throw new Error('Failed to load time logs');
            }
        },

        /**
         * Get single time log by ID
         */
        async get(id) {
            try {
                const doc = await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .doc(id)
                    .get();

                if (!doc.exists) {
                    throw new Error('Time log not found');
                }

                return {
                    id: doc.id,
                    ...doc.data(),
                    timeStarted: doc.data().timeStarted?.toDate().toISOString(),
                    timeEnded: doc.data().timeEnded?.toDate().toISOString(),
                    signedAt: doc.data().signedAt?.toDate().toISOString(),
                    uploadQueuedAt: doc.data().uploadQueuedAt?.toDate().toISOString(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                };
            } catch (error) {
                console.error('Error getting timelog:', error);
                throw new Error('Failed to load time log');
            }
        },

        /**
         * Get signature download URL
         */
        async getSignatureUrl(timelogId) {
            try {
                const doc = await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .doc(timelogId)
                    .get();

                if (!doc.exists) {
                    throw new Error('Time log not found');
                }

                const signaturePath = doc.data().signaturePath;
                if (!signaturePath) {
                    return null;
                }

                const ref = storage.ref(signaturePath);
                return await ref.getDownloadURL();
            } catch (error) {
                console.error('Error getting signature URL:', error);
                return null;
            }
        },

        /**
         * Get photo download URLs
         */
        async getPhotoUrls(timelogId) {
            try {
                const doc = await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .doc(timelogId)
                    .get();

                if (!doc.exists) {
                    throw new Error('Time log not found');
                }

                const photoPaths = doc.data().photoPaths || [];
                if (photoPaths.length === 0) {
                    return [];
                }

                const urlPromises = photoPaths.map(async (path) => {
                    try {
                        const ref = storage.ref(path);
                        return await ref.getDownloadURL();
                    } catch (error) {
                        console.error(`Error getting photo URL for ${path}:`, error);
                        return null;
                    }
                });

                const urls = await Promise.all(urlPromises);
                return urls.filter(url => url !== null);
            } catch (error) {
                console.error('Error getting photo URLs:', error);
                return [];
            }
        },

        /**
         * Delete time log (admin only)
         */
        async delete(id) {
            try {
                await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .doc(id)
                    .delete();
            } catch (error) {
                console.error('Error deleting timelog:', error);
                throw new Error('Failed to delete time log');
            }
        }
    },

    // Engineers methods
    engineers: {
        /**
         * List all engineers
         */
        async list() {
            try {
                const snapshot = await db.collection(firestoreService.COLLECTIONS.ENGINEERS)
                    .orderBy('name')
                    .get();

                return snapshot.docs.map(doc => ({
                    id: doc.id,
                    ...doc.data(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                }));
            } catch (error) {
                console.error('Error listing engineers:', error);
                throw new Error('Failed to load engineers');
            }
        },

        /**
         * Get single engineer
         */
        async get(id) {
            try {
                const doc = await db.collection(firestoreService.COLLECTIONS.ENGINEERS)
                    .doc(id)
                    .get();

                if (!doc.exists) {
                    throw new Error('Engineer not found');
                }

                return {
                    id: doc.id,
                    ...doc.data(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                };
            } catch (error) {
                console.error('Error getting engineer:', error);
                throw new Error('Failed to load engineer');
            }
        },

        /**
         * Create new engineer
         */
        async create(data) {
            try {
                const now = firebase.firestore.Timestamp.now();
                const engineerData = {
                    uid: data.uid || data.email, // Use email as UID if not provided
                    email: data.email,
                    name: data.name,
                    phone: data.phone || null,
                    isActive: data.isActive !== undefined ? data.isActive : true,
                    createdAt: now,
                    updatedAt: now
                };

                const docRef = await db.collection(firestoreService.COLLECTIONS.ENGINEERS)
                    .add(engineerData);

                return {
                    id: docRef.id,
                    ...engineerData,
                    createdAt: engineerData.createdAt.toDate().toISOString(),
                    updatedAt: engineerData.updatedAt.toDate().toISOString()
                };
            } catch (error) {
                console.error('Error creating engineer:', error);
                throw new Error('Failed to create engineer');
            }
        },

        /**
         * Update engineer
         */
        async update(id, data) {
            try {
                const updateData = {
                    ...data,
                    updatedAt: firebase.firestore.Timestamp.now()
                };

                await db.collection(firestoreService.COLLECTIONS.ENGINEERS)
                    .doc(id)
                    .update(updateData);

                return await this.get(id);
            } catch (error) {
                console.error('Error updating engineer:', error);
                throw new Error('Failed to update engineer');
            }
        },

        /**
         * Toggle engineer active status
         */
        async toggleActive(id, isActive) {
            try {
                await db.collection(firestoreService.COLLECTIONS.ENGINEERS)
                    .doc(id)
                    .update({
                        isActive,
                        updatedAt: firebase.firestore.Timestamp.now()
                    });
            } catch (error) {
                console.error('Error toggling engineer status:', error);
                throw new Error('Failed to update engineer status');
            }
        }
    },

    // Customers methods
    customers: {
        /**
         * List active customers
         */
        async list() {
            try {
                const snapshot = await db.collection(firestoreService.COLLECTIONS.CUSTOMERS)
                    .where('isActive', '==', true)
                    .get();

                // Sort client-side to avoid composite index requirement
                return snapshot.docs
                    .map(doc => ({
                        id: doc.id,
                        ...doc.data(),
                        createdAt: doc.data().createdAt?.toDate().toISOString(),
                        updatedAt: doc.data().updatedAt?.toDate().toISOString()
                    }))
                    .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
            } catch (error) {
                console.error('Error listing customers:', error);
                throw new Error('Failed to load customers');
            }
        },

        /**
         * List all customers (including inactive) - for portal admin
         */
        async listAll() {
            try {
                const snapshot = await db.collection(firestoreService.COLLECTIONS.CUSTOMERS)
                    .orderBy('name')
                    .get();

                return snapshot.docs.map(doc => ({
                    id: doc.id,
                    ...doc.data(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                }));
            } catch (error) {
                console.error('Error listing all customers:', error);
                throw new Error('Failed to load customers');
            }
        },

        /**
         * Get single customer
         */
        async get(id) {
            try {
                const doc = await db.collection(firestoreService.COLLECTIONS.CUSTOMERS)
                    .doc(id)
                    .get();

                if (!doc.exists) {
                    throw new Error('Customer not found');
                }

                return {
                    id: doc.id,
                    ...doc.data(),
                    createdAt: doc.data().createdAt?.toDate().toISOString(),
                    updatedAt: doc.data().updatedAt?.toDate().toISOString()
                };
            } catch (error) {
                console.error('Error getting customer:', error);
                throw new Error('Failed to load customer');
            }
        },

        /**
         * Create new customer
         */
        async create(data) {
            try {
                const currentUser = auth.currentUser;
                if (!currentUser) {
                    throw new Error('Not authenticated');
                }

                const now = firebase.firestore.Timestamp.now();
                const customerData = {
                    name: data.name,
                    address: data.address || null,
                    contactName: data.contactName || null,
                    contactPhone: data.contactPhone || null,
                    contactEmail: data.contactEmail || null,
                    notes: data.notes || null,
                    createdBy: currentUser.uid,
                    isActive: true,
                    createdAt: now,
                    updatedAt: now
                };

                const docRef = await db.collection(firestoreService.COLLECTIONS.CUSTOMERS)
                    .add(customerData);

                return {
                    id: docRef.id,
                    ...customerData,
                    createdAt: customerData.createdAt.toDate().toISOString(),
                    updatedAt: customerData.updatedAt.toDate().toISOString()
                };
            } catch (error) {
                console.error('Error creating customer:', error);
                throw new Error('Failed to create customer');
            }
        },

        /**
         * Update customer
         */
        async update(id, data) {
            try {
                const updateData = {
                    ...data,
                    updatedAt: firebase.firestore.Timestamp.now()
                };

                await db.collection(firestoreService.COLLECTIONS.CUSTOMERS)
                    .doc(id)
                    .update(updateData);

                return await this.get(id);
            } catch (error) {
                console.error('Error updating customer:', error);
                throw new Error('Failed to update customer');
            }
        },

        /**
         * Get customer job history
         */
        async getHistory(customerId) {
            try {
                const snapshot = await db.collection(firestoreService.COLLECTIONS.TIMELOGS)
                    .where('customerId', '==', customerId)
                    .where('status', '==', 'completed')
                    .orderBy('timeStarted', 'desc')
                    .limit(50)
                    .get();

                return snapshot.docs.map(doc => ({
                    id: doc.id,
                    ...doc.data(),
                    timeStarted: doc.data().timeStarted?.toDate().toISOString(),
                    timeEnded: doc.data().timeEnded?.toDate().toISOString(),
                    signedAt: doc.data().signedAt?.toDate().toISOString()
                }));
            } catch (error) {
                console.error('Error getting customer history:', error);
                throw new Error('Failed to load customer history');
            }
        }
    },

    // Export to CSV
    async exportToCSV(filters = {}) {
        try {
            const timelogs = await this.timelogs.list(filters);

            // CSV headers
            const headers = [
                'Date',
                'Engineer',
                'Customer',
                'Travel Time',
                'On-Site Time',
                'Total Time',
                'Work Description',
                'Signer Name',
                'Status'
            ];

            // CSV rows
            const rows = timelogs.map(log => [
                log.timeStarted ? new Date(log.timeStarted).toLocaleDateString() : '',
                log.engineerName || '',
                log.customerName || '',
                log.travelTimeMinutes || 0,
                log.timeOnSiteMinutes || 0,
                (log.travelTimeMinutes || 0) + (log.timeOnSiteMinutes || 0),
                log.workDescription || '',
                log.customerSignerName || '',
                log.status || ''
            ]);

            // Generate CSV content
            const csvContent = [
                headers.join(','),
                ...rows.map(row => row.map(cell =>
                    typeof cell === 'string' && cell.includes(',')
                        ? `"${cell}"`
                        : cell
                ).join(','))
            ].join('\n');

            // Create blob and download
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            link.setAttribute('href', url);
            link.setAttribute('download', `easylog-export-${new Date().toISOString().split('T')[0]}.csv`);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error('Error exporting CSV:', error);
            throw new Error('Failed to export data');
        }
    }
};
