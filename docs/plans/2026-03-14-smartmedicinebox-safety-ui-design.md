# SmartMedicineBox Safety and UI Design

**Date:** 2026-03-14
**Scope:** Firmware + Android APP
**Worktrees:**
- Root firmware/doc worktree: `C:\Users\xiangyu\SmartMedicineBox\.worktrees\safety-features-root`
- APP worktree: `C:\Users\xiangyu\.config\superpowers\worktrees\SmartMedicineBoxApp\safety-features-app`

## Goals

- Fix the APP white-screen/freeze path triggered by manual refresh after MQTT connection.
- Remove pressure/altitude from the board-to-APP protocol, APP models, storage, UI, and READMEs.
- Add three new end-user capabilities:
  - abnormal lid-open behavior detection
  - filtered/confirmed drop detection
  - graded environment risk (`normal`, `warning`, `critical`)
- Refresh the APP visual style to feel like a home medicine box product, centered on light blue and white rather than green.

## Current Constraints

- The firmware and APP live in separate git repositories and must be changed in separate isolated worktrees.
- The APP currently conflates broker connectivity and device online status into a single `isOnline` concept.
- The current firmware drop path still uses free-fall plus impact confirmation, while the user-facing documentation says `>6G for >20ms`.
- In the synced firmware worktree, the KEY2 cancel path is miswired in software: the debounce logic reads `KEY3` instead of `KEY2(PB15)` in `MDK-ARM/code/app_tasks.c`.
- The APP still carries `pressure` in data models and docs even though the UI card has already been simplified.

## Design Decisions

### 1. Protocol Simplification

- Remove `pressure` and `altitude` from `environment` in board JSON.
- `environment` will contain only:
  - `temperature`
  - `humidity`
- Keep `environment_limits` because the APP uses rated values and abnormal ranges.

### 2. New `behavior` and `risk` Objects

The firmware `sensors` JSON will be extended with two new objects instead of overloading the existing `state` string.

`behavior`:

```json
{
  "lid_state": "closed",
  "open_duration_ms": 0,
  "open_abnormal": 0,
  "open_reason": "normal"
}
```

`risk`:

```json
{
  "env_level": "normal",
  "drop_state": "none"
}
```

Rationale:
- `state` remains the coarse compatibility field (`closed/opened/moving/tilted`).
- `behavior` carries lid-operation semantics.
- `risk` carries UI-facing severity state.

### 3. Lid Behavior State Machine

Firmware adds a lightweight lid behavior state machine:

- `closed`
- `opening`
- `opened`
- `closing`

Recommended thresholds:
- enter `opening`:
  - `abs(pitch) > 15` or `abs(roll) > 15`, sustained `300ms`
  - or `accel_z < 0.88g`, sustained `300ms`
- `opening -> opened`:
  - sustained for `800ms`
- `opened -> closing`:
  - `abs(pitch) < 12`, `abs(roll) < 12`, `accel_z > 0.92g`, sustained `300ms`
- `closing -> closed`:
  - sustained for `800ms`

Abnormal lid-open conditions:
- `vibration > 0.9g` during lid-open flow
- or tilt excursion over `65deg`
- or lid remains open longer than `30s`

Notification policy:
- normal open/close: record only
- abnormal open / open timeout: notification + alert event
- lid close after an abnormal/open-timeout cycle: recovery-style alert event

### 4. Drop Detection Filtering

The drop path is upgraded from "single condition causes alarm" to "suspected then confirmed".

States:
- `none`
- `suspected`
- `confirmed`

Detection:
- `suspected`:
  - acceleration magnitude `>= 6G`
  - sustained for `20ms`
- `confirmed`:
  - once suspected, confirm only if one of the following follows within the confirmation window:
    - orientation change `> 45deg`
    - post-impact vibration remains elevated
    - state enters `moving` or `tilted` and persists

User-facing behavior:
- only `confirmed` causes:
  - `drop_detected` alert event
  - 5s on / 3s off / 3 rounds alarm
  - KEY2 cancellation path
- `suspected` stays internal to firmware and can be reflected in `sensors.risk.drop_state` for APP status only

Implementation note:
- The current 100ms sensor task cannot prove `20ms` sustain.
- This will be implemented via a small faster drop sampler in firmware, using a short moving window derived from MPU6050 reads so the `20ms` requirement becomes measurable.

### 5. Environment Risk Grading

Environment risk becomes three-level:

- `normal`
- `warning`
- `critical`

