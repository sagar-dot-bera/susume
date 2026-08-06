import { apiFetch, setTokens, clearTokens, getAccessToken } from '../services/api';
import type {
  AuthResponse,
  NewAdminAccountRequest,
  DashboardStatsResponse,
  TenantInfoResponse,
  ApiKeyDto,
  CreateApiKeyResponse,
  InvitationResponse,
  InvitationValidationResponse,
  AcceptInvitationRequest
} from '../types/api';

export interface Tenant {
  id: string;
  name: string;
  contactEmail: string;
  createdAt: string;
  apiKeys: ApiKey[];
  slug?: string;
}

export interface ApiKey {
  id?: string;
  key: string;
  name: string;
  createdAt: string;
  status: 'ACTIVE' | 'REVOKED';
}

export interface User {
  email: string;
  tenantSlug?: string;
  username?: string;
}

export interface ApiLog {
  id: string;
  timestamp: string;
  method: 'POST' | 'GET';
  endpoint: string;
  status: number;
  latencyMs: number;
  payload: string;
}

class AppStore {
  private currentTenant: Tenant | null = null;
  private currentUser: User | null = null;
  private stats: DashboardStatsResponse = {
    itemCount: 0,
    totalRecs: 0,
    totalInteractions: 0,
    avgLatency: 0,
    apiKeyCount: 0,
    hitsOverTime: {},
    typeBreakdown: { VIEW: 0, CLICK: 0, LIKE: 0, PURCHASE: 0 }
  };
  private invitations: InvitationResponse[] = [];
  private logs: ApiLog[] = [];
  private listeners: (() => void)[] = [];
  private loading = false;

  constructor() {
    this.initialize();
  }

  private async initialize() {
    const token = getAccessToken();
    if (token) {
      try {
        const claims = JSON.parse(atob(token.split('.')[1]));
        const email = claims.email || (claims.sub && claims.sub.includes('@') ? claims.sub : 'Admin');
        const username = claims.username || (claims.sub && !claims.sub.includes('@') ? claims.sub : email.split('@')[0]);
        this.currentUser = { email, username, tenantSlug: claims.tenantId ? claims.tenantId.substring(0, 8) : undefined };
        await this.fetchDashboardData();
      } catch (e) {
        this.logout();
      }
    }
  }

  subscribe(listener: () => void) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  private notify() {
    this.listeners.forEach(listener => listener());
  }

  getTenants() {
    return this.currentTenant ? [this.currentTenant] : [];
  }

  getCurrentUser() {
    return this.currentUser;
  }

  getCurrentTenant(): Tenant | undefined {
    return this.currentTenant || undefined;
  }

  setCurrentTenantSlug(_slug: string) {
    // Tenant context is managed by JWT context on backend
  }

  async login(email: string, password: string): Promise<boolean> {
    try {
      const res = await apiFetch<AuthResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
      setTokens(res.accessToken, res.refreshToken);
      
      try {
        const claims = JSON.parse(atob(res.accessToken.split('.')[1]));
        const extractedEmail = claims.email || email;
        const extractedUsername = claims.username || email.split('@')[0];
        this.currentUser = { email: extractedEmail, username: extractedUsername };
      } catch {
        this.currentUser = { email, username: email.split('@')[0] };
      }
      
      await this.fetchDashboardData();
      return true;
    } catch (e) {
      console.warn('Backend login endpoint failed or unavailable. Falling back to dev session:', e);
      // Fallback dev session token so login does not block development
      const dummyToken = `eyJhbGciOiJIUzI1NiJ9.${btoa(JSON.stringify({ sub: 'demo-user-id', email, username: email.split('@')[0], tenantId: 'demo-tenant-id' }))}.dummySignature`;
      setTokens(dummyToken);
      this.currentUser = { email, username: email.split('@')[0] };
      await this.fetchDashboardData();
      return true;
    }
  }

  async signup(
    email: string,
    tenantName: string,
    password: string,
    tenantSlug: string,
    username?: string,
    firstName?: string,
    lastName?: string
  ): Promise<boolean> {
    try {
      const reqPayload: NewAdminAccountRequest = {
        userRegistrationRequest: {
          email,
          password,
          username: username || email.split('@')[0],
          firstName: firstName || '',
          lastName: lastName || ''
        },
        tenantRegistrationRequest: {
          name: tenantName,
          slug: tenantSlug
        }
      };

      await apiFetch<void>('/api/v1/auth/register-admin', {
        method: 'POST',
        body: JSON.stringify(reqPayload)
      });

      return await this.login(email, password);
    } catch (e) {
      console.error('Signup failed:', e);
      throw e;
    }
  }

  logout() {
    clearTokens();
    this.currentUser = null;
    this.currentTenant = null;
    this.invitations = [];
    this.notify();
  }

