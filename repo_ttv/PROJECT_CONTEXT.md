# Project Name

Yashora AI Video Generator

---

# Project Overview

Yashora AI Video Generator is a native Android application built using Kotlin and Jetpack Compose.

The application generates complete AI-powered videos from a user topic or script.

This project is already production-ready and actively maintained.

The existing architecture is considered the source of truth.

All future development must extend the current codebase without rebuilding, replacing, or redesigning the application.

Always continue from the existing implementation.

---

# Technology Stack

- Kotlin
- Jetpack Compose
- MVVM Architecture
- Repository Pattern
- StateFlow
- Kotlin Coroutines
- Material 3
- Dependency Injection
- AndroidX
- Retrofit
- OkHttp
- ExoPlayer
- MediaCodec
- MediaMuxer
- Canvas Rendering
- FFmpeg (where applicable)

---

# Existing Features

- AI Script Generation
- AI Image Generation
- AI Voice Generation
- Subtitle Generation
- Timeline Editor
- Motion Effects
- Zoom & Pan Animation
- Video Clip Support
- Audio Support
- Preview Renderer
- MP4 Export
- Local Rendering Pipeline
- Project Saving
- Material 3 UI
- Settings
- AI Provider Management

---

# Architecture Rules

The current application architecture is final.

Never rebuild the project.

Never redesign the architecture.

Never replace working implementations.

Never rename packages unless absolutely necessary.

Never introduce duplicate implementations.

Always reuse the existing architecture.

Reuse existing:

- Dependency Injection
- ViewModels
- Repository Layer
- Navigation
- Theme
- Retrofit
- OkHttp
- Network Layer
- API Clients
- Preferences
- Settings
- Logging
- Error Handling
- Utility Classes
- Common Components
- Existing AI Infrastructure
- Rendering Pipeline
- Export Pipeline

Avoid duplicate code.

Maintain the existing package structure.

Follow the current coding style.

---

# Current Development Strategy

The Text-To-Video application is the source of truth.

External repositories are feature repositories only.

Business logic may be migrated from external repositories into this application.

Architecture must always come from the main application.

Never copy an external repository completely.

Only migrate the minimum business logic required.

---

# Current Integrations

Current feature integrations include:

• AI Script Generator

• VoxEleven Voice Provider

Future integrations may include additional repositories.

The integration process should always follow the same architecture.

---

# Script Generator

The application includes a built-in AI Script Generator.

The Script Generator is integrated from an external repository.

Only the following components may be migrated:

- Topic Input
- Prompt Builder
- AI Request Builder
- AI Response Parser
- Script Generation
- Repository
- ViewModel
- Models
- Utilities required only for Script Generation

Everything else must reuse the existing application implementation.

---

# VoxEleven

VoxEleven is the native ElevenLabs integration.

Users supply their own ElevenLabs API Keys.

The application never owns API Keys.

The application never distributes API Keys.

API Keys belong entirely to users.

---

# Security Rules

API Keys must:

- remain only on the local device
- never be uploaded
- never be logged
- never be included inside analytics
- never be included inside crash reports
- never be stored in plain text
- always use encrypted local storage
- be removable by users
- be masked inside the UI

Authorization headers must never appear in logs.

---

# VoxEleven Features

- API Key Validation
- Save & Connect
- Fetch Voices
- Refresh Voices
- Voice Cache
- Voice Search
- Voice Selection
- Favorite Voices
- Voice Preview
- Voice Settings
- Speech Generation
- Retry Requests
- Audio Preview
- Local Audio Storage

Generated audio must integrate directly into the existing rendering pipeline.

No duplicate rendering pipeline may be created.

---

# Expected User Flow

Topic

↓

Generate AI Script

↓

Edit Script

↓

Generate Voice

↓

Generate Images / Videos

↓

Timeline Editor

↓

Preview

↓

Export MP4

All generated assets must remain compatible with the existing rendering system.

---

# Development Process

Before implementation:

1. Read PROJECT_CONTEXT.md completely.

2. Analyze the entire project.

3. Analyze every external repository.

4. Detect duplicate implementations.

5. Detect reusable components.

6. Produce an Integration Plan.

7. Wait for approval.

Never implement before analysis.

---

# Code Quality

Production-quality code only.

Maintain backward compatibility.

Preserve current performance.

Minimize new dependencies.

Reuse existing Gradle configuration.

Reuse existing Manifest configuration.

Maintain modular architecture.

Keep code clean.

Keep code maintainable.

---

# Deliverables

Every implementation must provide:

- Integration Plan
- Files Added
- Files Modified
- Files Removed
- Dependency Changes
- Gradle Changes
- Manifest Changes
- Architecture Notes
- Code-Level Explanation
- Patch / Diff
- Verification Steps
- Testing Checklist
- Performance Impact

---

# AI Instructions

Always read PROJECT_CONTEXT.md before making any code changes.

Never rebuild the application.

Never redesign the architecture.

Never duplicate implementations.

Always continue from the current codebase.

Always reuse the existing architecture whenever possible.

Only migrate business logic from external repositories.

Never replace existing functionality.

---

# Repositories

Main Application

https://github.com/titanboysurya-sys/Text-To-Video

Voice Provider

https://github.com/titanboysurya-sys/voxeleven