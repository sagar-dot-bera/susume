import { useState, useEffect } from 'react';
import { getToken, clearToken, setToken } from '../services/api';

export interface UserClaims {
  sub: string; // User ID
  tenantId: string;
  role: string;
  email: string;
  exp: number;
}

function parseJwt(token: string): UserClaims | null {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window.atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload) as UserClaims;
  } catch {
    return null;
  }
}

export function useAuth() {
  const [token, setTokenState] = useState<string | null>(getToken());
  const [user, setUser] = useState<UserClaims | null>(null);

  useEffect(() => {
    if (token) {
      const claims = parseJwt(token);
      if (claims && claims.exp * 1000 > Date.now()) {
        setUser(claims);
      } else {
        // Expired
        clearToken();
        setTokenState(null);
        setUser(null);
      }
    } else {
      setUser(null);
    }
  }, [token]);

  const login = (newToken: string) => {
    setToken(newToken);
    setTokenState(newToken);
  };

  const logout = () => {
    clearToken();
    setTokenState(null);
    setUser(null);
    window.location.href = '#/login';
  };

  return {
    token,
    user,
    isAuthenticated: !!user,
    login,
    logout,
  };
}
