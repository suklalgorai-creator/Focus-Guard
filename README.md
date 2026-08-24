# FocusGuard

## Canonical build source

Build only from `app/`. The `frictionguard-perfect-decompiled/` directory is an
ignored reverse-engineering reference for the legacy APK; it is never compiled
or merged as smali. The generated APK will therefore contain the root app UI
and the blocking behaviour ported into its Kotlin sources.

> *"Make bad habits so difficult, slow, and frustrating that you voluntarily give up."*

A personal Android app that uses **extreme, multi-layered, adaptive friction** to discourage opening Instagram and other distracting apps.

## Features

- **Instant Detection** — AccessibilityService detects blocked apps within milliseconds
- **6-Layer Friction Engine** — Forced delays, cognitive tasks, random waits, fake failures, escalation, and uncertainty gate
- **25% Success Rate** — Even after completing all challenges, access is only granted ~25% of the time
- **Anti-Adaptation** — Randomized tasks, durations, and failure points prevent pattern memorization
- **Anti-Bypass** — Detects Settings access attempts and applies escalation penalties
- **Self-Healing** — Foreground service with watchdog and boot receiver survives kills/reboots
- **Confrontational Messaging** — 40+ blunt, reality-check messages tied to NEET prep

## Setup

1. Open in Android Studio
2. Build and install on your device
3. Grant all 4 permissions:
   - Accessibility Service
   - Display Over Other Apps
   - Usage Data Access
   - Ignore Battery Optimization
4. The guard activates automatically

## Architecture

```
Detection (Accessibility + UsageStats)
    → Overlay (Pre-initialized, zero-delay)
        → FrictionOrchestrator
            → EscalationEngine (calculates difficulty)
            → FrictionPipeline (builds randomized layers)
                → Layer 1: Forced Delay
                → Layer 2: Cognitive Tasks (Math/Typing/Memory)
                → Layer 3: Random Wait (fake loading)
                → Layer 4: Failure Injection (fake errors)
            → UncertaintyGate (25% success probability)
        → Access Granted / Denied
```

## Built for NEET Prep

This app was built as a personal tool to combat Instagram addiction during NEET exam preparation. The psychological messages, difficulty scaling, and confrontational tone are all calibrated for this specific use case.
