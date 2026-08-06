import type { ErrorResponse, AuthResponse } from '../types/api';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || localStorage.getItem('jwt');
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken?: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem('jwt', accessToken);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function getToken(): string | null {
  return getAccessToken();
}

export function setToken(token: string): void {
  setTokens(token);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem('jwt');
}

export function clearToken(): void {
  clearTokens();
}

export function isAuthenticated(): boolean {
  return !!getAccessToken();
}

export interface ApiFetchOptions extends RequestInit {
  apiKey?: string;
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (options.apiKey) {
    headers.set('X-API-KEY', options.apiKey);
  } else if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const { apiKey, ...fetchInit } = options;

  const response = await fetch(path, {
    ...fetchInit,
    headers,
  });

  if (response.status === 401) {
    clearTokens();
    window.location.href = '#/login';
    throw new Error('Unauthorized session. Please log in again.');
  }

  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`;
    try {
      const errorData = await response.json() as ErrorResponse;
      if (errorData && errorData.message) {
        errorMessage = errorData.message;
      }
    } catch {
      // JSON parsing failed, use fallback message
    }
    throw new Error(errorMessage);
  }

  // Handle empty responses gracefully
  if (response.status === 204) {
    return {} as T;
  }

  return response.json() as Promise<T>;
}
