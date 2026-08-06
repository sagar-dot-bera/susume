package com.susume.recommendation.service;

import com.susume.recommendation.dto.AcceptInvitationRequest;
import com.susume.recommendation.dto.CreateInvitationRequest;
import com.susume.recommendation.dto.InvitationResponse;
import com.susume.recommendation.dto.InvitationValidationResponse;

import java.util.List;
import java.util.UUID;

public interface TenantInvitationService {

    /**
     * Create a new invitation for an email address under the current tenant.
     * Sends an invitation email to the provided address.
     */
    InvitationResponse createInvitation(CreateInvitationRequest request);

    /**
     * Validate an invitation token without consuming it.
     * Returns token metadata and validity status.
     */
    InvitationValidationResponse validateInvitation(String token);

    /**
     * Accept an invitation by token.
     * Creates a new DashboardUser under the tenant and marks the invitation as ACCEPTED.
     */
    void acceptInvitation(String token, AcceptInvitationRequest request);

    /**
     * Decline an invitation by token. Marks the invitation as DECLINED.
     */
    void declineInvitation(String token);

    /**
     * Resend the invitation email for a given invitation ID.
     * Resets the expiry and regenerates the token.
     */
    void resendInvitation(UUID invitationId);

    /**
     * Cancel (soft-delete by status) a pending invitation.
     * Only the owning tenant can cancel their invitations.
     */
    void cancelInvitation(UUID invitationId);

    /**
     * Get all invitations for the currently authenticated tenant.
     */
    List<InvitationResponse> getInvitations();

    /**
     * Get a specific invitation by ID (scoped to the current tenant).
     */
    InvitationResponse getInvitation(UUID invitationId);

    /**
     * Scheduled cleanup — marks expired PENDING invitations as EXPIRED.
     */
    void deleteExpiredInvitations();
}
