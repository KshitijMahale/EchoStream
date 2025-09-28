package com.example.reactivechat.handler;

import com.example.reactivechat.controller.ChatController;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ChatController chatController;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastTyping = new ConcurrentHashMap<>();

    // A reactive sink to broadcast messages to all connected clients
    private final Sinks.Many<String> chatSink = Sinks.many().multicast().onBackpressureBuffer(5000, false);

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Broadcasted output stream (server -> client)
        Flux<WebSocketMessage> output = chatSink.asFlux()
                .map(session::textMessage);

        // Handle input (client -> server)
        Flux<String> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(payload -> {
                    log.info("Received: {}", payload);
                    try {
                        Map<String, Object> message = objectMapper.readValue(payload, Map.class);
                        String type = (String) message.get("type");
                        String sender = (String) message.get("sender");

                        if (type != null && sender != null) {
                            switch (type) {
                                case "JOIN" -> activeUsers.add(sender);
                                case "LEAVE" -> activeUsers.remove(sender);
                            }

                            if (type.equals("JOIN") ||
                                    type.equals("LEAVE") ||
                                    type.equals("STOP_TYPING")) {
                                chatSink.tryEmitNext(payload);
                            }
                            if (type.equals("CHAT")) {
                                chatController.addMessage(payload); // Store message
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

        // Send output while consuming input
        return session.send(output).and(input.then());
    }
}
