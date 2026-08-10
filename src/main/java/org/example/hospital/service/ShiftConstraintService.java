package org.example.hospital.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.repository.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排班约束检查服务
 * 实现硬约束、中等约束和软约束的验证
 */
@Service
public class ShiftConstraintService {

    private final ShiftRepository shiftRepository;

    public ShiftConstraintService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    /**
     * 检查班次时间重叠（硬约束）
     * 防止同一员工出现重叠的班次
     */
    public void checkShiftOverlap(UserAccount employee, LocalDateTime newStart, LocalDateTime newEnd, Long excludeShiftId) {
        List<Shift> employeeShifts = shiftRepository.findByAssignedUser(employee);

        for (Shift existing : employeeShifts) {
            // 排除当前正在更新的班次
            if (excludeShiftId != null && existing.getId().equals(excludeShiftId)) {
                continue;
            }

            // 检查时间是否重叠
            if (newStart.isBefore(existing.getEndTime()) &&
                newEnd.isAfter(existing.getStartTime())) {
                throw new IllegalArgumentException(
                    String.format("员工 %s 在 %s 至 %s 已有班次（%s），时间重叠",
                        employee.getFullName(),
                        existing.getStartTime(),
                        existing.getEndTime(),
                        existing.getNotes() != null ? existing.getNotes() : "未命名")
                );
            }
        }
    }

    /**
     * 检查每周最大工时（硬约束）
     * 超过 45 小时的违规行为
     */
    public void checkWeeklyMaxHours(UserAccount employee, LocalDateTime newShiftStart, LocalDateTime newShiftEnd) {
        // 计算该周的起止时间（周一到周日）
        LocalDateTime weekStart = newShiftStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        // 查询该周所有已指派班次
        List<Shift> weekShifts = shiftRepository.findByAssignedUser(employee)
                .stream()
                .filter(s -> !s.getStartTime().isBefore(weekStart) &&
                             s.getStartTime().isBefore(weekEnd))
                .collect(Collectors.toList());

        // 计算总工时（分钟）
        long totalMinutes = weekShifts.stream()
                .mapToLong(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                .sum();

        // 加上新班次的工时
        long newShiftMinutes = java.time.Duration.between(newShiftStart, newShiftEnd).toMinutes();
        long totalWithNew = totalMinutes + newShiftMinutes;

        // 硬约束：不超过最大工时
        int maxHours = employee.getMaxWeeklyHours();
        if (totalWithNew > maxHours * 60) {
            throw new IllegalArgumentException(
                String.format("员工 %s 本周工时已达 %d 小时，加上新班次将超过 %d 小时上限（当前 %d 小时 + 新班次 %.1f 小时）",
                    employee.getFullName(),
                    totalMinutes / 60,
                    maxHours,
                    totalMinutes / 60,
                    newShiftMinutes / 60.0)
            );
        }
    }

    /**
     * 检查最短休息时间（中等约束）
     * 连续两班之间有 8 小时的休息时间
     */
    public void checkMinimumBreak(UserAccount employee, LocalDateTime newShiftStart) {
        List<Shift> employeeShifts = shiftRepository.findByAssignedUser(employee)
                .stream()
                .filter(s -> s.getEndTime().isBefore(newShiftStart))
                .sorted(Comparator.comparing(Shift::getEndTime).reversed())
                .collect(Collectors.toList());

        if (!employeeShifts.isEmpty()) {
            Shift previous = employeeShifts.get(0);
            long breakHours = java.time.Duration.between(previous.getEndTime(), newShiftStart).toHours();

            if (breakHours < 8) {
                throw new IllegalArgumentException(
                    String.format("员工 %s 上一班次结束于 %s，距离新班次开始仅 %d 小时，不足 8 小时休息时间",
                        employee.getFullName(),
                        previous.getEndTime(),
                        breakHours)
                );
            }
        }
    }

    /**
     * 检查每周最低工时（中等约束）
     * 全职至少 32 小时（仅在排班完成后检查，作为警告）
     */
    public boolean checkWeeklyMinHours(UserAccount employee, LocalDateTime referenceDate) {
        LocalDateTime weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<Shift> weekShifts = shiftRepository.findByAssignedUser(employee)
                .stream()
                .filter(s -> !s.getStartTime().isBefore(weekStart) &&
                             s.getStartTime().isBefore(weekEnd))
                .collect(Collectors.toList());

        long totalMinutes = weekShifts.stream()
                .mapToLong(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                .sum();

        int minHours = employee.getMinWeeklyHours();
        return totalMinutes >= minHours * 60;
    }

    /**
     * 计算员工本周工时
     */
    public long getWeeklyHours(UserAccount employee, LocalDateTime referenceDate) {
        LocalDateTime weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<Shift> weekShifts = shiftRepository.findByAssignedUser(employee)
                .stream()
                .filter(s -> !s.getStartTime().isBefore(weekStart) &&
                             s.getStartTime().isBefore(weekEnd))
                .collect(Collectors.toList());

        return weekShifts.stream()
                .mapToLong(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes())
                .sum() / 60;
    }

    /**
     * 检查技能匹配（硬约束）
     * 仅分配具备所需技能的员工
     */
    public void checkSkillMatch(UserAccount employee, String requiredSkill) {
        if (requiredSkill != null && !requiredSkill.isEmpty()) {
            if (!employee.hasSkill(requiredSkill)) {
                throw new IllegalArgumentException(
                    String.format("员工 %s 不具备所需技能：%s（当前技能：%s）",
                        employee.getFullName(),
                        requiredSkill,
                        employee.getSkills().isEmpty() ? "无" : String.join(", ", employee.getSkills()))
                );
            }
        }
    }
}
