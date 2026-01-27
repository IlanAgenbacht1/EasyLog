// Utility Functions

const utils = {
    // Format date for display
    formatDate(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toLocaleDateString('en-ZA', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    },

    // Format time for display
    formatTime(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toLocaleTimeString('en-ZA', {
            hour: '2-digit',
            minute: '2-digit'
        });
    },

    // Format datetime
    formatDateTime(dateString) {
        if (!dateString) return '-';
        return `${this.formatDate(dateString)} ${this.formatTime(dateString)}`;
    },

    // Format minutes as hours and minutes
    formatMinutes(totalMinutes) {
        if (!totalMinutes && totalMinutes !== 0) return '-';
        const hours = Math.floor(totalMinutes / 60);
        const minutes = totalMinutes % 60;

        if (hours > 0 && minutes > 0) {
            return `${hours}h ${minutes}m`;
        } else if (hours > 0) {
            return `${hours}h`;
        } else {
            return `${minutes}m`;
        }
    },

    // Format minutes as decimal hours
    formatHoursDecimal(totalMinutes) {
        if (!totalMinutes && totalMinutes !== 0) return '0.0';
        return (totalMinutes / 60).toFixed(1);
    },

    // Get date string for input (YYYY-MM-DD)
    getDateInputValue(date) {
        if (!date) date = new Date();
        return date.toISOString().split('T')[0];
    },

    // Get date X days ago
    getDaysAgo(days) {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return this.getDateInputValue(date);
    },

    // Show toast notification
    showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `fixed bottom-4 right-4 px-6 py-3 rounded-lg shadow-lg text-white z-50 transform transition-all duration-300 ${
            type === 'error' ? 'bg-red-500' :
            type === 'success' ? 'bg-green-500' :
            'bg-blue-500'
        }`;
        toast.textContent = message;
        document.body.appendChild(toast);

        setTimeout(() => {
            toast.classList.add('opacity-0', 'translate-y-2');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    },

    // Show loading spinner
    showLoading(container) {
        container.innerHTML = `
            <div class="flex items-center justify-center py-12">
                <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            </div>
        `;
    },

    // Show error state
    showError(container, message) {
        container.innerHTML = `
            <div class="flex flex-col items-center justify-center py-12 text-gray-500">
                <svg class="w-16 h-16 mb-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                <p class="text-lg">${message}</p>
            </div>
        `;
    },

    // Show empty state
    showEmpty(container, message) {
        container.innerHTML = `
            <div class="flex flex-col items-center justify-center py-12 text-gray-500">
                <svg class="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path>
                </svg>
                <p class="text-lg">${message}</p>
            </div>
        `;
    },

    // Debounce function
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },

    // Escape HTML
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    // Status badge HTML
    getStatusBadge(status) {
        const classes = status === 'completed'
            ? 'bg-green-100 text-green-800'
            : 'bg-yellow-100 text-yellow-800';
        const displayText = status === 'completed' ? 'Completed' :
                           status === 'in_progress' ? 'In Progress' : status;
        return `<span class="inline-flex px-2 py-1 text-xs font-medium rounded ${classes}">${displayText}</span>`;
    },

    // Upload status badge HTML
    getUploadStatusBadge(uploadStatus, uploadQueuedAt) {
        if (!uploadStatus || uploadStatus === 'completed') {
            return '';
        }

        // Check if upload is stale (older than 24 hours)
        const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24 hours
        let isStale = false;
        let ageText = '';

        if (uploadQueuedAt) {
            const queuedDate = new Date(uploadQueuedAt);
            const now = new Date();
            const ageMs = now - queuedDate;
            isStale = ageMs > STALE_THRESHOLD_MS;

            // Format age for display
            const ageHours = Math.floor(ageMs / (60 * 60 * 1000));
            const ageDays = Math.floor(ageHours / 24);

            if (ageDays > 0) {
                ageText = ` (${ageDays}d)`;
            } else if (ageHours > 0) {
                ageText = ` (${ageHours}h)`;
            }
        }

        const classes = isStale
            ? 'bg-red-100 text-red-800'
            : 'bg-blue-100 text-blue-800';

        const icon = isStale ? '⚠️' : '⏳';
        const text = isStale ? `Stale Upload${ageText}` : `Uploading${ageText}`;

        return `<span class="inline-flex px-2 py-1 text-xs font-medium rounded ${classes}">${icon} ${text}</span>`;
    }
};
