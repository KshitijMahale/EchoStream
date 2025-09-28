package com.example.reactivechat.model;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    private String id;
    private MessageType type;
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private boolean isRead;
    private String recipient;
}
