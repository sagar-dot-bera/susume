package com.susume.recommendation.controller;

import com.susume.recommendation.dto.AcceptInvitationRequest;
import com.susume.recommendation.dto.CreateInvitationRequest;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.dto.InvitationResponse;
import com.susume.recommendation.dto.InvitationValidationResponse;
import com.susume.recommendation.exception.InvitationExpiredException;
import com.susume.recommendation.exception.InvitationNotFoundException;
import com.susume.recommendation.service.TenantInvitationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController()
@Slf4j
public class InvitationController {

    private final TenantInvitationService invitationService;

    public InvitationController(TenantInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/v1/dashboard/invitations")
    public ResponseEntity<?> createInvitation(@RequestBody CreateInvitationRequest request) {

        InvitationResponse response = invitationService.createInvitation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    @GetMapping("/api/v1/dashboard/invitations")
    public ResponseEntity<?> getInvitations() {
        try {
            List<InvitationResponse> responses = invitationService.getInvitations();
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error fetching invitations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to fetch invitations"));
        }
    }


    @GetMapping("/api/v1/dashboard/invitations/{id}")
    public ResponseEntity<?> getInvitation(@PathVariable UUID id) {
        try {
            InvitationResponse response = invitationService.getInvitation(id);
            return ResponseEntity.ok(response);
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("FORBIDDEN", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching invitation {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to fetch invitation"));
        }
    }

    @PostMapping("/api/v1/dashboard/invitations/{id}/resend")
    public ResponseEntity<?> resendInvitation(@PathVariable UUID id) {
        try {
            invitationService.resendInvitation(id);
            return ResponseEntity.noContent().build();
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException | SecurityException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Error resending invitation {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to resend invitation"));
        }
    }

  
    @DeleteMapping("/api/v1/dashboard/invitations/{id}")
    public ResponseEntity<?> cancelInvitation(@PathVariable UUID id) {
        try {
            invitationService.cancelInvitation(id);
            return ResponseEntity.noContent().build();
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException | SecurityException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling invitation {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to cancel invitation"));
        }
    }



    @GetMapping("/api/v1/invitations/validate/{token}")
    public ResponseEntity<?> validateInvitation(@PathVariable String token) {
        try {
            InvitationValidationResponse response = invitationService.validateInvitation(token);
            return ResponseEntity.ok(response);
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Error validating invitation token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to validate invitation"));
        }
    }


    @PostMapping("/api/v1/invitations/{token}/accept")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable String token,
            @RequestBody AcceptInvitationRequest request) {
        try {
            invitationService.acceptInvitation(token, request);
            return ResponseEntity.noContent().build();
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (InvitationExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(new ErrorResponse("EXPIRED", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Error accepting invitation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to accept invitation"));
        }
    }

    @PostMapping("/api/v1/invitations/{token}/decline")
    public ResponseEntity<?> declineInvitation(@PathVariable String token) {
        try {
            invitationService.declineInvitation(token);
            return ResponseEntity.noContent().build();
        } catch (InvitationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Error declining invitation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to decline invitation"));
        }
    }
}
