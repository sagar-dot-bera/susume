package com.susume.recommendation.service;

import com.susume.recommendation.dto.AcceptInvitationRequest;
import com.susume.recommendation.dto.CreateInvitationRequest;
import com.susume.recommendation.dto.InvitationResponse;
import com.susume.recommendation.dto.InvitationValidationResponse;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.entity.UserRole;
import com.susume.recommendation.entity.InvitationStatus;
import com.susume.recommendation.entity.TenantInvitation;
import com.susume.recommendation.exception.InvitationExpiredException;
import com.susume.recommendation.exception.InvitationNotFoundException;
import com.susume.recommendation.filter.JwtContext;
import com.susume.recommendation.repository.UserRepository;
import com.susume.recommendation.repository.TenantInvitationRepository;
import com.susume.recommendation.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TenantInvitationServiceImpl implements TenantInvitationService {

    private final TenantInvitationRepository invitationRepository;
    private final UserRepository dashboardUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthMailService authMailService;

    @Value("${invitation.expiry-hours:72}")
    private int invitationExpiryHours;

    public TenantInvitationServiceImpl(
            TenantInvitationRepository invitationRepository,
            UserRepository dashboardUserRepository,
            BCryptPasswordEncoder passwordEncoder, AuthMailService authMailService) {
        this.invitationRepository = invitationRepository;
        this.dashboardUserRepository = dashboardUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authMailService = authMailService;
    }

    @Override
    @Transactional
    public InvitationResponse createInvitation(CreateInvitationRequest request) {
        UUID tenantId = requireTenantId();

        // Guard: don't create a duplicate pending invitation for the same email
        if (invitationRepository.existsByTenantIdAndEmailAndStatus(
                tenantId, request.email(), InvitationStatus.PENDING)) {
            throw new IllegalStateException(
                    "A pending invitation for " + request.email() + " already exists");
        }

        String token = generateSecureToken();
        Instant expiresAt = Instant.now().plus(invitationExpiryHours, ChronoUnit.HOURS);

        TenantInvitation invitation = TenantInvitation.builder()
                .tenantId(tenantId)
                .email(request.email())
                .role(UserRole.fromString(request.role()))
                .token(token)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        TenantInvitation saved = invitationRepository.save(invitation);
        log.info("Invitation created [id={}, email={}, tenant={}]",
                saved.getId(), saved.getEmail(), tenantId);

        authMailService.sendInvitationEmail(saved.getEmail(), token);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationValidationResponse validateInvitation(String token) {
        TenantInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));

        boolean valid = invitation.getStatus() == InvitationStatus.PENDING
                && invitation.getExpiresAt().isAfter(Instant.now());

        return new InvitationValidationResponse(
                invitation.getEmail(),
                invitation.getRole().toString(),
                valid,
                toLocalDateTime(invitation.getExpiresAt()));
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, AcceptInvitationRequest request) {
        TenantInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));

        assertPending(invitation);
        assertNotExpired(invitation);

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Create the dashboard user under the tenant
        if (dashboardUserRepository.findByEmail(invitation.getEmail()).isPresent()) {
            throw new IllegalStateException(
                    "A user with email " + invitation.getEmail() + " already exists");
        }

        User newUser = User.builder()
                .tenantId(invitation.getTenantId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .build();

        dashboardUserRepository.save(newUser);

        // Mark invitation accepted
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Invitation accepted [id={}, email={}]",
                invitation.getId(), invitation.getEmail());
    }

    @Override
    @Transactional
    public void declineInvitation(String token) {
        TenantInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));

        assertPending(invitation);

        invitation.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(invitation);

        log.info("Invitation declined [id={}, email={}]",
                invitation.getId(), invitation.getEmail());
    }

    @Override
    @Transactional
    public void resendInvitation(UUID invitationId) {
        UUID tenantId = requireTenantId();

        TenantInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));

        requireSameTenant(invitation, tenantId);

        // Allow resend for PENDING or EXPIRED invitations only
        if (invitation.getStatus() == InvitationStatus.ACCEPTED
                || invitation.getStatus() == InvitationStatus.DECLINED) {
            throw new IllegalStateException(
                    "Cannot resend an invitation with status: " + invitation.getStatus());
        }

        // Regenerate token and reset expiry
        String token = generateSecureToken();
        invitation.setToken(token);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(invitationExpiryHours, ChronoUnit.HOURS));
        invitationRepository.save(invitation);

        log.info("Invitation resent [id={}, email={}]",
                invitation.getId(), invitation.getEmail());

        authMailService.sendInvitationEmail(invitation.getEmail(), token);
    }

    @Override
    @Transactional
    public void cancelInvitation(UUID invitationId) {
        UUID tenantId = requireTenantId();

        TenantInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));

        requireSameTenant(invitation, tenantId);
        assertPending(invitation);

        invitationRepository.delete(invitation);
        log.info("Invitation cancelled [id={}, tenant={}]", invitationId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitations() {
        UUID tenantId = requireTenantId();
        return invitationRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(UUID invitationId) {
        UUID tenantId = requireTenantId();
        TenantInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));
        requireSameTenant(invitation, tenantId);
        return toResponse(invitation);
    }

    @Override
    @Transactional
    public void deleteExpiredInvitations() {
        int updated = invitationRepository.markExpiredInvitations(
                Instant.now(), InvitationStatus.EXPIRED, InvitationStatus.PENDING);
        log.info("Marked {} invitation(s) as EXPIRED", updated);
    }

    private UUID requireTenantId() {
        UUID tenantId = JwtContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No authenticated tenant found in request context");
        }
        return tenantId;
    }

    private void requireSameTenant(TenantInvitation invitation, UUID tenantId) {
        if (!invitation.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied: invitation belongs to a different tenant");
        }
    }

    private void assertPending(TenantInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException(
                    "Invitation is no longer PENDING (current status: " + invitation.getStatus() + ")");
        }
    }

    private void assertNotExpired(TenantInvitation invitation) {
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new InvitationExpiredException();
        }
    }

    /** Generate a URL-safe 64-char hex token. */
    private String generateSecureToken() {
        return CryptoUtil.generateAPIKey() + CryptoUtil.generateAPIKey();
    }

    private InvitationResponse toResponse(TenantInvitation inv) {
        return new InvitationResponse(
                inv.getId(),
                inv.getEmail(),
                inv.getRole().toString(),
                inv.getStatus().name(),
                toLocalDateTime(inv.getExpiresAt()),
                toLocalDateTime(inv.getAcceptedAt()),
                toLocalDateTime(inv.getCreatedAt()));
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null)
            return null;
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

}
