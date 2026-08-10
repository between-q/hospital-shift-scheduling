package org.example.hospital.dto;

import jakarta.validation.constraints.NotNull;

public class CreateShiftRequestDTO {

    @NotNull
    private Long shiftId;

    @NotNull
    private String requestType;

    private Long targetShiftId;

    private String reason;

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public Long getTargetShiftId() { return targetShiftId; }
    public void setTargetShiftId(Long targetShiftId) { this.targetShiftId = targetShiftId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
