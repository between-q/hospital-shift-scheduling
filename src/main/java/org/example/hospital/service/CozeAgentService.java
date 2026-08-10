package org.example.hospital.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.example.hospital.domain.Department;
import org.example.hospital.domain.RoleType;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.dto.ChatMessage;
import org.example.hospital.dto.CozeRequest;
import org.example.hospital.dto.CozeResponse;
import org.example.hospital.repository.DepartmentRepository;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Coze AI 智能体集成服务
 * 调用 Coze API 获取 AI 回复
 * 演示模式下直接操作数据库
 */

@Service
public class CozeAgentService {
    private static final Logger logger = LoggerFactory.getLogger(CozeAgentService.class);
    private static final String LOCAL_PROXY_PATH = "/api/coze/chat";
    private static final String WORKFLOW_RUN_PATH = "/v1/workflow/run";

    @Value("${coze.api.url:http://localhost:8000}")
    private String cozeApiUrl;

    @Value("${coze.api.key:}")
    private String cozeApiKey;

    @Value("${coze.workflow.id:}")
    private String workflowId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AgentChatService agentChatService;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final ShiftRepository shiftRepository;
    private final NaturalLanguageService naturalLanguageService;
    private final LlmService llmService;

    public CozeAgentService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AgentChatService agentChatService,
            DepartmentRepository departmentRepository,
            UserAccountRepository userAccountRepository,
            ShiftRepository shiftRepository,
            NaturalLanguageService naturalLanguageService,
            LlmService llmService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.agentChatService = agentChatService;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.shiftRepository = shiftRepository;
        this.naturalLanguageService = naturalLanguageService;
        this.llmService = llmService;
    }

    @PostConstruct
    public void logCozeConfig() {
        logger.info("[COZE_CONFIG] apiUrl={}, apiKeyPresent={}, workflowIdPresent={}, workflowId={}",
            cozeApiUrl,
            cozeApiKey != null && !cozeApiKey.trim().isEmpty(),
            workflowId != null && !workflowId.trim().isEmpty(),
            workflowId);
        logger.info("[LLM_CONFIG] available={}", llmService.isAvailable());
    }

    /**
     * 调用 Coze 智能体获取回复
     */
    public CozeResponse chat(CozeRequest request) {
        logger.info("chat() called with request: {}", request);
        if (request == null) {
            String errorMsg = "输入内容不能为空";
            logger.warn("{}: request is null", errorMsg);
            return new CozeResponse(null, "FAILED", errorMsg);
        }

        String normalizedInput = request.getNormalizedContent();
        logger.info("Normalized input: '{}'", normalizedInput);
        if (normalizedInput.isEmpty()) {
            String errorMsg = "输入内容不能为空";
            logger.warn("{}: content='{}', message='{}'", errorMsg, request.getContent(), request.getMessage());
            return new CozeResponse(null, "FAILED", errorMsg);
        }

        try {
            String response;

            // 如果 LLM 可用，优先使用 LLM 处理所有输入
            if (llmService.isAvailable()) {
                logger.info("LLM 可用，使用 LLM 处理输入");
                response = callLlmWithFallback(normalizedInput);
            } else {
                // LLM 不可用时，走原有流程
                logger.info("LLM 不可用，走原有流程");
                response = callCozeWorkflow(normalizedInput);
            }

            if (response == null) {
                String errorMsg = "智能体返回 null";
                logger.error(errorMsg);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            if (isCozeExecuteLink(response)) {
                String errorMsg = "返回了执行链接而非最终文本";
                logger.error("{}，response={}", errorMsg, response);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            if (response.trim().isEmpty()) {
                String errorMsg = "智能体返回空响应";
                logger.warn(errorMsg);
                return new CozeResponse(null, "FAILED", errorMsg);
            }

            // 保存 Agent 回复到数据库
            ChatMessage agentMessage = new ChatMessage("Coze Agent", "AGENT", response);
            agentMessage.setTimestamp(OffsetDateTime.now());
            agentChatService.save(agentMessage);

            logger.info("智能体回复成功，回复长度：{}", response.length());
            return new CozeResponse(response, "SUCCESS");

        } catch (Exception e) {
            String errorMsg = "智能体调用失败：" + e.getMessage();
            logger.error(errorMsg, e);
            return new CozeResponse(null, "FAILED", errorMsg);
        }
    }

    /**
     * LLM 优先处理，失败时降级到 demo 模式
     */
    private String callLlmWithFallback(String input) {
        try {
            String llmResponse = llmService.chat(input);
            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                logger.info("LLM 回复成功，长度：{}", llmResponse.length());
                return llmResponse;
            }
            logger.warn("LLM 返回空响应，降级到演示模式");
        } catch (Exception e) {
            logger.warn("LLM 调用失败，降级到演示模式：{}", e.getMessage());
        }
        // LLM 失败时降级到 demo 模式
        return handleDemoInput(input);
    }

    private boolean isCozeExecuteLink(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase();
        return normalized.contains("coze.cn/work_flow?")
            || normalized.contains("coze.cn/workflow?")
            || (normalized.contains("execute_id=") && normalized.contains("workflow_id="));
    }

    /**
     * 调用工作流：Coze API → LLM 大模型 → 本地演示模式
     */
    private String callCozeWorkflow(String input) throws Exception {
        logger.info("调用工作流，输入：{}", input);

        // 优先级 1：调用 Coze API
        if (cozeApiUrl != null && !cozeApiUrl.trim().isEmpty() &&
            cozeApiKey != null && !cozeApiKey.trim().isEmpty()) {
            logger.info("使用 HTTP 调用 Coze API: {}", cozeApiUrl);
            try {
                return callCozeViaHttp(input);
            } catch (Exception e) {
                logger.warn("HTTP 调用 Coze 失败，降级到 LLM：{}", e.getMessage());
            }
        }

        // 优先级 2：调用 LLM 大模型
        if (llmService.isAvailable()) {
            logger.info("使用 LLM 大模型处理输入");
            try {
                String llmResponse = llmService.chat(input);
                if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                    logger.info("LLM 回复成功，长度：{}", llmResponse.length());
                    return llmResponse;
                }
                logger.warn("LLM 返回空响应，降级到演示模式");
            } catch (Exception e) {
                logger.warn("LLM 调用失败，降级到演示模式：{}", e.getMessage());
            }
        }

        // 优先级 3：本地演示模式（关键词匹配兜底）
        logger.info("使用本地演示模式处理输入");
        String response = handleDemoInput(input);
        logger.info("演示响应：{}", response);
        return response;
    }

    /**
     * 通过 HTTP 调用 Coze 工作流
     */
    private String callCozeViaHttp(String input) throws Exception {
        String resolvedUrl = resolveCozeUrl();
        boolean proxyMode = isProxyMode(resolvedUrl);
        logger.info("通过 HTTP 调用 Coze: baseUrl={}, resolvedUrl={}, mode={}, workflowId={}, 输入={}",
            cozeApiUrl,
            resolvedUrl,
            proxyMode ? "proxy" : "workflow",
            workflowId,
            input);

        Map<String, Object> payload = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (proxyMode) {
            payload.put("input", input);
            if (workflowId != null && !workflowId.trim().isEmpty()) {
                payload.put("workflow_id", workflowId);
            }
        } else {
            payload.put("workflow_id", workflowId);
            Map<String, String> parameters = new HashMap<>();
            parameters.put("input", input);
            payload.put("parameters", parameters);
            headers.setBearerAuth(cozeApiKey);
        }

        HttpEntity<String> request = new HttpEntity<>(
            objectMapper.writeValueAsString(payload),
            headers
        );

        logger.debug("发送 HTTP 请求到：{}, body={}", resolvedUrl, payload);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                resolvedUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            logger.info("Coze HTTP 响应状态码：{}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                String errorMsg = String.format("Coze API 返回错误状态码：%s", response.getStatusCode());
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            String responseBody = response.getBody();
            logger.debug("Coze HTTP 原始响应：{}", responseBody);
            String finalResponse = extractResponseText(responseBody, proxyMode);
            logger.info("Coze HTTP 调用成功，解析后回复长度：{}", finalResponse.length());
            return finalResponse;
        } catch (Exception e) {
            logger.error("HTTP 请求异常：{}, resolvedUrl={}", e.getMessage(), resolvedUrl, e);
            throw e;
        }
    }

    private String resolveCozeUrl() {
        String base = Objects.toString(cozeApiUrl, "").trim();
        if (base.isEmpty()) {
            return WORKFLOW_RUN_PATH;
        }

        if (base.contains(LOCAL_PROXY_PATH) || base.contains(WORKFLOW_RUN_PATH)) {
            return base;
        }

        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (normalized.contains("localhost:8000") || normalized.contains("127.0.0.1:8000")) {
            return normalized + LOCAL_PROXY_PATH;
        }
        return normalized + WORKFLOW_RUN_PATH;
    }

    private boolean isProxyMode(String resolvedUrl) {
        return resolvedUrl != null && resolvedUrl.contains(LOCAL_PROXY_PATH);
    }

    private String extractResponseText(String responseBody, boolean proxyMode) throws Exception {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "无有效回复内容";
        }

        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
        Object dataObj;
        if (proxyMode) {
            dataObj = firstNonNull(result.get("response"), result.get("message"), result.get("data"), result.get("msg"));
        } else {
            dataObj = firstNonNull(result.get("data"), result.get("message"), result.get("msg"), result.get("response"));
        }

        if (dataObj == null) {
            return responseBody;
        }

        String finalResponse = dataObj.toString();
        try {
            if (finalResponse.startsWith("{")) {
                Map<String, Object> dataMap = objectMapper.readValue(finalResponse, Map.class);
                Object nested = firstNonNull(dataMap.get("output"), dataMap.get("response"), dataMap.get("message"), dataMap.get("content"));
                if (nested != null) {
                    finalResponse = nested.toString();
                }
            }
        } catch (Exception ignored) {
            logger.debug("解析 Coze 嵌套响应失败，使用原始 data 文本");
        }
        return finalResponse;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ==================== 演示模式：直接操作数据库 ====================

    /**
     * 演示模式：解析用户输入并执行实际数据库操作
     */
    private String handleDemoInput(String input) {
        logger.info("演示模式处理输入：{}", input);

        if (input == null || input.trim().isEmpty()) {
            return "您发送了空消息，请输入具体内容。";
        }

        String lowerInput = input.toLowerCase();

        // 帮助
        if (lowerInput.contains("帮助") || lowerInput.contains("help") || lowerInput.contains("介绍") || lowerInput.contains("你能做什么")) {
            return buildHelpMessage();
        }

        // 问候
        if (lowerInput.contains("你好") || lowerInput.contains("嗨") || lowerInput.contains("hello") || lowerInput.contains("hi")) {
            return buildGreetingMessage();
        }

        // ===== 自然语言指令解析（优先处理复合指令）=====
        
        // 检查是否是"给XX增加班次"类型的指令
        if (isCreateShiftForPersonCommand(input)) {
            logger.info("识别到自然语言排班指令：{}", input);
            return naturalLanguageService.handleCreateShiftForPerson(input);
        }

        // ===== 简单关键词匹配 =====

        // 查询科室
        if (lowerInput.contains("部门") || lowerInput.contains("科室")) {
            return handleQueryDepartments();
        }

        // 查询员工
        if (lowerInput.contains("员工") || lowerInput.contains("医生") || lowerInput.contains("护士")) {
            return handleQueryEmployees(input);
        }

        // 查询排班
        if (lowerInput.contains("排班") || lowerInput.contains("班次")) {
            return handleQueryShifts(input);
        }

        // 创建科室
        if (lowerInput.contains("添加部门") || lowerInput.contains("新增部门") || lowerInput.contains("创建部门")
                || lowerInput.contains("添加科室") || lowerInput.contains("新增科室") || lowerInput.contains("创建科室")) {
            return handleCreateDepartment(input);
        }

        // 创建排班
        if (lowerInput.contains("创建排班") || lowerInput.contains("新增排班") || lowerInput.contains("添加排班")) {
            return handleCreateShift(input);
        }

        // 校验排班
        if (lowerInput.contains("校验") || lowerInput.contains("检查") || lowerInput.contains("冲突")) {
            return handleValidateShifts();
        }

        // 统计概览
        if (lowerInput.contains("统计") || lowerInput.contains("概览") || lowerInput.contains("总览")) {
            return handleOverview();
        }

        // 默认回复
        return " 已收到您的消息：\"" + input + "\"\n\n" +
               "我是医院排班智能助手，目前处于本地演示模式，可以直接操作数据库。\n\n" +
               "您可以尝试以下指令：\n" +
               "• 部门表 - 查看所有科室\n" +
               "• 员工表 - 查看所有员工\n" +
               "• 排班表 - 查看所有班次\n" +
               "• 给王医生增加一个 2026 年 8 月 5 号早上 8 点到下午 5 点的班次\n" +
               "• 添加部门：神经外科 - 创建新科室\n" +
               "• 校验排班 - 检查班次冲突\n" +
               "• 统计 - 查看系统概览\n\n" +
               "如需接入真实 Coze AI，请配置 COZE_API_KEY 和 COZE_WORKFLOW_ID";
    }

    /**
     * 判断是否是"给XX增加班次"类型的自然语言指令
     */
    private boolean isCreateShiftForPersonCommand(String input) {
        String lower = input.toLowerCase();
        // 包含"增加班次"、"添加班次"、"创建班次"、"安排班次"等关键词
        boolean hasShiftAction = lower.contains("增加班次") || lower.contains("添加班次") || 
                                  lower.contains("创建班次") || lower.contains("安排班次") ||
                                  lower.contains("排一个班") || lower.contains("加一个班");
        // 包含人物名称（医生、护士等）
        boolean hasPerson = lower.contains("医生") || lower.contains("护士") || lower.contains("主任");
        // 包含日期或时间
        boolean hasDateTime = lower.matches(".*\\d{4}.*\\d{1,2}.*\\d{1,2}.*") || 
                               lower.contains("今天") || lower.contains("明天") || lower.contains("后天") ||
                               lower.contains("点") || lower.contains(":");
        
        return hasShiftAction && hasPerson && hasDateTime;
    }

    private String buildHelpMessage() {
        long deptCount = departmentRepository.count();
        long userCount = userAccountRepository.count();
        long shiftCount = shiftRepository.count();

        return "🤖 我是医院排班智能助手，当前系统数据：\n\n" +
               "📊 系统状态\n" +
               "  - 科室数量：" + deptCount + " 个\n" +
               "  - 员工数量：" + userCount + " 人\n" +
               "  - 班次数量：" + shiftCount + " 个\n\n" +
               "📋 支持的指令\n" +
               "  1. 部门表 / 科室列表 - 查看所有科室\n" +
               "  2. 员工表 - 查看所有员工\n" +
               "  3. 排班表 - 查看所有班次\n" +
               "  4. 添加部门：XXX - 创建新科室\n" +
               "  5. 校验排班 - 检查班次冲突\n" +
               "  6. 统计 - 查看系统概览\n\n" +
               "💡 直接输入自然语言即可，我会自动识别并执行操作。";
    }

    private String buildGreetingMessage() {
        return "你好！😊 我是智能排班管理专家，已连接到本地数据库。\n\n" +
               "我可以帮您：\n" +
               "• 查询科室、员工、排班信息\n" +
               "• 创建新科室和排班\n" +
               "• 校验排班冲突\n" +
               "• 查看系统统计概览\n\n" +
               "请输入指令，例如\"部门表\"或\"排班表\"。";
    }

    private String handleQueryDepartments() {
        try {
            List<Department> depts = departmentRepository.findAll();
            if (depts.isEmpty()) {
                return "📋 当前没有科室数据。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(" 科室列表（共 ").append(depts.size()).append(" 个）：\n\n");
            for (Department d : depts) {
                sb.append("  ").append(d.getId()).append(". ").append(d.getName());
                if (d.getDescription() != null && !d.getDescription().isEmpty()) {
                    sb.append(" - ").append(d.getDescription());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("查询科室失败", e);
            return "❌ 查询科室失败：" + e.getMessage();
        }
    }

    private String handleQueryEmployees(String input) {
        try {
            logger.info("查询员工，输入：{}", input);
            List<UserAccount> users = userAccountRepository.findAll();
            logger.info("查询到 {} 个员工", users.size());
            if (users.isEmpty()) {
                return "👥 当前没有员工数据。";
            }

            // 尝试按科室过滤
            String lowerInput = input.toLowerCase();
            String targetDept = null;
            for (Department d : departmentRepository.findAll()) {
                if (lowerInput.contains(d.getName().toLowerCase())) {
                    targetDept = d.getName();
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (UserAccount u : users) {
                if (targetDept != null && u.getDepartment() != null
                        && !u.getDepartment().getName().equals(targetDept)) {
                    continue;
                }
                count++;
                if (count <= 30) {
                    sb.append("  ").append(u.getId()).append(". ").append(u.getFullName());
                    sb.append(" (").append(u.getEmail()).append(")");
                    if (u.getDepartment() != null) {
                        sb.append(" - ").append(u.getDepartment().getName());
                    }
                    if (u.getRoles() != null && !u.getRoles().isEmpty()) {
                        sb.append(" [").append(u.getRoles().iterator().next().getName()).append("]");
                    }
                    sb.append("\n");
                }
            }

            String header = targetDept != null
                ? "👥 " + targetDept + " 员工列表（共 " + count + " 人）：\n\n"
                : "👥 员工列表（共 " + users.size() + " 人）：\n\n";

            if (count == 0) {
                return "👥 未找到匹配的员工。";
            }
            return header + sb.toString();
        } catch (Exception e) {
            logger.error("查询员工失败", e);
            return "❌ 查询员工失败：" + e.getMessage();
        }
    }

    private String handleQueryShifts(String input) {
        try {
            List<Shift> shifts = shiftRepository.findAll();
            if (shifts.isEmpty()) {
                return "📅 当前没有排班数据。";
            }

            // 尝试按状态过滤
            String lowerInput = input.toLowerCase();
            boolean showOpen = lowerInput.contains("待指派") || lowerInput.contains("open") || lowerInput.contains("未分配");

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Shift s : shifts) {
                if (showOpen && s.getStatus() != ShiftStatus.OPEN) {
                    continue;
                }
                count++;
                if (count <= 20) {
                    sb.append("  ").append(s.getId()).append(". ");
                    sb.append(s.getStartTime().toLocalDate()).append(" ");
                    sb.append(s.getRequiredRole()).append(" ");
                    sb.append(s.getStatus());
                    if (s.getAssignedUser() != null) {
                        sb.append(" - ").append(s.getAssignedUser().getFullName());
                    } else {
                        sb.append(" - 待指派");
                    }
                    if (s.getDepartment() != null) {
                        sb.append(" [").append(s.getDepartment().getName()).append("]");
                    }
                    sb.append("\n");
                }
            }

            String header = showOpen ? "📅 待指派班次" : " 排班列表";
            String result = header + "（共 " + shifts.size() + " 个" + (showOpen ? "，待指派 " + count + " 个" : "") + "）：\n\n" + sb.toString();

            if (shifts.size() > 20) {
                result += "\n... 仅显示前 20 条";
            }
            return result;
        } catch (Exception e) {
            logger.error("查询排班失败", e);
            return "❌ 查询排班失败：" + e.getMessage();
        }
    }

    private String handleCreateDepartment(String input) {
        try {
            // 提取科室名称：添加部门：XXX 或 新增科室 XXX
            Pattern pattern = Pattern.compile("(?:添加|新增|创建)(?:部门|科室)[：:：]?\\s*(\\S+)");
            Matcher matcher = pattern.matcher(input);

            String name = null;
            String description = null;

            if (matcher.find()) {
                name = matcher.group(1).trim();
                // 尝试提取描述（逗号后面的内容）
                String rest = input.substring(matcher.end()).trim();
                if (rest.startsWith("，") || rest.startsWith(",") || rest.startsWith(":") || rest.startsWith("：")) {
                    description = rest.substring(1).trim();
                }
            }

            if (name == null || name.isEmpty()) {
                return "❌ 请指定科室名称。格式：添加部门：神经外科\n\n" +
                       "示例：\n" +
                       "  添加部门：神经外科，神经系统手术科室\n" +
                       "  新增科室：儿科";
            }

            // 检查是否已存在
            if (departmentRepository.findByName(name).isPresent()) {
                return "️ 科室\"" + name + "\"已存在，请勿重复创建。";
            }

            Department dept = new Department(name, description);
            departmentRepository.save(dept);
            logger.info("演示模式创建科室：{}", name);

            return "✅ 科室创建成功！\n\n" +
                   "  名称：" + name + "\n" +
                   (description != null ? "  描述：" + description + "\n" : "") +
                   "  ID：" + dept.getId();
        } catch (Exception e) {
            logger.error("创建科室失败", e);
            return "❌ 创建科室失败：" + e.getMessage();
        }
    }

    private String handleCreateShift(String input) {
        try {
            // 尝试解析：创建排班 2026-08-01 医生 呼吸内科
            Pattern datePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
            Matcher dateMatcher = datePattern.matcher(input);

            if (!dateMatcher.find()) {
                return "❌ 请指定排班日期。格式：创建排班 2026-08-01 医生 呼吸内科\n\n" +
                       "示例：\n" +
                       "  创建排班 2026-08-01 医生 呼吸内科\n" +
                       "  新增排班 2026-08-02 护士 急诊科";
            }

            String dateStr = dateMatcher.group(1);
            LocalDateTime startTime = LocalDateTime.parse(dateStr + "T08:00:00");
            LocalDateTime endTime = LocalDateTime.parse(dateStr + "T16:00:00");

            // 解析角色
            RoleType role = RoleType.DOCTOR;
            if (input.contains("护士")) {
                role = RoleType.NURSE;
            }

            // 解析科室
            Department dept = null;
            for (Department d : departmentRepository.findAll()) {
                if (input.contains(d.getName())) {
                    dept = d;
                    break;
                }
            }
            if (dept == null) {
                dept = departmentRepository.findById(1L).orElse(null);
            }

            if (dept == null) {
                return "❌ 未找到匹配的科室，请先创建科室。";
            }

            Shift shift = new Shift(startTime, endTime, role, dept);
            shift.setStatus(ShiftStatus.OPEN);
            shiftRepository.save(shift);
            logger.info("演示模式创建排班：{} {} {}", dateStr, role, dept.getName());

            return "✅ 排班创建成功！\n\n" +
                   "  日期：" + dateStr + "\n" +
                   "  角色：" + role + "\n" +
                   "  科室：" + dept.getName() + "\n" +
                   "  时间：08:00-16:00\n" +
                   "  状态：待指派\n" +
                   "  ID：" + shift.getId();
        } catch (Exception e) {
            logger.error("创建排班失败", e);
            return "❌ 创建排班失败：" + e.getMessage();
        }
    }

    private String handleValidateShifts() {
        try {
            List<Shift> shifts = shiftRepository.findAll();
            int total = shifts.size();
            int assigned = 0;
            int open = 0;
            int conflicts = 0;

            for (Shift s : shifts) {
                if (s.getStatus() == ShiftStatus.ASSIGNED) {
                    assigned++;
                } else if (s.getStatus() == ShiftStatus.OPEN) {
                    open++;
                }
            }

            // 简单冲突检测：同一员工同一时间段多个班次
            Map<Long, List<Shift>> byUser = new HashMap<>();
            for (Shift s : shifts) {
                if (s.getAssignedUser() != null) {
                    byUser.computeIfAbsent(s.getAssignedUser().getId(), k -> new java.util.ArrayList<>()).add(s);
                }
            }
            for (List<Shift> userShifts : byUser.values()) {
                for (int i = 0; i < userShifts.size(); i++) {
                    for (int j = i + 1; j < userShifts.size(); j++) {
                        Shift a = userShifts.get(i);
                        Shift b = userShifts.get(j);
                        if (a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime())) {
                            conflicts++;
                        }
                    }
                }
            }

            double coverage = total > 0 ? (double) assigned / total * 100 : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("🔍 排班校验结果：\n\n");
            sb.append("📊 基本统计\n");
            sb.append("  - 总班次：").append(total).append("\n");
            sb.append("  - 已指派：").append(assigned).append("\n");
            sb.append("  - 待指派：").append(open).append("\n");
            sb.append("  - 覆盖率：").append(String.format("%.1f%%", coverage)).append("\n\n");

            if (conflicts > 0) {
                sb.append("⚠️ 发现 ").append(conflicts).append(" 个时间冲突，请调整排班。\n");
            } else {
                sb.append("✅ 未发现时间冲突。\n");
            }

            if (open > 0) {
                sb.append("\n💡 提示：还有 ").append(open).append(" 个班次待指派。");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("校验排班失败", e);
            return "❌ 校验排班失败：" + e.getMessage();
        }
    }

    private String handleOverview() {
        try {
            long deptCount = departmentRepository.count();
            long userCount = userAccountRepository.count();
            long shiftCount = shiftRepository.count();
            long openShifts = shiftRepository.findByStatus(ShiftStatus.OPEN).size();
            long assignedShifts = shiftRepository.findByStatus(ShiftStatus.ASSIGNED).size();

            return "📊 系统概览：\n\n" +
                   "🏥 科室：" + deptCount + " 个\n" +
                   "👥 员工：" + userCount + " 人\n" +
                   "📅 班次：" + shiftCount + " 个\n" +
                   "  - 已指派：" + assignedShifts + "\n" +
                   "  - 待指派：" + openShifts + "\n" +
                   "  - 覆盖率：" + (shiftCount > 0 ? String.format("%.1f%%", (double) assignedShifts / shiftCount * 100) : "0%");
        } catch (Exception e) {
            logger.error("获取概览失败", e);
            return "❌ 获取概览失败：" + e.getMessage();
        }
    }
}
