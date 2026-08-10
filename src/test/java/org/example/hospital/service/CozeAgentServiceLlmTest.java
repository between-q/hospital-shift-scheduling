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
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.dto.CozeRequest;
import org.example.hospital.dto.CozeResponse;
import org.example.hospital.repository.DepartmentRepository;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CozeAgentServiceLlmTest {

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AgentChatService agentChatService;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private NaturalLanguageService naturalLanguageService;

    @Mock
    private LlmService llmService;

    @InjectMocks
    private CozeAgentService cozeAgentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cozeAgentService, "cozeApiUrl", "http://localhost:8000/api/coze/chat");
        ReflectionTestUtils.setField(cozeAgentService, "cozeApiKey", "");
        ReflectionTestUtils.setField(cozeAgentService, "workflowId", "");
    }

    private void stubDemoMode() {
        when(departmentRepository.count()).thenReturn(4L);
        when(userAccountRepository.count()).thenReturn(5L);
        when(shiftRepository.count()).thenReturn(8L);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Collections.emptyList());
        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(userAccountRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @Test
    void chat_usesLlmWhenCozeNotConfigured() {
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.chat("你好")).thenReturn("你好！我是排班助手，有什么可以帮你的？");

        CozeRequest request = new CozeRequest("你好", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("你好！我是排班助手，有什么可以帮你的？", response.getResponse());
        verify(llmService).chat("你好");
        verify(agentChatService).save(any());
    }

    @Test
    void chat_fallsBackToDemoWhenLlmUnavailable() {
        when(llmService.isAvailable()).thenReturn(false);
        stubDemoMode();

        CozeRequest request = new CozeRequest("帮助", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getResponse().contains("医院排班智能助手"));
        verify(llmService, never()).chat(anyString());
    }

    @Test
    void chat_fallsBackToDemoWhenLlmReturnsNull() {
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.chat("帮助")).thenReturn(null);
        stubDemoMode();

        CozeRequest request = new CozeRequest("帮助", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getResponse().contains("排班智能助手"));
    }

    @Test
    void chat_fallsBackToDemoWhenLlmThrowsException() {
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.chat("统计")).thenThrow(new RuntimeException("Connection timeout"));
        stubDemoMode();

        CozeRequest request = new CozeRequest("统计", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getResponse().contains("系统概览"));
    }

    @Test
    void chat_returnsFailedOnNullRequest() {
        CozeResponse response = cozeAgentService.chat(null);

        assertEquals("FAILED", response.getStatus());
    }

    @Test
    void chat_returnsFailedOnEmptyContent() {
        CozeRequest request = new CozeRequest("", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("FAILED", response.getStatus());
    }

    @Test
    void chat_llmHandlesNaturalLanguageShiftQuery() {
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.chat("呼吸内科有哪些医生？"))
            .thenReturn("呼吸内科目前有李医生和王医生两位医生。");

        CozeRequest request = new CozeRequest("呼吸内科有哪些医生？", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("呼吸内科目前有李医生和王医生两位医生。", response.getResponse());
    }

    @Test
    void chat_demoHandlesDepartmentQuery() {
        when(llmService.isAvailable()).thenReturn(false);

        Department dept = new Department("呼吸内科", "呼吸系统疾病");
        when(departmentRepository.findAll()).thenReturn(List.of(dept));

        CozeRequest request = new CozeRequest("部门表", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getResponse().contains("呼吸内科"));
    }

    @Test
    void chat_demoHandlesShiftQuery() {
        when(llmService.isAvailable()).thenReturn(false);

        Department dept = new Department("急诊科", "急诊");
        UserAccount nurse = new UserAccount("nurse@test.com", "pass", "陈护士");
        Role nurseRole = new Role(RoleType.NURSE);
        nurse.setRoles(Set.of(nurseRole));

        Shift shift = new Shift(
            java.time.LocalDateTime.of(2026, 8, 5, 8, 0),
            java.time.LocalDateTime.of(2026, 8, 5, 16, 0),
            RoleType.NURSE, dept);
        shift.setStatus(ShiftStatus.ASSIGNED);
        shift.setAssignedUser(nurse);

        when(shiftRepository.findAll()).thenReturn(List.of(shift));

        CozeRequest request = new CozeRequest("排班表", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getResponse().contains("排班列表"));
    }

    @Test
    void chat_llmTakesPriorityOverDemo() {
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.chat(anyString())).thenReturn("LLM 回复");

        CozeRequest request = new CozeRequest("部门表", null);
        CozeResponse response = cozeAgentService.chat(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("LLM 回复", response.getResponse());
        verify(llmService).chat("部门表");
        verify(departmentRepository, never()).findAll();
    }
}
