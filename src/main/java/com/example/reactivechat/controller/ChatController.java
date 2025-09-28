package com.example.reactivechat.controller;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/messages")
public class ChatController {

    private final List<String> messageHistory = new CopyOnWriteArrayList<>();

    public void addMessage(String message) {
        messageHistory.add(message);
    }

    @GetMapping
    public List<String> getMessages(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int fromIndex = Math.max(messageHistory.size() - (page + 1) * size, 0);
        int toIndex = Math.min(fromIndex + size, messageHistory.size());
        return messageHistory.subList(fromIndex, toIndex);
    }
}
