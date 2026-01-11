# Caller Name Announcement Enhancement

## Problem
Incoming calls lacked audible caller identification, reducing accessibility for users with visual impairments or multitasking scenarios.

## Solution
Added TextToSpeech (TTS) integration to announce the caller's name when a call enters the INCOMING state.

## Implementation Details
- Added TextToSpeech import and field in Call.java
- Initialized TTS in constructor with default language
- Triggered announcement in setState() for INCOMING calls
- Used existing getCallerDisplayName() method for name retrieval

## Code Changes
- Modified java/com/android/incallui/call/Call.java
- Added TTS initialization and state check
- No new permissions or dependencies required

## Why It Works
- Leverages Android's built-in TTS API
- Respects user privacy by only announcing on incoming calls
- Follows AOSP conventions for call state handling
- Safe and non-breaking, as TTS is optional and gracefully handles failures

## Testing
- Verified TTS speaks on incoming calls
- Confirmed no impact on call flow
- Tested with various contact names and numbers