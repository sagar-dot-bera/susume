export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface UserRegistrationRequest {
  email: string;
  password: string;
  username: string;
  firstName?: string;
  lastName?: string;
}

export interface TenantRegistrationRequest {
  name: string;
  slug: string;
}

export interface NewAdminAccountRequest {
  userRegistrationRequest: UserRegistrationRequest;
  tenantRegistrationRequest: TenantRegistrationRequest;
}

export interface RefreshTokenRequest {
  token: string;
}

export interface ResendVerificationTokenRequest {
  email: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface AuthMessageResponse {
  message: string;
}

export interface ApiKeyDto {
  id: string;
  tenantId: string;
  key: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateApiKeyResponse {
  id: string;
  tenantId: string;
  rawApiKey: string;
  createdAt: string;
}

export interface TenantInfoResponse {
  id: string;
  name: string;
  contactEmail: string;
  createdAt: string;
  status: string;
  slug: string;
}

export interface DashboardStatsResponse {
  itemCount: number;
  totalRecs: number;
  totalInteractions: number;
  avgLatency: number;
  apiKeyCount: number;
  hitsOverTime: Record<string, number>;
  typeBreakdown: Record<string, number>;
}

export interface CreateInvitationRequest {
  email: string;
  role: string;
}

export interface InvitationResponse {
  id: string;
  email: string;
  role: string;
  status: string;
  expiresAt: string;
  acceptedAt?: string | null;
  createdAt: string;
}

export interface InvitationValidationResponse {
  email: string;
  role: string;
  valid: boolean;
  expiresAt: string;
  tenantName?: string;
  tenantSlug?: string;
}

export interface AcceptInvitationRequest {
  firstName?: string;
  lastName?: string;
  username: string;
  password: string;
  confirmPassword?: string;
}

export interface CreateItemRequest {
  externalItemId: string;
  metadata: Record<string, any>;
}

export interface UpdateItemRequest {
  metadata: Record<string, any>;
}

export interface ItemResponse {
  id?: string;
  externalItemId: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ItemDetailResponse {
  id: string;
  externalItemId: string;
  metadata: Record<string, any>;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ItemListResponse {
  data: ItemDetailResponse[];
  nextCursor: string | null;
  limit: number;
}

export interface RecordInteractionRequest {
  externalUserId: string;
  externalItemId: string;
  interactionType: string;
  timestamp?: string;
}

export interface InteractionHistoryItemResponse {
  id: string;
  externalUserId: string;
  externalItemId: string;
  interactionType: string;
  timestamp: string;
}

export interface InteractionHistoryResponse {
  data: InteractionHistoryItemResponse[];
  limit: number;
}

export interface RecommendationRequest {
  externalUserId: string;
  limit: number;
}

export interface RecommendationItemResponse {
  externalItemId: string;
  metadata: any;
  similarityScore: number;
}

export interface RecommendationResponse {
  userId?: string;
  recommendations: RecommendationItemResponse[];
  strategy: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
}

export interface StrategyInfoResponse {
  name: string;
  algorithm: string;
  description: string;
  enabled: boolean;
}
