package org.example.hospital.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.example.hospital.domain.Department;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 大模型推理服务
 * 通过 OpenAI 兼容 API 调用本地或远程 LLM（如 Ollama、DeepSeek 等）
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    @Value("${llm.api.base-url:http://localhost:11434/v1}")
    private String baseUrl;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.model:qwen2.5}")
    private String model;

    @Value("${llm.api.max-tokens:1024}")
    private int maxTokens;

    @Value("${llm.api.temperature:0.7}")
    private double temperature;

    @Value("${llm.api.timeout-seconds:60}")
    private int timeoutSeconds;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final ShiftRepository shiftRepository;

    public LlmService(ObjectMapper objectMapper,
                      DepartmentRepository departmentRepository,
                      UserAccountRepository userAccountRepository,
                      ShiftRepository shiftRepository) {
        this.objectMapper = objectMapper;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.shiftRepository = shiftRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void logConfig() {
        logger.info("[LLM_CONFIG] baseUrl={}, model={}, apiKeyPresent={}, maxTokens={}, temperature={}",
            baseUrl, model,
            apiKey != null && !apiKey.trim().isEmpty(),
            maxTokens, temperature);
    }

    /**
     * 判断 LLM 是否可用（已配置 base-url 和 model）
     */
    public boolean isAvailable() {
        return baseUrl != null && !baseUrl.trim().isEmpty()
            && model != null && !model.trim().isEmpty();
    }

    /**
     * 调用 LLM 获取智能回复
     */
    public String chat(String userMessage) {
        if (!isAvailable()) {
            return null;
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String responseText = callOpenAiCompatibleApi(systemPrompt, userMessage);
            if (responseText != null && !responseText.trim().isEmpty()) {
                logger.info("LLM 回复成功，长度：{}", responseText.length());
                return responseText;
            }
            logger.warn("LLM 返回空响应");
            return null;
        } catch (Exception e) {
            logger.error("LLM 调用失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建包含当前系统上下文的 System Prompt
     */
    String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是医院排班智能助手，负责帮助管理员和医护人员进行排班管理。\n\n");

        try {
            long deptCount = departmentRepository.count();
            long userCount = userAccountRepository.count();
            long shiftCount = shiftRepository.count();
            long openCount = shiftRepository.findByStatus(ShiftStatus.OPEN).size();
            long assignedCount = shiftRepository.findByStatus(ShiftStatus.ASSIGNED).size();

            List<Department> departments = departmentRepository.findAll();
            String deptNames = departments.stream()
                .map(Department::getName)
                .collect(Collectors.joining("、"));

            List<UserAccount> users = userAccountRepository.findAll();
            long doctorCount = users.stream()
                .filter(u -> u.getRoles().stream()
                    .anyMatch(r -> r.getName() != null && r.getName().name().equals("DOCTOR")))
                .count();
            long nurseCount = users.size() - doctorCount;

            sb.append("当前系统状态：\n");
            sb.append("- 科室：").append(deptCount).append(" 个");
            if (!deptNames.isEmpty()) {
                sb.append("（").append(deptNames).append("）");
            }
            sb.append("\n");
            sb.append("- 员工：").append(userCount).append(" 人（医生 ").append(doctorCount).append("，护士 ").append(nurseCount).append("）\n");
            sb.append("- 班次：").append(shiftCount).append(" 个（已指派 ").append(assignedCount).append("，待指派 ").append(openCount).append("）\n\n");

            if (!users.isEmpty()) {
                sb.append("员工列表：\n");
                for (UserAccount u : users) {
                    sb.append("- ").append(u.getFullName());
                    if (u.getDepartment() != null) {
                        sb.append("（").append(u.getDepartment().getName()).append("）");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            List<Shift> recentShifts = shiftRepository.findByStatus(ShiftStatus.OPEN);
            if (!recentShifts.isEmpty()) {
                sb.append("待指派班次：\n");
                for (Shift s : recentShifts) {
                    sb.append("- ").append(s.getStartTime().toLocalDate())
                      .append(" ").append(s.getStartTime().toLocalTime())
                      .append("-").append(s.getEndTime().toLocalTime())
                      .append(" ").append(s.getRequiredRole());
                    if (s.getDepartment() != null) {
                        sb.append("（").append(s.getDepartment().getName()).append("）");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            logger.warn("构建系统上下文失败，使用基础 prompt：{}", e.getMessage());
        }

        sb.append("功能说明：\n");
        sb.append("1. 查询科室、员工、排班信息并给出分析\n");
        sb.append("2. 分析排班合理性（工时分布、覆盖率、冲突检测）\n");
        sb.append("3. 用自然语言回答排班相关问题\n");
        sb.append("4. 建议优化排班方案\n");
        sb.append("5. 执行操作指令：添加部门、创建排班、校验排班、统计概览\n\n");
        sb.append("回复要求：\n");
        sb.append("- 用友好、专业的语气回复，使用 emoji 增强可读性\n");
        sb.append("- 回复使用中文\n");
        sb.append("- 如果用户请求执行操作（如创建排班），先确认信息再给出执行结果\n");
        sb.append("- 如果用户只是询问信息，直接给出分析和建议\n");

        return sb.toString();
    }

    /**
     * 调用 OpenAI 兼容 API
     */
    private String callOpenAiCompatibleApi(String systemPrompt, String userMessage) throws Exception {
        String url = resolveApiUrl();
        logger.info("调用 LLM API：url={}, model={}", url, model);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        payload.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<String> request = new HttpEntity<>(
            objectMapper.writeValueAsString(payload),
            headers
        );

        ResponseEntity<String> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            request,
            String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("LLM API 返回状态码：" + response.getStatusCode());
        }

        return extractContentFromResponse(response.getBody());
    }

    /**
     * 解析 OpenAI 格式的响应 JSON，提取 content
     */
    private String extractContentFromResponse(String responseBody) throws Exception {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            logger.warn("LLM 响应中没有 choices 字段");
            return null;
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message == null) {
            logger.warn("LLM 响应中没有 message 字段");
            return null;
        }

        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return null;
        }
        String text = content.asText();
        return stripThinkingTags(text);
    }

    /**
     * 移除 MiniMax 模型输出中的 <think>...</think> 标签，
     * 只保留最终回复内容。
     */
    private String stripThinkingTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 移除 <think>...</think> 块（支持多行）
        String cleaned = text.replaceAll("(?s)<think>.*?</think>", "").trim();
        // 移除可能残留的孤立标签
        cleaned = cleaned.replaceAll("(?s)<think>.*", "").trim();
        cleaned = cleaned.replaceAll("(?s).*</think>", "").trim();
        return cleaned.isEmpty() ? text : cleaned;
    }

    /**
     * 解析 API URL，确保指向 chat completions 端点
     */
    private String resolveApiUrl() {
        String base = baseUrl != null ? baseUrl.trim() : "";
        if (base.isEmpty()) {
            base = "http://localhost:11434/v1";
        }

        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        if (base.endsWith("/")) {
            return base + "chat/completions";
        }
        return base + "/chat/completions";
    }
}
