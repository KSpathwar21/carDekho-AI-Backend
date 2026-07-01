# 🚗 AI Car Buying Assistant
## PROJECT_SCOPE.md

---

# Overview

## Problem Statement

Buying a car is overwhelming because users need to compare hundreds of vehicles across multiple dimensions such as:

- Budget
- Fuel Type
- Body Type
- Safety
- Mileage
- Features
- Reviews
- Family Needs

Instead of browsing filters manually, users should simply have a conversation with an AI assistant that understands their needs and recommends suitable cars.

The objective is to build a full-stack AI-powered web application that guides users from:

> "I don't know what car to buy"

to

> "I'm confident about my shortlist."

---

# Product Vision

Create an AI-powered conversational car advisor that:

- understands user requirements naturally
- asks follow-up questions when needed
- converts user intent into structured search criteria
- retrieves matching cars from a database
- explains recommendations in simple language
- allows users to refine recommendations naturally

---

# Target Users

- First-time buyers
- Family buyers
- Users confused between multiple options
- Users who don't know technical specifications

---

# MVP Features

## 1. Conversational Chat Interface

Instead of traditional filters, users interact with an AI assistant.

Example

AI

> Hi! I'm your AI Car Buying Assistant.

> I'll ask a few questions to recommend the perfect car.

---

## 2. Guided Questionnaire

Questions are asked one at a time.

Example flow

Budget

↓

Fuel Preference

↓

Body Type

↓

Transmission

↓

Driving Pattern

↓

Family Size

↓

Priority

↓

Recommendation

---

## 3. AI Conversation

The assistant should:

- ask one question at a time
- remember previous answers
- avoid asking duplicate questions
- understand free-text answers

Example

User

"I travel mostly in the city."

Assistant

"Do you prefer Petrol, Diesel or EV?"

---

## 4. AI Recommendation Engine

Once enough information has been collected,

the backend should:

- generate SQL
- fetch matching cars
- rank them
- send results to AI
- AI explains recommendations

---

## 5. Recommendation Cards

Each recommendation contains

- Brand
- Model
- Variant
- Price
- Mileage
- Safety Rating
- Fuel Type
- Transmission
- AI Summary

---

## 6. Comparison View

Users can compare recommended cars.

Comparison includes

- Price
- Mileage
- Safety
- Engine
- Fuel
- Transmission
- Seating Capacity
- Boot Space
- Review Score

---

## 7. Car Details

Detailed page containing

Specifications

Pros

Cons

AI Summary

---

# AI Responsibilities

The LLM is responsible for

✔ Understanding user intent

✔ Asking follow-up questions

✔ Generating SQL

✔ Explaining recommendations

The LLM is NOT responsible for

✘ Executing SQL

✘ Accessing the database directly

✘ Business logic

✘ Ranking logic

---

# Backend Responsibilities

The backend should

- maintain conversation history
- call the LLM
- validate SQL
- execute SQL
- fetch cars
- send results to the LLM
- return formatted responses

---

# Frontend Responsibilities

The frontend should

- display conversation
- manage chat history
- show typing animation
- render recommendation cards
- display comparison tables
- call backend APIs

---

# Non Functional Requirements

The application should

- be responsive
- work on desktop
- work on mobile
- have fast response times
- be easy to understand
- have clean UI

---

# Tech Stack

Frontend

- React
- TypeScript
- Tailwind CSS
- Axios
- React Router

Backend

- Java 21
- Spring Boot 3
- Spring Data JPA
- Spring AI
- MySQL
- Maven

Deployment

Backend

Railway

Frontend

Vercel

---

# Features Included

✅ Conversational AI

✅ AI-guided questionnaire

✅ Text-to-SQL

✅ SQL validation

✅ MySQL

✅ Recommendation engine

✅ AI explanations

✅ Car comparison

✅ Responsive UI

✅ Railway deployment

---

# Features Excluded (Deliberately)

To keep the project within the required 2–3 hour implementation window, the following features are intentionally excluded:

- Authentication
- User accounts
- Wishlist
- Favorites
- Dealer search
- Maps integration
- Financing/EMI calculator
- Test drive booking
- Payment gateway
- Image uploads
- Voice interaction
- Notifications
- Admin dashboard
- Analytics dashboard
- Reviews submission

---

# Success Criteria

The project is considered complete when:

✅ User opens the application

↓

✅ Starts a conversation

↓

✅ AI asks intelligent questions

↓

✅ User answers naturally

↓

✅ Backend retrieves matching cars

↓

✅ AI explains recommendations

↓

✅ User views recommendation cards

↓

✅ User compares cars

↓

✅ Application is deployed successfully

---

# Stretch Goals (If Time Permits)

- Conversation memory
- Streaming AI responses
- Voice input
- Better ranking algorithm
- Save conversations
- Compare multiple cars
- Charts for comparison
- RAG with real reviews
- Vector search
- Multi-agent workflow

---

# Acceptance Criteria

The application must:

- Run locally with one command
- Be deployable
- Have working frontend
- Have working backend
- Demonstrate AI integration
- Show complete end-to-end flow
- Handle errors gracefully
- Have clean architecture

---

# Time Allocation

Planning (Completed Before Recording)

Backend

~70 minutes

Frontend

~60 minutes

Testing

~20 minutes

Deployment

~20 minutes

README

~10 minutes

Buffer

~20 minutes

Total

Approximately 3 hours

---

# End Goal

Deliver a production-style MVP demonstrating:

- Product thinking
- AI-native development
- Clean backend architecture
- Conversational UX
- Responsible LLM orchestration
- End-to-end deployment

rather than attempting to build a complete CarDekho clone.