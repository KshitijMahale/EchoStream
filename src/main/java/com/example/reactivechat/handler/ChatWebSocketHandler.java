package com.example.reactivechat.handler;

import com.example.reactivechat.controller.ChatController;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatController chatController;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastTyping = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final Sinks.Many<String> chatSink = Sinks.many().multicast().onBackpressureBuffer(5000, false);

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
        Flux<WebSocketMessage> output = chatSink.asFlux()
                .map(session::textMessage);

        Flux<String> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(payload -> {
                    log.info("Received: {}", payload);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> message = objectMapper.readValue(payload, Map.class);
                        String type = (String) message.get("type");
                        String sender = (String) message.get("sender");

                        if (type != null && sender != null) {
                            switch (type) {
                                case "JOIN" -> {
                                    activeUsers.add(sender);
                                    userSessions.put(sender, session);
                                    sendOnlineUsersToUser(sender);
                                    broadcastUserOnline(sender);
                                }
                                case "LEAVE" -> {
                                    activeUsers.remove(sender);
                                    userSessions.remove(sender);
                                    broadcastUserOffline(sender);
                                }
                            }

                            if (type.equals("JOIN") ||
                                    type.equals("LEAVE") ||
                                    type.equals("STOP_TYPING")) {
                                chatSink.tryEmitNext(payload);
                            }
                            if (type.equals("CHAT")) {
                                chatController.addMessage(payload); // Store message
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
                                    String messageId = java.util.UUID.randomUUID().toString();
                                    messageMap.put("id", messageId);
                                    String enhancedPayload = objectMapper.writeValueAsString(messageMap);
                                    chatSink.tryEmitNext(enhancedPayload);
                                } catch (Exception e) {
                                    log.error("Failed to add message ID", e);
                                    chatSink.tryEmitNext(payload);
                                }
                            }
                            if (type.equals("MESSAGE_READ")) {
                                String messageId = (String) message.get("messageId");
                                String reader = (String) message.get("reader");
                                try {
                                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                        .uri(java.net.URI.create("http://localhost:8080/api/messages/" + messageId + "/read?reader=" + reader))
                                        .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                                        .build();
                                    client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                                } catch (Exception e) {
                                    log.error("Failed to mark message as read", e);
                                }
                                chatSink.tryEmitNext(payload);
                            }
                            if (type.equals("TYPING")) {
                                long now = System.currentTimeMillis();
                                long last = lastTyping.getOrDefault(sender, 0L);
                                if (now - last > 500) {
                                    lastTyping.put(sender, now);
                                    chatSink.tryEmitNext(payload);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse message: {}", payload, e);
                    }
                })
                .doOnError(error -> log.error("WebSocket error", error))
                .onErrorResume(e -> Mono.empty());

        return session.send(output)
                .and(input.then())
                .doOnTerminate(() -> {
                    String userToRemove = null;
                    for (Map.Entry<String, WebSocketSession> entry : userSessions.entrySet()) {
                        if (entry.getValue().equals(session)) {
                            userToRemove = entry.getKey();
                            break;
                        }
                    }
                    if (userToRemove != null) {
                        activeUsers.remove(userToRemove);
                        userSessions.remove(userToRemove);
                        broadcastUserOffline(userToRemove);
                        log.info("User {} disconnected", userToRemove);
                    }
                });
    }

    private void sendOnlineUsersToUser(String username) {
        try {
            List<String> onlineUsers = new ArrayList<>(activeUsers);
            Map<String, Object> presenceMessage = Map.of(
                "type", "PRESENCE_UPDATE",
                "onlineUsers", onlineUsers
            );
            String message = objectMapper.writeValueAsString(presenceMessage);
            chatSink.tryEmitNext(message);
        } catch (Exception e) {
            log.error("Failed to send online users to {}", username, e);
        }
    }

    private void broadcastUserOnline(String username) {
        try {
            Map<String, Object> onlineMessage = Map.of(
                "type", "USER_ONLINE",
                "username", username
            );
            String message = objectMapper.writeValueAsString(onlineMessage);
            chatSink.tryEmitNext(message);
        } catch (Exception e) {
            log.error("Failed to broadcast user online: {}", username, e);
        }
    }

    private void broadcastUserOffline(String username) {
        try {
            Map<String, Object> offlineMessage = Map.of(
                "type", "USER_OFFLINE",
                "username", username
            );
            String message = objectMapper.writeValueAsString(offlineMessage);
            chatSink.tryEmitNext(message);
        } catch (Exception e) {
            log.error("Failed to broadcast user offline: {}", username, e);
        }
    }
}
