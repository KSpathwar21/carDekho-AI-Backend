package com.carDekhoAI.llm.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String call(String conversationId, String userMessage) {
        return call(conversationId, null, userMessage);
    }

    public String call(String conversationId, String systemPrompt, String userMessage) {
        long startTime = System.currentTimeMillis();
        log.info("LLM call started - conversationId={}, systemPrompt={}, userMessage={}",
                conversationId, systemPrompt, userMessage);
        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt();
            if (systemPrompt != null) {
                request = request.system(systemPrompt);
            }
            String content = request.user(userMessage).call().content();

            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("LLM call succeeded - conversationId={}, latencyMs={}", conversationId, latencyMs);
            return content;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("LLM call failed - conversationId={}, latencyMs={}, error={}",
                    conversationId, latencyMs, e.getMessage());
            throw new LlmException("LLM call failed for conversationId: " + conversationId, e);
        }
    }
}
