package com.example.reactivechat.handler;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // A reactive sink to broadcast messages to all connected clients
    private final Sinks.Many<String> chatSink = Sinks.many().multicast().onBackpressureBuffer();

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

                        // Only allow valid message types to be broadcast
                        if (type != null && (
                                type.equals("CHAT") ||
                                        type.equals("JOIN") ||
                                        type.equals("LEAVE") ||
                                        type.equals("TYPING") ||
                                        type.equals("STOP_TYPING"))
                        ) {
                            chatSink.tryEmitNext(payload);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse message: {}", payload, e);
                    }
                });

        // Send output while consuming input
        return session.send(output).and(input.then());
    }
}
