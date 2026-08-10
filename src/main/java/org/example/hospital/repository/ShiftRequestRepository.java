package org.example.hospital.repository;

import java.util.List;
import org.example.hospital.domain.RequestStatus;
import org.example.hospital.domain.ShiftRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, Long> {
    List<ShiftRequest> findByStatus(RequestStatus status);
    List<ShiftRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    long countByStatus(RequestStatus status);
    List<ShiftRequest> findByStatusInOrderByReviewedAtDesc(List<RequestStatus> statuses);
}
