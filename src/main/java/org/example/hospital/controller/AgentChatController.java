package org.example.hospital.controller;

import java.time.OffsetDateTime;
import org.example.hospital.dto.ChatMessage;
import org.example.hospital.dto.CozeRequest;
import org.example.hospital.dto.CozeResponse;
import org.example.hospital.service.AgentChatService;
import org.example.hospital.service.CozeAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class AgentChatController {

    private static final Logger logger = LoggerFactory.getLogger(AgentChatController.class);

    private final AgentChatService agentChatService;
    private final CozeAgentService cozeAgentService;
    private final SimpMessagingTemplate messagingTemplate;

    public AgentChatController(
            AgentChatService agentChatService,
            CozeAgentService cozeAgentService,
            SimpMessagingTemplate messagingTemplate) {
        this.agentChatService = agentChatService;
        this.cozeAgentService = cozeAgentService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/agent-chat")
    public void chat(ChatMessage message) {
        if (message.getRole() == null || message.getRole().isBlank()) {
            message.setRole("CLIENT");
        }

        // 1. 保存用户消息并广播
        ChatMessage savedUserMessage = agentChatService.save(message);
        messagingTemplate.convertAndSend("/topic/agent-chat", savedUserMessage);

        // 2. 调用智能体生成回复
        try {
            CozeRequest cozeRequest = new CozeRequest();
            cozeRequest.setContent(message.getContent());

            CozeResponse cozeResponse = cozeAgentService.chat(cozeRequest);

            if ("SUCCESS".equals(cozeResponse.getStatus()) && cozeResponse.getResponse() != null) {
                // 3. 保存并发送智能体回复
                ChatMessage agentReply = new ChatMessage();
                agentReply.setSender("Coze Agent");
                agentReply.setRole("AGENT");
                agentReply.setContent(cozeResponse.getResponse());
                agentReply.setTimestamp(OffsetDateTime.now());

                ChatMessage savedAgentMessage = agentChatService.save(agentReply);
                messagingTemplate.convertAndSend("/topic/agent-chat", savedAgentMessage);
            } else {
                logger.warn("智能体回复失败：status={}, error={}", cozeResponse.getStatus(), cozeResponse.getError());
            }
        } catch (Exception e) {
            logger.error("调用智能体异常", e);
        }
    }
}
