package org.example.hospital.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.hospital.domain.Department;
import org.example.hospital.domain.RoleType;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.repository.DepartmentRepository;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自然语言指令解析服务
 * 解析用户的自然语言指令并执行相应操作
 */
@Service
public class NaturalLanguageService {

    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final ShiftRepository shiftRepository;

    // 日期匹配模式：2026年8月5号、2026-08-05、8月5日等
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})[号日]?"
    );

    // 时间匹配模式：早上8点、下午五点、08:00、17:00等
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(早上|上午|中午|下午|晚上|凌晨)?(\\d{1,2})[点时](\\d{0,2})?"
    );

    // 时间范围匹配：8点到下午五点、08:00-17:00
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
        "((?:早上|上午|中午|下午|晚上|凌晨)?\\d{1,2}[点时]\\d{0,2}?)\\s*(?:到|至|-)\\s*((?:早上|上午|中午|下午|晚上|凌晨)?\\d{1,2}[点时]\\d{0,2}?)"
    );

    public NaturalLanguageService(
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            ShiftRepository shiftRepository) {
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.shiftRepository = shiftRepository;
    }

    /**
     * 解析并执行"给XX医生增加班次"指令
     */
    @Transactional
    public String handleCreateShiftForPerson(String input) {
        // 1. 解析人物
        String personName = extractPersonName(input);
        if (personName == null) {
            return " 未找到指定的人员，请说明要给谁增加班次（如：王医生、李护士）";
        }

        UserAccount employee = findUserByName(personName);
        if (employee == null) {
            return "❌ 未找到名为\"" + personName + "\"的员工";
        }

        // 2. 解析日期
        LocalDate date = extractDate(input);
        if (date == null) {
            return "❌ 未找到日期信息，请说明具体日期（如：2026年8月5号、8月5日）";
        }

        // 3. 解析时间范围
        LocalDateTime[] timeRange = extractTimeRange(input, date);
        if (timeRange == null) {
            return "❌ 未找到时间信息，请说明时间段（如：早上8点到下午五点、08:00-17:00）";
        }

        // 4. 创建班次
        Shift shift = new Shift(timeRange[0], timeRange[1], RoleType.DOCTOR, employee.getDepartment());
        shift.setNotes("智能体创建：" + input);
        shift = shiftRepository.save(shift);

        // 5. 指派给该员工
        shift.setAssignedUser(employee);
        shift.setStatus(ShiftStatus.ASSIGNED);
        shift = shiftRepository.save(shift);

        return String.format(
            "✅ 已成功为 %s 创建班次：\n\n" +
            "📅 日期：%s\n" +
            "⏰ 时间：%s - %s\n" +
            "🏥 科室：%s\n" +
            "👤 员工：%s\n\n" +
            "班次 ID: %d",
            personName,
            date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
            timeRange[0].format(DateTimeFormatter.ofPattern("HH:mm")),
            timeRange[1].format(DateTimeFormatter.ofPattern("HH:mm")),
            employee.getDepartment() != null ? employee.getDepartment().getName() : "未分配",
            employee.getFullName(),
            shift.getId()
        );
    }

    /**
     * 从输入中提取人物名称
     */
    private String extractPersonName(String input) {
        // 匹配"给XX医生"、"给XX护士"、"为XX"等模式
        Pattern personPattern = Pattern.compile("(?:给|为|帮)(.+?)(?:医生|护士|主任|医师)?(?:增加|添加|创建|安排|排)");
        Matcher matcher = personPattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 匹配"XX医生"、"XX护士"
        personPattern = Pattern.compile("(\\S+)(?:医生|护士|主任|医师)");
        matcher = personPattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 根据名称查找用户
     */
    private UserAccount findUserByName(String name) {
        List<UserAccount> allUsers = userAccountRepository.findAll();
        for (UserAccount user : allUsers) {
            if (user.getFullName() != null && user.getFullName().contains(name)) {
                return user;
            }
            if (user.getFullName() != null && name.contains(user.getFullName())) {
                return user;
            }
        }
        return null;
    }

    /**
     * 从输入中提取日期
     */
    private LocalDate extractDate(String input) {
        Matcher matcher = DATE_PATTERN.matcher(input);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                return null;
            }
        }

        // 尝试匹配"明天"、"后天"
        if (input.contains("明天")) {
            return LocalDate.now().plusDays(1);
        }
        if (input.contains("后天")) {
            return LocalDate.now().plusDays(2);
        }
        if (input.contains("今天")) {
            return LocalDate.now();
        }

        return null;
    }

    /**
     * 从输入中提取时间范围
     */
    private LocalDateTime[] extractTimeRange(String input, LocalDate date) {
        Matcher matcher = TIME_RANGE_PATTERN.matcher(input);
        if (matcher.find()) {
            LocalTime startTime = parseTimeString(matcher.group(1));
            LocalTime endTime = parseTimeString(matcher.group(2));
            if (startTime != null && endTime != null) {
                return new LocalDateTime[]{
                    LocalDateTime.of(date, startTime),
                    LocalDateTime.of(date, endTime)
                };
            }
        }
        return null;
    }

    /**
     * 解析时间字符串（支持"早上8点"、"下午五点"、"08:00"等格式）
     */
    private LocalTime parseTimeString(String timeStr) {
        if (timeStr == null) return null;

        // 尝试直接解析 HH:mm 格式
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            // 继续尝试其他格式
        }

        // 解析"早上8点"、"下午五点"等中文格式
        Matcher matcher = TIME_PATTERN.matcher(timeStr);
        if (matcher.find()) {
            String period = matcher.group(1); // 早上、下午等
            int hour = Integer.parseInt(matcher.group(2));
            int minute = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

            // 根据时间段调整小时
            if ("下午".equals(period) || "晚上".equals(period)) {
                if (hour < 12) {
                    hour += 12;
                }
            } else if ("凌晨".equals(period) || "早上".equals(period) || "上午".equals(period)) {
                if (hour == 12) {
                    hour = 0;
                }
            }

            return LocalTime.of(hour, minute);
        }

        return null;
    }
}
