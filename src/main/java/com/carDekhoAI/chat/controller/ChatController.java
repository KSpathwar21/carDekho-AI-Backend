package com.carDekhoAI.chat.controller;

import com.carDekhoAI.chat.dto.ChatRequest;
import com.carDekhoAI.chat.dto.ChatResponse;
import com.carDekhoAI.chat.dto.ConversationResponse;
import com.carDekhoAI.chat.orchestrator.ConversationNotFoundException;
import com.carDekhoAI.chat.orchestrator.ConversationOrchestrator;
import com.carDekhoAI.chat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ConversationService conversationService;
    private final ConversationOrchestrator conversationOrchestrator;

    public ChatController(ConversationService conversationService,
                           ConversationOrchestrator conversationOrchestrator) {
        this.conversationService = conversationService;
        this.conversationOrchestrator = conversationOrchestrator;
    }

    @PostMapping("/start")
    public ConversationResponse startConversation() {
        return conversationService.startConversation();
    }

    @PostMapping("/message")
    public ChatResponse sendMessage(@Valid @RequestBody ChatRequest request) {
        return conversationOrchestrator.handleMessage(request.conversationId(), request.message());
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleConversationNotFound(ConversationNotFoundException ex) {
        return ex.getMessage();
    }
}
