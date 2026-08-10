package org.example.hospital.dto;

import java.time.LocalDateTime;

public class ShiftRequestResponse {

    private Long id;
    private Long requesterId;
    private String requesterName;
    private Long shiftId;
    private String shiftTime;
    private String shiftDepartment;
    private String requestType;
    private Long targetShiftId;
    private String targetShiftTime;
    private String reason;
    private String status;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public String getShiftTime() { return shiftTime; }
    public void setShiftTime(String shiftTime) { this.shiftTime = shiftTime; }

    public String getShiftDepartment() { return shiftDepartment; }
    public void setShiftDepartment(String shiftDepartment) { this.shiftDepartment = shiftDepartment; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public Long getTargetShiftId() { return targetShiftId; }
    public void setTargetShiftId(Long targetShiftId) { this.targetShiftId = targetShiftId; }

    public String getTargetShiftTime() { return targetShiftTime; }
    public void setTargetShiftTime(String targetShiftTime) { this.targetShiftTime = targetShiftTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getReviewedById() { return reviewedById; }
    public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }

    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
