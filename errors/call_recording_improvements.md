# Call Recording Improvements

## Problem
Call recording used MIC source and was not enabled by default.

## Solution
Changed audio source to VOICE_CALL for better quality and enabled recording by default.

## Implementation Details
- Modified CallRecordingService.java to use VOICE_CALL source
- Changed isEnabled() to return true by default

## Code Changes
- Modified java/com/android/dialer/callrecording/CallRecordingService.java

## Why It Works
- VOICE_CALL source captures both sides of the call
- Default enable provides immediate functionality
- User can still disable in settings

## Testing
- Verified recording works with new source
- Confirmed default enable