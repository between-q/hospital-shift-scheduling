package org.example.hospital.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.example.hospital.domain.RequestStatus;
import org.example.hospital.domain.RequestType;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftRequest;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.dto.CreateShiftRequestDTO;
import org.example.hospital.dto.ShiftRequestResponse;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.ShiftRequestRepository;
import org.example.hospital.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftRequestService {

    private final ShiftRequestRepository shiftRequestRepository;
    private final ShiftRepository shiftRepository;
    private final UserAccountRepository userAccountRepository;

    public ShiftRequestService(ShiftRequestRepository shiftRequestRepository,
                               ShiftRepository shiftRepository,
                               UserAccountRepository userAccountRepository) {
        this.shiftRequestRepository = shiftRequestRepository;
        this.shiftRepository = shiftRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public ShiftRequestResponse submitRequest(CreateShiftRequestDTO dto, Long requesterId) {
        UserAccount requester = userAccountRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new IllegalArgumentException("班次不存在"));

        ShiftRequest req = new ShiftRequest();
        req.setRequester(requester);
        req.setShift(shift);
        req.setRequestType(RequestType.valueOf(dto.getRequestType()));
        req.setReason(dto.getReason());
        req.setStatus(RequestStatus.PENDING);
        req.setCreatedAt(LocalDateTime.now());

        if (req.getRequestType() == RequestType.SWAP && dto.getTargetShiftId() != null) {
            Shift targetShift = shiftRepository.findById(dto.getTargetShiftId())
                    .orElseThrow(() -> new IllegalArgumentException("目标班次不存在"));
            req.setTargetShift(targetShift);
        }

        shiftRequestRepository.save(req);
        return toResponse(req);
    }

    @Transactional(readOnly = true)
    public List<ShiftRequestResponse> listPending() {
        List<ShiftRequest> list = shiftRequestRepository.findByStatus(RequestStatus.PENDING);
        // Force initialization of lazy-loaded relationships
        list.forEach(r -> {
            r.getRequester().getFullName();
            r.getShift().getStartTime();
            if (r.getShift().getDepartment() != null) {
                r.getShift().getDepartment().getName();
            }
        });
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftRequestResponse> listByUser(Long userId) {
        List<ShiftRequest> list = shiftRequestRepository.findByRequesterIdOrderByCreatedAtDesc(userId);
        list.forEach(r -> {
            r.getRequester().getFullName();
            r.getShift().getStartTime();
            if (r.getShift().getDepartment() != null) {
                r.getShift().getDepartment().getName();
            }
        });
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public long countPending() {
        return shiftRequestRepository.countByStatus(RequestStatus.PENDING);
    }

    @Transactional
    public ShiftRequestResponse approve(Long requestId, Long reviewerId) {
        ShiftRequest req = shiftRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在"));
        UserAccount reviewer = userAccountRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("审核人不存在"));

        req.setStatus(RequestStatus.APPROVED);
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());

        // 如果是换班，执行班次互换
        if (req.getRequestType() == RequestType.SWAP && req.getTargetShift() != null) {
            UserAccount originalAssignee = req.getShift().getAssignedUser();
            UserAccount targetAssignee = req.getTargetShift().getAssignedUser();

            req.getShift().setAssignedUser(targetAssignee);
            req.getTargetShift().setAssignedUser(originalAssignee);

            shiftRepository.save(req.getShift());
            shiftRepository.save(req.getTargetShift());
        }

        // 如果是请假，取消班次指派
        if (req.getRequestType() == RequestType.LEAVE) {
            req.getShift().setAssignedUser(null);
            req.getShift().setStatus(org.example.hospital.domain.ShiftStatus.OPEN);
            shiftRepository.save(req.getShift());
        }

        shiftRequestRepository.save(req);
        return toResponse(req);
    }

    @Transactional
    public ShiftRequestResponse reject(Long requestId, Long reviewerId) {
        ShiftRequest req = shiftRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在"));
        UserAccount reviewer = userAccountRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("审核人不存在"));

        req.setStatus(RequestStatus.REJECTED);
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());

        shiftRequestRepository.save(req);
        return toResponse(req);
    }

    private ShiftRequestResponse toResponse(ShiftRequest req) {
        ShiftRequestResponse resp = new ShiftRequestResponse();
        resp.setId(req.getId());
        resp.setRequesterId(req.getRequester().getId());
        resp.setRequesterName(req.getRequester().getFullName());
        resp.setShiftId(req.getShift().getId());
        resp.setShiftTime(formatShiftTime(req.getShift()));
        resp.setShiftDepartment(req.getShift().getDepartment() != null
                ? req.getShift().getDepartment().getName() : "");
        resp.setRequestType(req.getRequestType().name());
        resp.setReason(req.getReason());
        resp.setStatus(req.getStatus().name());
        resp.setCreatedAt(req.getCreatedAt());

        if (req.getTargetShift() != null) {
            resp.setTargetShiftId(req.getTargetShift().getId());
            resp.setTargetShiftTime(formatShiftTime(req.getTargetShift()));
        }
        if (req.getReviewedBy() != null) {
            resp.setReviewedById(req.getReviewedBy().getId());
            resp.setReviewedByName(req.getReviewedBy().getFullName());
        }
        resp.setReviewedAt(req.getReviewedAt());

        return resp;
    }

    private String formatShiftTime(Shift shift) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        String start = shift.getStartTime().format(fmt);
        String end = shift.getEndTime().format(fmt);
        return start + " - " + end;
    }

    @Transactional(readOnly = true)
    public List<ShiftRequestResponse> listReviewed() {
        List<RequestStatus> reviewedStatuses = List.of(RequestStatus.APPROVED, RequestStatus.REJECTED);
        List<ShiftRequest> list = shiftRequestRepository.findByStatusInOrderByReviewedAtDesc(reviewedStatuses);
        list.forEach(r -> {
            r.getRequester().getFullName();
            r.getShift().getStartTime();
            if (r.getShift().getDepartment() != null) {
                r.getShift().getDepartment().getName();
            }
            if (r.getReviewedBy() != null) {
                r.getReviewedBy().getFullName();
            }
        });
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Stats getStats() {
        return new Stats(
            countPending(),
            shiftRequestRepository.countByStatus(RequestStatus.APPROVED),
            shiftRequestRepository.countByStatus(RequestStatus.REJECTED)
        );
    }

    public static class Stats {
        private final long pendingCount;
        private final long approvedCount;
        private final long rejectedCount;

        public Stats(long pendingCount, long approvedCount, long rejectedCount) {
            this.pendingCount = pendingCount;
            this.approvedCount = approvedCount;
            this.rejectedCount = rejectedCount;
        }

        public long getPendingCount() { return pendingCount; }
        public long getApprovedCount() { return approvedCount; }
        public long getRejectedCount() { return rejectedCount; }
    }
}
