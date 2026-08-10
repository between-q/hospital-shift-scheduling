package org.example.hospital.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.example.hospital.domain.Department;
import org.example.hospital.domain.Role;
import org.example.hospital.domain.RoleType;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.repository.DepartmentRepository;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ShiftRepository shiftRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LlmService llmService;

    @BeforeEach
    void setUp() throws Exception {
        llmService = new LlmService(objectMapper,
            departmentRepository, userAccountRepository, shiftRepository);
        // 用反射替换构造函数创建的 RestTemplate 为 mock
        ReflectionTestUtils.setField(llmService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(llmService, "baseUrl", "https://api.minimaxi.com/v1");
        ReflectionTestUtils.setField(llmService, "model", "MiniMax-M2.7");
        ReflectionTestUtils.setField(llmService, "apiKey", "gw-1df9095c-ad99-4d8d-bb0e-042a1af27102");
        ReflectionTestUtils.setField(llmService, "maxTokens", 1024);
        ReflectionTestUtils.setField(llmService, "temperature", 0.7);
        ReflectionTestUtils.setField(llmService, "timeoutSeconds", 60);
    }

    @Test
    void isAvailable_withValidConfig_returnsTrue() {
        assertTrue(llmService.isAvailable());
    }

    @Test
    void isAvailable_withEmptyBaseUrl_returnsFalse() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "");
        assertFalse(llmService.isAvailable());
    }

    @Test
    void isAvailable_withEmptyModel_returnsFalse() {
        ReflectionTestUtils.setField(llmService, "model", "");
        assertFalse(llmService.isAvailable());
    }

    @Test
    void chat_returnsValidResponse() {
        when(departmentRepository.count()).thenReturn(4L);
        when(userAccountRepository.count()).thenReturn(5L);
        when(shiftRepository.count()).thenReturn(8L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        String mockResponse = "{\"choices\":[{\"message\":{\"content\":\"你好！我是排班助手。\"}}]}";
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        String result = llmService.chat("你好");

        assertNotNull(result);
        assertEquals("你好！我是排班助手。", result);
    }

    @Test
    void chat_withEmptyChoices_returnsNull() {
        when(departmentRepository.count()).thenReturn(0L);
        when(userAccountRepository.count()).thenReturn(0L);
        when(shiftRepository.count()).thenReturn(0L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        String mockResponse = "{\"choices\":[]}";
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        String result = llmService.chat("你好");

        assertNull(result);
    }

    @Test
    void chat_withNullContent_returnsNull() {
        when(departmentRepository.count()).thenReturn(0L);
        when(userAccountRepository.count()).thenReturn(0L);
        when(shiftRepository.count()).thenReturn(0L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        String mockResponse = "{\"choices\":[{\"message\":{\"content\":null}}]}";
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        String result = llmService.chat("你好");

        assertNull(result);
    }

    @Test
    void chat_withApiError_returnsNull() {
        when(departmentRepository.count()).thenReturn(0L);
        when(userAccountRepository.count()).thenReturn(0L);
        when(shiftRepository.count()).thenReturn(0L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        String result = llmService.chat("你好");

        assertNull(result);
    }

    @Test
    void chat_notAvailable_returnsNull() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "");

        String result = llmService.chat("你好");

        assertNull(result);
    }

    @Test
    void buildSystemPrompt_containsContextData() {
        Department dept = new Department("呼吸内科", "呼吸系统疾病");
        when(departmentRepository.count()).thenReturn(1L);
        when(userAccountRepository.count()).thenReturn(2L);
        when(shiftRepository.count()).thenReturn(3L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(List.of(dept));

        UserAccount doctor = new UserAccount("doc@test.com", "pass", "李医生");
        Role role = new Role(RoleType.DOCTOR);
        doctor.setRoles(Set.of(role));
        doctor.setDepartment(dept);
        when(userAccountRepository.findAll()).thenReturn(List.of(doctor));

        String prompt = llmService.buildSystemPrompt();

        assertTrue(prompt.contains("医院排班智能助手"));
        assertTrue(prompt.contains("呼吸内科"));
        assertTrue(prompt.contains("李医生"));
        assertTrue(prompt.contains("排班管理"));
    }

    @Test
    void chat_sendsCorrectPayload() {
        when(departmentRepository.count()).thenReturn(0L);
        when(userAccountRepository.count()).thenReturn(0L);
        when(shiftRepository.count()).thenReturn(0L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        String mockResponse = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        llmService.chat("测试消息");

        verify(restTemplate).exchange(
            contains("/chat/completions"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void resolveApiUrl_withV1Suffix() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "https://api.minimaxi.com/v1");
        String url = (String) ReflectionTestUtils.invokeMethod(llmService, "resolveApiUrl");
        assertEquals("https://api.minimaxi.com/v1/chat/completions", url);
    }

    @Test
    void resolveApiUrl_withTrailingSlash() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "http://localhost:11434/");
        String url = (String) ReflectionTestUtils.invokeMethod(llmService, "resolveApiUrl");
        assertEquals("http://localhost:11434/chat/completions", url);
    }

    @Test
    void resolveApiUrl_withChatCompletionsSuffix() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "http://localhost:11434/v1/chat/completions");
        String url = (String) ReflectionTestUtils.invokeMethod(llmService, "resolveApiUrl");
        assertEquals("http://localhost:11434/v1/chat/completions", url);
    }

    @Test
    void resolveApiUrl_withPlainText() {
        ReflectionTestUtils.setField(llmService, "baseUrl", "http://localhost:11434");
        String url = (String) ReflectionTestUtils.invokeMethod(llmService, "resolveApiUrl");
        assertEquals("http://localhost:11434/chat/completions", url);
    }

    @Test
    void stripThinkingTags_removesThinkingBlock() {
        String input = "<think>用户在问你好，我应该友好回复。</think>你好！我是排班助手。";
        String result = (String) ReflectionTestUtils.invokeMethod(llmService, "stripThinkingTags", input);
        assertEquals("你好！我是排班助手。", result);
    }

    @Test
    void stripThinkingTags_preservesNormalText() {
        String input = "你好！我是排班助手。";
        String result = (String) ReflectionTestUtils.invokeMethod(llmService, "stripThinkingTags", input);
        assertEquals("你好！我是排班助手。", result);
    }

    @Test
    void stripThinkingTags_handlesMultilineThinking() {
        String input = "<think>\n这是多行\n思考内容\n</think>最终回复";
        String result = (String) ReflectionTestUtils.invokeMethod(llmService, "stripThinkingTags", input);
        assertEquals("最终回复", result);
    }

    @Test
    void stripThinkingTags_handlesNullInput() {
        String result = (String) ReflectionTestUtils.invokeMethod(llmService, "stripThinkingTags", (String) null);
        assertNull(result);
    }

    @Test
    void stripThinkingTags_handlesEmptyInput() {
        String result = (String) ReflectionTestUtils.invokeMethod(llmService, "stripThinkingTags", "");
        assertEquals("", result);
    }

    @Test
    void chat_stripsThinkingTagsFromResponse() {
        when(departmentRepository.count()).thenReturn(0L);
        when(userAccountRepository.count()).thenReturn(0L);
        when(shiftRepository.count()).thenReturn(0L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());

        String mockResponse = "{\"choices\":[{\"message\":{\"content\":\"<think>用户在问你好</think>你好！\"}}]}";
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        String result = llmService.chat("你好");

        assertNotNull(result);
        assertEquals("你好！", result);
    }
}
