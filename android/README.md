# PeakCut Android

This directory is the native Android application workstream for the `android-apk` branch.

## Goal

Ship an installable Android APK that runs its processing locally inside the app. Users should not need Termux or a separately hosted backend.

## Current status

This is a porting plan, not a finished app or APK. The existing project is a Python/FastAPI application and cannot be made into a reliable Android APK by simply wrapping its web UI.

## Required implementation

1. Native Android shell and app lifecycle.
2. Local media import/export using Android Storage Access Framework.
3. Android-compatible FFmpeg integration for cutting, rendering, and audio extraction.
4. Sherpa-ONNX Android API integration for on-device speech recognition.
5. Replace Python subprocess and desktop-only file assumptions with Android services.
6. Port or replace backend features and dependencies that are not Android-compatible.
7. Add foreground-service handling for long-running jobs and clear storage/model download controls.
8. Build and test on ARM64 Android devices.

## Milestones

- M1: Android project builds and installs; app opens.
- M2: Import a local video, cut a selected segment, and export it.
- M3: On-device transcription and captions.
- M4: Complete PeakCut workflows, including live-source handling where technically and legally supported.

A milestone must be tested before it is marked complete. No APK is produced by this documentation commit.
