# 🚗 AI Car Buying Assistant

# ARCHITECTURE.md

---

# Vision

This project is an AI-native conversational car recommendation platform.

Instead of exposing CRUD APIs with business logic spread across services, the backend acts as an AI Orchestrator.

The Large Language Model is responsible for reasoning.

Spring Boot is responsible for orchestration, validation, persistence and tool execution.

This architecture closely resembles modern AI Agent systems.

---

# High Level Architecture

                    React Frontend
                           │
                           │ REST
                           ▼
                ConversationController
                           │
                           ▼
               Conversation Orchestrator
                           │
        ┌──────────────────┼───────────────────┐
        │                  │                   │
        ▼                  ▼                   ▼
Conversation Agent   Preference Agent     SQL Agent
        │                  │                   │
        └──────────────┬───┴───────────────────┘
                       ▼
               SQL Validation Tool
                       │
                Safe SQL ?
               /         \
            NO            YES
            │              │
            ▼              ▼
     Ask LLM Again     Database Tool
                            │
                            ▼
                         MySQL
                            │
                            ▼
                  Matching Car Records
                            │
                            ▼
                Recommendation Agent
                            │
                            ▼
                     React Frontend

---

# Why Agent Architecture?

Instead of one huge AIService,

each agent has exactly one responsibility.

Benefits

✔ Easier to maintain

✔ Easier to debug

✔ Better prompts

✔ Demonstrates AI-native design

✔ Mirrors MCP / Tool Calling concepts

---

# Agent Responsibilities

## 1 Conversation Agent

Purpose

Manage conversation.

Responsibilities

- maintain history

- know previous answers

- maintain conversation state

- send context to LLM

Input

Conversation

User Message

Output

Updated Conversation

Next Action

---

## 2 Preference Agent

Purpose

Extract structured user preferences.

Example

User

"I have around 18 lakh budget."

↓

Output

{
 budget:1800000
}

Another example

"I drive mostly in city."

↓

{
 drivingPattern:"CITY"
}

Eventually

{
 budget
 fuel
 bodyType
 priority
 transmission
 familySize
}

---

## 3 SQL Agent

Purpose

Generate SQL.

Input

Structured Preferences

Output

SELECT *
FROM cars
WHERE ...

No execution.

Only SQL generation.

---

## 4 SQL Validation Tool

Purpose

Prevent dangerous SQL.

Allowed

SELECT

WHERE

LIMIT

ORDER BY

LIKE

AND

OR

Forbidden

UPDATE

DELETE

DROP

ALTER

INSERT

UNION

TRUNCATE

Subqueries

Multiple Statements

If invalid

↓

Request regeneration from SQL Agent.

---

## 5 Database Tool

Purpose

Execute validated SQL.

Uses

JdbcTemplate

Returns

List<Car>

Nothing more.

---

## 6 Recommendation Agent

Purpose

Generate natural language explanations.

Input

User Preferences

Matching Cars

Prompt

Explain

Why recommended

Trade-offs

Alternative choices

Return Markdown.

---

# Conversation Lifecycle

User

↓

POST /chat/message

↓

Conversation Agent

↓

Preference Agent

↓

Enough Information?

NO

↓

Conversation Agent asks next question

YES

↓

SQL Agent

↓

SQL Validation Tool

↓

Database Tool

↓

Recommendation Agent

↓

Frontend

---

# Required User Information

Budget

Fuel Type

Body Type

Transmission

Driving Pattern

Family Size

Priority

Optional

Brand Preference

Ground Clearance

Boot Space

Mileage

---

# Conversation Memory

Conversation

conversationId

messages

preferences

status

Example

Conversation

|

|-- Messages

|

|-- Preferences

budget

fuel

priority

familySize

|

|-- Recommendation Generated

---

# Backend Package Structure

com.cardekho.ai

controller

orchestrator

agent

tool

entity

repository

dto

config

exception

mapper

util

validator

---

# Folder Responsibilities

controller

REST endpoints

orchestrator

Coordinates entire workflow.

agent

ConversationAgent

PreferenceAgent

SQLAgent

RecommendationAgent

tool

DatabaseTool

LLMTool

validator

SQLValidator

---

# AI Workflow

Conversation

↓

Conversation Agent

↓

Preference Agent

↓

Need More Information?

↓

YES

↓

Generate Next Question

↓

Frontend

↓

NO

↓

SQL Agent

↓

SQL Validator

↓

Database Tool

↓

Recommendation Agent

↓

Frontend

---

# API Design

POST

/chat/start

Returns

conversationId

assistantMessage

---

POST

/chat/message

Request

conversationId

message

Response

assistantMessage

recommendations

comparison

conversationCompleted

---

GET

/cars/{id}

Returns

Car Details

---

GET

/cars

Pagination

---

GET

/health

Health Check

---

# LLM Prompting Strategy

Conversation Agent

Prompt

Continue conversation.

Ask only one question.

Preference Agent

Prompt

Extract structured JSON.

SQL Agent

Prompt

Generate safe SQL only.

Recommendation Agent

Prompt

Explain recommendations.

Mention trade-offs.

Never hallucinate.

---

# Logging

Conversation Id

Prompt

LLM Response

Generated SQL

Execution Time

AI Latency

Database Latency

Errors

---

# Future Agent Extensions

Review Agent

Dealer Agent

Finance Agent

Insurance Agent

Comparison Agent

RAG Agent

Web Search Agent

Image Search Agent

Voice Agent

---

# Why This Architecture?

This project demonstrates

✔ AI Orchestration

✔ Multi-Agent Design

✔ Tool Calling

✔ LLM Integration

✔ Backend Engineering

✔ Secure SQL Execution

✔ Production-grade Separation of Concerns

instead of simply calling an LLM from a controller.