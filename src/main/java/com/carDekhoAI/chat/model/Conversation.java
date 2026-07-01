package com.carDekhoAI.chat.model;

import com.carDekhoAI.preference.dto.UserPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Conversation {

    @EqualsAndHashCode.Include
    private String conversationId;

    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    private ConversationStatus status;

    private LocalDateTime createdAt;

    private UserPreference preferences;

    public String toTranscript() {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(message.role()).append(": ").append(message.content()).append('\n');
        }
        return transcript.toString();
    }
}