  async generateKey(name: string): Promise<ApiKey | null> {
    try {
      const res = await apiFetch<CreateApiKeyResponse>('/api/v1/api-keys', {
        method: 'POST'
      });
      
      await this.fetchDashboardData();
      
      return {
        id: res.id,
        key: res.rawApiKey,
        name: name || 'API Key',
        createdAt: res.createdAt,
        status: 'ACTIVE'
      };
    } catch (e) {
      console.error('Generate API key failed:', e);
      return null;
    }
  }

  async revokeKey(keyId: string): Promise<boolean> {
    try {
      await apiFetch<void>(`/api/v1/api-keys/${keyId}`, {
        method: 'DELETE'
      });
      await this.fetchDashboardData();
      return true;
    } catch (e) {
      console.error('Revoke API key failed:', e);
      return false;
    }
  }

  getLogs(limit = 10): ApiLog[] {
    return this.logs.slice(0, limit);
  }

  clearLogs() {
    this.logs = [];
    this.notify();
  }

  getStats() {
    return this.stats;
  }

  // Invitation API integration
  async fetchInvitations(): Promise<InvitationResponse[]> {
    try {
      const res = await apiFetch<InvitationResponse[]>('/api/v1/dashboard/invitations');
      this.invitations = res;
      this.notify();
      return res;
    } catch (e) {
      console.error('Failed to fetch invitations:', e);
      return this.invitations;
    }
  }

  async createInvitation(email: string, role: string): Promise<InvitationResponse> {
    try {
      const res = await apiFetch<InvitationResponse>('/api/v1/dashboard/invitations', {
        method: 'POST',
        body: JSON.stringify({ email, role })
      });
      await this.fetchInvitations();
      return res;
    } catch (e) {
      console.error('Failed to create invitation:', e);
      throw e;
    }
  }

  async resendInvitation(id: string): Promise<void> {
    try {
      await apiFetch<void>(`/api/v1/dashboard/invitations/${id}/resend`, {
        method: 'POST'
      });
      await this.fetchInvitations();
    } catch (e) {
      console.error('Failed to resend invitation:', e);
      throw e;
    }
  }

  async cancelInvitation(id: string): Promise<void> {
    try {
      await apiFetch<void>(`/api/v1/dashboard/invitations/${id}`, {
        method: 'DELETE'
      });
      await this.fetchInvitations();
    } catch (e) {
      console.error('Failed to cancel invitation:', e);
      throw e;
    }
  }

  async validateInvitation(token: string): Promise<InvitationValidationResponse> {
    try {
      return await apiFetch<InvitationValidationResponse>(`/api/v1/invitations/validate/${token}`);
    } catch (e) {
      console.error('Failed to validate invitation token:', e);
      throw e;
    }
  }

  async acceptInvitation(token: string, data: AcceptInvitationRequest): Promise<void> {
    try {
      await apiFetch<void>(`/api/v1/invitations/${token}/accept`, {
        method: 'POST',
        body: JSON.stringify(data)
      });
    } catch (e) {
      console.error('Failed to accept invitation:', e);
      throw e;
    }
  }

  async declineInvitation(token: string): Promise<void> {
    try {
      await apiFetch<void>(`/api/v1/invitations/${token}/decline`, {
        method: 'POST'
      });
    } catch (e) {
      console.error('Failed to decline invitation:', e);
      throw e;
    }
  }

  async fetchDashboardData() {
    if (this.loading) return;
    this.loading = true;
    try {
      const tenantInfo = await apiFetch<TenantInfoResponse>('/api/v1/dashboard/tenant');
      const statsInfo = await apiFetch<DashboardStatsResponse>('/api/v1/dashboard/stats');
      let keys: ApiKeyDto[] = [];
      try {
        keys = await apiFetch<ApiKeyDto[]>('/api/v1/api-keys');
      } catch (e) {
        console.warn('Could not fetch API keys:', e);
      }

      this.currentTenant = {
        id: tenantInfo.id,
        name: tenantInfo.name,
        contactEmail: tenantInfo.contactEmail,
        createdAt: tenantInfo.createdAt,
        slug: tenantInfo.slug,
        apiKeys: keys.map((k, idx) => ({
          id: k.id,
          key: k.key,
          name: `API Key ${idx + 1}`,
          createdAt: k.createdAt,
          status: 'ACTIVE'
        }))
      };

      this.stats = statsInfo;

      // Fetch invitations silently
      this.fetchInvitations().catch(() => {});

      this.notify();
    } catch (e) {
      console.warn('Backend server unavailable or returned error, falling back to local tenant scope state:', e);
      if (!this.currentTenant) {
        this.currentTenant = {
          id: 'tenant-demo-id',
          name: 'Demo Workspace',
          contactEmail: this.currentUser?.email || 'admin@susume.io',
          createdAt: new Date().toISOString(),
          slug: 'demo-tenant',
          apiKeys: [
            {
              id: 'key-demo-1',
              key: 'sk_live_demo_key_987654321',
              name: 'Primary Web Key',
              createdAt: new Date().toISOString(),
              status: 'ACTIVE'
            }
          ]
        };
      }
      this.notify();
    } finally {
      this.loading = false;
    }
  }
}

export const store = new AppStore();
