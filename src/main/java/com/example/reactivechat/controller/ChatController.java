package com.example.reactivechat.controller;

import com.example.reactivechat.model.ChatMessage;
import com.example.reactivechat.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class ChatController {

    private final Map<String, ChatMessage> messageHistory = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void addMessage(String messageJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = objectMapper.readValue(messageJson, Map.class);

            if ("CHAT".equals(messageMap.get("type"))) {
                ChatMessage chatMessage = ChatMessage.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(MessageType.CHAT)
                    .sender((String) messageMap.get("sender"))
                    .content((String) messageMap.get("content"))
                    .timestamp(LocalDateTime.now())
                    .isRead(false)
                    .recipient("all") // For now, all messages are broadcast
                    .build();

                messageHistory.put(chatMessage.getId(), chatMessage);
            }
        } catch (Exception e) {
        }
    }
}
