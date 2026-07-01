package com.carDekhoAI.chat.model;

import java.time.LocalDateTime;

public record Message(MessageRole role, String content, LocalDateTime timestamp) {
}
