package com.carDekhoAI.llm.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmClientTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        llmClient = new LlmClient(chatClient);
    }

    @Test
    void callWithoutSystemPromptReturnsContentAndSkipsSystem() {
        when(callResponseSpec.content()).thenReturn("pong");

        String response = llmClient.call("conv-1", "ping");

        assertThat(response).isEqualTo("pong");
        verify(requestSpec, never()).system(anyString());
        verify(requestSpec).user("ping");
    }

    @Test
    void callWithSystemPromptInvokesSystemAndReturnsContent() {
        when(callResponseSpec.content()).thenReturn("pong");

        String response = llmClient.call("conv-1", "you are a helpful assistant", "ping");

        assertThat(response).isEqualTo("pong");
        verify(requestSpec).system("you are a helpful assistant");
        verify(requestSpec).user("ping");
    }

    @Test
    void callWrapsFailureInLlmException() {
        when(callResponseSpec.content()).thenThrow(new RuntimeException("upstream failure"));

        assertThatThrownBy(() -> llmClient.call("conv-1", "ping"))
                .isInstanceOf(LlmException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("conv-1");
    }
}
