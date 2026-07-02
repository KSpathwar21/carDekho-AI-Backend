# 🚗 AI Car Buying Assistant

# CLAUDE_BACKEND.md

---

# Objective

Build the backend for an AI-native conversational car recommendation platform.

This backend should not behave like a CRUD application.

Instead, it should act as an AI Orchestrator that coordinates multiple AI agents and tools.

The frontend communicates only with this backend.

The backend communicates with the LLM and MySQL.

---

# Technology Stack

Java 21

Spring Boot 3.x

Spring Web

Spring Data JPA

Spring AI

MySQL

Lombok

Validation

Jackson

Maven

Docker

Railway Deployment Ready

---

# Project Architecture

Follow Clean Architecture.

Never place business logic inside controllers.

Package Structure

com.cardekho.ai

├── controller
├── orchestrator
├── agent
├── tool
├── validator
├── service
├── repository
├── entity
├── dto
├── mapper
├── config
├── exception
├── util

---

# Application Flow

Frontend

↓

ConversationController

↓

ConversationOrchestrator

↓

ConversationAgent

↓

PreferenceAgent

↓

Missing Information?

↓

YES

↓

Generate Next Question

↓

Frontend

↓

NO

↓

SQLAgent

↓

SQLValidator

↓

DatabaseTool

↓

RecommendationAgent

↓

Frontend

---

# Database

Use MySQL.

Create one table.

cars

Columns

id

brand

model

variant

bodyType

fuelType

transmission

price

engine

power

torque

mileage

safetyRating

bootSpace

groundClearance

seatCapacity

reviewScore

pros

cons

createdAt

updatedAt

Populate around 50 Indian cars using data.sql.

Use realistic data.

---

# Conversation Storage

Do NOT use a database.

Maintain conversations in-memory.

Conversation

conversationId

messages

preferences

createdAt

status

Each Message

role

content

timestamp

---

# Agents

## Conversation Agent

Responsibilities

Maintain conversation history.

Know previous answers.

Know conversation progress.

Generate conversation context.

Input

Conversation

User Message

Output

Updated Conversation

---

## Preference Agent

Responsibilities

Extract structured user preferences.

Required Fields

budget

fuelType

bodyType

transmission

drivingPattern

familySize

priority

Optional

brandPreference

groundClearance

bootSpace

Output

UserPreference DTO

---

## SQL Agent

Responsibilities

Generate SQL only.

Never execute SQL.

Input

UserPreference

Output

SQL Query

Example

SELECT *

FROM cars

WHERE price <= 1500000

AND fuel_type='Petrol'

ORDER BY safety_rating DESC

LIMIT 5

---

## Recommendation Agent

Input

UserPreference

Cars

Responsibilities

Generate

Summary

Pros

Trade-offs

Alternative Suggestions

Markdown response.

---

# SQL Validator

Allowed

SELECT

WHERE

ORDER BY

LIMIT

AND

OR

LIKE

Forbidden

UPDATE

DELETE

INSERT

DROP

ALTER

CREATE

TRUNCATE

UNION

Subqueries

Multiple Statements

Reject anything unsafe.

---

# Database Tool

Responsibilities

Execute validated SQL.

Use JdbcTemplate.

Never expose SQL exceptions to frontend.

Return List<Car>

---

# Orchestrator

ConversationOrchestrator coordinates everything.

Workflow

Receive user message.

↓

ConversationAgent

↓

PreferenceAgent

↓

Enough Information?

↓

NO

↓

Generate Next Question

↓

Return

↓

YES

↓

SQLAgent

↓

SQLValidator

↓

DatabaseTool

↓

RecommendationAgent

↓

Return Recommendations

The orchestrator should contain almost no business logic.

It simply coordinates agents.

---

# APIs

POST

/chat/start

Response

{
conversationId,
assistantMessage
}

---

POST

/chat/message

Request

{
conversationId,
message
}

Response

{
assistantMessage,

recommendations,

comparison,

completed
}

---

GET

/cars

Supports pagination.

---

GET

/cars/{id}

Returns complete car specification.

---

GET

/health

Returns

OK

---

# DTOs

ChatRequest

ChatResponse

ConversationResponse

RecommendationResponse

CarResponse

UserPreference

SQLResponse

ErrorResponse

---

# LLM Integration

Use Spring AI.

Create one LLM client.

Configure via application.yml.

Environment Variable

OPENAI_API_KEY

or

GROQ_API_KEY

Never hardcode secrets.

---

# Prompt Strategy

Conversation Agent

Prompt

You are an automotive consultant.

Continue the conversation.

Ask only ONE question.

Never ask duplicate questions.

---

Preference Agent

Prompt

Extract structured JSON.

Return ONLY JSON.

No explanation.

---

SQL Agent

Prompt

Generate SQL for MySQL.

Only query table

cars

Never use unsafe SQL.

Return SQL only.

---

Recommendation Agent

Prompt

You are an automotive expert.

Explain why these cars suit the customer.

Mention

Pros

Cons

Trade-offs

Use markdown.

---

# Configuration

application.yml

Database

Spring AI

Logging

CORS

Jackson

Validation

Railway compatibility

---

# Exception Handling

Create GlobalExceptionHandler.

Handle

LLM Failure

Database Failure

Invalid SQL

Conversation Not Found

Validation Errors

Internal Server Error

Return consistent ErrorResponse.

---

# Logging

Log

Conversation ID

Prompt

Generated SQL

LLM latency

Database latency

Execution time

Errors

Never log API keys.

---

# Validation

Validate every request.

Use Bean Validation.

Return proper HTTP status codes.

---

# Testing

Write unit tests for

SQLValidator

ConversationOrchestrator

RecommendationAgent

DatabaseTool

Use JUnit 5.

Mockito.

---

# Docker

Provide Dockerfile.

Provide docker-compose.

Backend should run with one command.

---

# Railway Deployment

Application should support

Environment Variables

MySQL

Health Check

Port Configuration

No code changes between local and production.

---

# Coding Standards

Use constructor injection.

Never use field injection.

Keep methods under 40 lines.

Prefer composition.

Avoid duplicate code.

Meaningful naming.

Follow SOLID.

No business logic inside controllers.

---

# Claude Code Guidelines

Implement milestone by milestone.

Never generate the entire project at once.

Wait for approval after every milestone.

Milestone 1

Project Setup

Milestone 2

Entities

Milestone 3

Database

Milestone 4

Agents

Milestone 5

Conversation APIs

Milestone 6

LLM Integration

Milestone 7

SQL Validation

Milestone 8

Recommendation Flow

Milestone 9

Testing

Milestone 10

Docker

Milestone 11

Railway Ready

Stop after each milestone and wait for further instructions.
