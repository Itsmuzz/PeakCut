# PeakCut Android

## First native milestone

A native Android application is now scaffolded for the `android-apk` branch.

The initial vertical slice is local video trimming:
- Pick a video through Android's system document picker.
- Preview the selected source.
- Set start and end times in seconds.
- Export an MP4 clip into `Movies/PeakCut` using Android MediaExtractor and MediaMuxer.

## Build

Open this `android/` folder in Android Studio, or run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35 installed.

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

A GitHub Actions workflow builds the debug APK on pushes to this branch and uploads it as an artifact.

## Not yet implemented

This is not full PeakCut feature parity. Speech recognition/captions, AI highlight detection, live stream ingestion, creator accounts, and social publishing still need Android-native implementations and device testing. The current clipper is an initial proof-of-concept and must be validated against different codecs, rotation metadata, audio/video sync, and long files before release.
