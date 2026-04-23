/**
 * Recommendation Engine - Dashboard JavaScript
 * Centralized API client and utilities
 */

const API_BASE = '/api';

/**
 * Generic fetch wrapper with JWT auth
 */
async function apiCall(endpoint, options = {}) {
    const token = localStorage.getItem('token');
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers,
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers,
        });

        if (response.status === 401) {
            // Token expired
            localStorage.removeItem('token');
            window.location.href = '/';
            return null;
        }

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || `HTTP ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

/**
 * Authentication: Login
 */
async function login(email, password) {
    const data = await apiCall('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
    });
    if (data && data.token) {
        localStorage.setItem('token', data.token);
        localStorage.setItem('userEmail', email);
    }
    return data;
}

/**
 * Get current user
 */
async function getCurrentUser() {
    return apiCall('/auth/me');
}

/**
 * Items API
 */
async function getItems(limit = 50, offset = 0) {
    return apiCall(`/items?limit=${limit}&offset=${offset}`);
}

async function getItem(id) {
    return apiCall(`/items/${id}`);
}

async function createItem(itemData) {
    return apiCall('/items', {
        method: 'POST',
        body: JSON.stringify(itemData),
    });
}

async function updateItem(id, itemData) {
    return apiCall(`/items/${id}`, {
        method: 'PUT',
        body: JSON.stringify(itemData),
    });
}

async function deleteItem(id) {
    return apiCall(`/items/${id}`, { method: 'DELETE' });
}

/**
 * Interactions API
 */
async function recordInteraction(externalUserId, externalItemId, interactionType) {
    return apiCall('/interactions', {
        method: 'POST',
        body: JSON.stringify({
            external_user_id: externalUserId,
            external_item_id: externalItemId,
            interaction_type: interactionType,
        }),
    });
}

async function getInteractions(limit = 100, offset = 0) {
    return apiCall(`/interactions?limit=${limit}&offset=${offset}`);
}

/**
 * Recommendations API
 */
async function getRecommendations(externalUserId, limit = 10) {
    return apiCall(`/recommendations?user_id=${externalUserId}&limit=${limit}`);
}

/**
 * Analytics API
 */
async function getDashboardStats() {
    return apiCall('/analytics/dashboard-stats');
}

async function getSystemHealth() {
    return apiCall('/health');
}

/**
 * Utility: Format date
 */
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

/**
 * Utility: Show notification
 */
function showNotification(message, type = 'success') {
    const notification = document.createElement('div');
    notification.className = `fixed top-4 right-4 px-6 py-3 rounded-lg font-medium text-white ${type === 'success' ? 'bg-green-500' : 'bg-red-500'
        }`;
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(() => notification.remove(), 4000);
}
