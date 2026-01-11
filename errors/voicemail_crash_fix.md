# Voicemail Crash Fix

## Problem
Crash in LegacyVoicemailNotifier due to invalid color primary value (0 or 0xfffffffe).

## Solution
Added validation for color primary, fallback to theme color if invalid.

## Implementation Details
- Modified LegacyVoicemailNotifier.createNotification()
- Added color check before setting notification color

## Code Changes
- Modified java/com/android/dialer/app/calllog/LegacyVoicemailNotifier.java

## Why It Works
- Prevents IllegalArgumentException by using valid color
- Fallback to default theme color

## Testing
- Verified notification creation with various themes