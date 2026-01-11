# Theme Crash Fix

## Problem
Crash when using dark theme with white text due to uninitialized color values (IllegalArgumentException in getTextColorPrimary).

## Solution
Added validation to prevent using fully white (0xfffffffe) as an uninitialized color.

## Implementation Details
- Modified AospThemeImpl.java to check for both Color.WHITE and 0xfffffffe
- Prevents crash in call log rendering

## Code Changes
- Modified java/com/android/dialer/theme/base/impl/AospThemeImpl.java

## Why It Works
- Adds proper color validation before using theme colors
- Fixes crash without changing theme logic
- Safe and minimal change

## Testing
- Verified no crash with dark theme and white text
- Confirmed call log renders correctly