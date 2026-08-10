package org.example.hospital.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.example.hospital.dto.CreateShiftRequestDTO;
import org.example.hospital.dto.ShiftRequestResponse;
import org.example.hospital.security.UserAccountDetails;
import org.example.hospital.service.ShiftRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shift-requests")
public class ShiftRequestController {

    private final ShiftRequestService shiftRequestService;

    public ShiftRequestController(ShiftRequestService shiftRequestService) {
        this.shiftRequestService = shiftRequestService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public ResponseEntity<ShiftRequestResponse> submit(
            @RequestBody @Valid CreateShiftRequestDTO dto,
            @AuthenticationPrincipal UserAccountDetails principal) {
        return ResponseEntity.ok(shiftRequestService.submitRequest(dto, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<List<ShiftRequestResponse>> list(
            @AuthenticationPrincipal UserAccountDetails principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return ResponseEntity.ok(shiftRequestService.listPending());
        }
        return ResponseEntity.ok(shiftRequestService.listByUser(principal.getId()));
    }

    @GetMapping("/count-pending")
    public ResponseEntity<Long> countPending() {
        return ResponseEntity.ok(shiftRequestService.countPending());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftRequestResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserAccountDetails principal) {
        return ResponseEntity.ok(shiftRequestService.approve(id, principal.getId()));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftRequestResponse> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserAccountDetails principal) {
        return ResponseEntity.ok(shiftRequestService.reject(id, principal.getId()));
    }

    @GetMapping("/reviewed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ShiftRequestResponse>> listReviewed() {
        return ResponseEntity.ok(shiftRequestService.listReviewed());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftRequestService.Stats> stats() {
        return ResponseEntity.ok(shiftRequestService.getStats());
    }
}