Rules:
- `warning`:
  - over rated range `±30%`, sustained `2s`
- `critical`:
  - over rated range `±50%` immediately
  - or `warning` sustained `30s`
- recovery:
  - return within `±25%`, sustained `10s`

Result:
- APP no longer treats all out-of-range values as equal-severity failures.

### 6. APP State Model Cleanup

The APP will separate:

- `mqttConnected`
- `deviceOnline`
- `isRefreshing`

Behavior:
- manual refresh allowed only when MQTT is connected and `deviceId` is valid
- refresh sets `isRefreshing=true`
- refresh finishes on:
  - command response
  - or new `sensors` frame
  - or timeout with visible error message
- `deviceOnline` is not set to true merely because broker connection succeeded

This prevents the current confusing state where the APP appears healthy before any device data arrives.

### 7. APP White-Screen Defensive Fix

The refresh white-screen bug will be addressed by:

- guarding refresh publish calls when MQTT is disconnected
- handling malformed/partial MQTT payloads with `runCatching` around parse/update entry points
- ensuring UI state updates cannot dereference missing nested fields during a refresh cycle
- making refresh timeout/failure visible as a user error instead of leaving the UI in a dead state

### 8. APP Visual Refresh

Visual direction: home medicine box product.

Theme:
- primary palette: light blue, mist blue, white
- reduce green usage to near zero outside dedicated success badges
- warning/critical colors remain distinct

UI work:
- replace most emoji-driven emphasis with vector/material icons
- clean up card hierarchy and spacing
- improve top app bar, state badges, alert cards
- update launcher icon to a simple rounded medicine-box mark in blue/white

### 9. Documentation Scope

Update:
- root `README.md`
- root `APP_INTERFACE.md`
- APP `README.md`

All docs must match:
- no pressure field
- new `behavior` and `risk` protocol sections
- confirmed drop detection rules
- lid abnormal events
- new APP status behavior and refreshed UI references

## Data Examples

### `sensors`

```json
{
  "timestamp": 1234567890,
  "device_id": "box001",
  "state": "opened",
  "environment": {
    "temperature": 26.4,
    "humidity": 68.5
  },
  "environment_limits": {
    "temperature_rated": 15.0,
    "temperature_low": 10.5,
    "temperature_high": 19.5,
    "humidity_rated": 50.0,
    "humidity_low": 35.0,
    "humidity_high": 65.0
  },
  "motion": {
    "accel_x": 0.120,
    "accel_y": -0.080,
    "accel_z": 0.810,
    "gyro_x": 0.20,
    "gyro_y": -0.10,
    "gyro_z": 0.05,
    "pitch": 21.0,
    "roll": 4.0,
    "vibration": 0.220
  },
  "behavior": {
    "lid_state": "opened",
    "open_duration_ms": 1800,
    "open_abnormal": 0,
    "open_reason": "normal"
  },
  "risk": {
    "env_level": "warning",
    "drop_state": "none"
  },
  "alerts": {
    "env_abnormal": 1,
    "temperature_abnormal": 1,
    "humidity_abnormal": 1
  },
  "valid": 1
}
```

### `alert`

`env_abnormal`

```json
{
  "event": "env_abnormal",
  "timestamp": 1234567890,
  "level": "critical",
  "temperature": 34.8,
  "humidity": 81.0,
  "rated_temperature": 15.0,
  "rated_humidity": 50.0,
  "temperature_abnormal": 1,
  "humidity_abnormal": 1
}
```

`lid_open_timeout`

```json
{
  "event": "lid_open_timeout",
  "timestamp": 1234568000,
  "open_duration_ms": 30000,
  "source": "behavior"
}
```

`drop_detected`

```json
{
  "event": "drop_detected",
  "timestamp": 1234568100,
  "accel_magnitude": 6.8,
  "threshold_g": 6.0,
  "duration_ms": 20,
  "confirmation": "posture_change"
}
```

## Testing Strategy

Firmware:
- add pure helper logic where possible so host-side unit tests can cover:
  - environment level grading
  - lid behavior transitions
  - confirmed-vs-suspected drop filtering
- verify build through existing Keil/UV project path if available

APP:
- add unit tests for:
  - new model parsing
  - refresh state transitions
  - environment risk mapping
  - alert event parsing
- run `:app:testDebugUnitTest`
- run `:app:assembleDebug`

## Out of Scope

- medication schedule / dose-time awareness
- cloud-side rule engine changes
- large navigation redesign
- backend persistence beyond current local APP needs
