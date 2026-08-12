# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The confirmed primary audience is a solo person exercising at home, following a structured multi-week program and using a phone before and during each session.

## Product Purpose

TwelveWeek helps someone choose a training program, know what to do next, execute each session with minimal friction, and retain a trustworthy record of completion. Success means moving from opening the app to the right workout in seconds, then interacting as little as possible while exercising.

## Positioning

An offline-first workout execution tool that combines a multi-week plan, persistent progress, focused guided sessions, timers, and optional exercise media without requiring an account.

## Operating Context

Use is split across three moments at home: deciding or resuming before training, glancing and tapping with limited attention from a mat or floor space, and checking completion afterward. Connectivity may be unavailable. During training, controls must remain legible at arm's length and usable one-handed.

## Capabilities and Constraints

Current product data includes multiple programs, weeks, workouts, sections, timed and repetition-based exercises, progress namespaced per program, guided sessions, timers, resets, synced program metadata, and optional ExerciseDB media. The implementation is a native Jetpack Compose Android app supporting Android 8.0 and newer.

The user explicitly wants both the interface and behavior reimagined. Navigation, hierarchy, onboarding, progress presentation, and workout interactions may change. Existing program content, saved progress semantics, offline operation, and the underlying capabilities above remain product truth unless a later decision changes them.

## Brand Commitments

The product name is TwelveWeek. No other visual identity, palette, typography, or interaction pattern is binding; the existing visual world is an anti-reference for this redesign.

## Evidence on Hand

- Real bundled workout programs and synced program metadata under `app/src/main/assets/programs/`.
- Existing product behavior and copy in `app/src/main/java/com/personal/twelveweek/`.
- Product capability documentation in `README.md`.
- No testimonials, outcome claims, health claims, or audience research are available and none may be fabricated.

## Product Principles

- Make the next useful action more prominent than plan administration.
- Minimize decisions and taps during a workout.
- Keep progress factual, reversible, and easy to understand.
- Make the core experience fully useful offline; connected media is enhancement.
- Preserve orientation across program, week, workout, section, and exercise.

## Accessibility & Inclusion

Use familiar Android semantics, TalkBack labels, minimum 48 dp targets, non-color status cues, strong text contrast, and layouts that remain understandable under motion reduction, larger text, fatigue, and divided attention.
