# Fitbit VibeBridge ⚡

<p align="center">
  <img src="app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" width="96" height="96" alt="Fitbit VibeBridge Logo" />
</p>

<p align="center">
  <b>Instant, distinct wrist vibrations for incoming calls and notifications on Google Fitbit bands.</b><br>
  <i>Pure BLE • 100% Offline • Zero Accessibility Services • Google Health Coexistence • Android 15 (16 KB) Ready</i>
</p>

<p align="center">
  <a href="https://github.com/ashu-sa/FitbitVibeBridge/releases/latest">
    <img src="https://img.shields.io/badge/Download-APK%20(v1.0.0)-00E5FF?style=for-the-badge&logo=android" alt="Download APK" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android" alt="Platform Android 8.0+" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline-00E676?style=for-the-badge" alt="100% Offline" />
  <img src="https://img.shields.io/badge/Page%20Size-16%20KB%20Compatible-blueviolet?style=for-the-badge" alt="16 KB Page Size Compatible" />
</p>

---

## 📱 Screenshots

<p align="center">
  <img src="screenshots/dashboard_main.png" width="23%" alt="Main Dashboard" />
  &nbsp;&nbsp;
  <img src="screenshots/pattern_selector.png" width="23%" alt="Pattern Selection & In-Dialog Audition" />
  &nbsp;&nbsp;
  <img src="screenshots/reset_recovery.png" width="23%" alt="Reset & Recovery Coexistence" />
  &nbsp;&nbsp;
  <img src="screenshots/faq_privacy.png" width="23%" alt="Architecture & Privacy FAQs" />
</p>

---

## 🚀 Key Features

* **⚡ Distinct Incoming Call Haptics**: Choose between **Urgent** *(intermittent high-frequency pulses)* and **Continuous** *(steady buzz)* so you never mistake a phone call for a simple message.
* **🎯 Priority App Filtering**: Choose up to 5 priority apps (WhatsApp, Telegram, Messages, Gmail, Instagram, etc.) and assign custom vibration patterns to each (e.g., *Double Pulse* for WhatsApp direct messages, *Single Pulse* for regular updates).
* **🎧 In-Dialog Vibration Audition**: When selecting a pattern, test and pause the vibration live on your wrist with dedicated **`[ ▶ Play ]`** / **`[ ⏸ Pause ]`** preview buttons.
* **🤝 Google Health / Fitbit Coexistence**: Automatically releases Bluetooth in 1–2 seconds after pulsing, allowing Google Health to sync seamlessly in the background without connection collisions.
* **🛡️ 100% Banking & UPI Safe**: Requires **NO Accessibility Services**, **NO screen capture**, and **NO overlay draw** permissions. Banking apps (Google Pay, PhonePe, Paytm, banking apps) detect zero risk and run completely uninterrupted.
* **🔋 Ultra Battery Optimized**: Event-driven architecture with zero background polling. Uses `< 1%` phone battery per day and minimal BLE micro-joules on the band.
* **🔄 Self-Healing Connection Recovery**: Built-in instant **`[ ↺ Reset ]`** and auto-detect scanner that flushes stale Android GATT caches, clears broken DTLS handshakes, and re-links in seconds if Bluetooth is dropped or out of range.
* **📦 16 KB Page-Size Native Compatibility**: Fully aligned and compatible with Android 15's native 16 KB memory page architecture.

---

## 🛠️ How It Works (Architecture)

```
[ Incoming Call / Notification ]
              │
              ▼
[ NotificationListenerService / TelephonyCallback ]
              │  (Wakes phone via Partial WakeLock in < 10ms)
              ▼
[ AirBridgeOneShotService (Foreground Service) ]
              │
              ├── 1. Acquires Low-Latency BLE Connection
              ├── 2. Establishes Gattlink Layer & DTLS 1.2 Handshake
              ├── 3. Sends CoAP Haptic Request: coap://10.127.0.1/app/haptics/pulse
              │      (Pattern: Single, Double, Triple, Long Gap, or Urgent)
              │
              ▼
[ Fitbit Band Vibrates on Wrist ]
              │
              ▼
[ Immediate Disconnect & Resource Release (< 1.5s) ]
              │
              ▼
[ Google Health Sync Remains Active & Free ]
```

---

## 📥 Download & Installation

### Option 1: Direct APK Download
1. Download the latest **`app-debug.apk`** from [GitHub Releases](https://github.com/ashu-sa/FitbitVibeBridge/releases/latest).
2. On your Android phone, tap the downloaded file and select **Install** *(allow "Install from Unknown Sources" if prompted)*.
3. Open **Fitbit VibeBridge** and grant the 3 essential permissions:
   - **Bluetooth**: To connect to your Fitbit band.
   - **Phone**: To detect incoming call ringing states.
   - **Notification Listener**: To detect alerts from your selected priority apps.
4. Tap **Auto-Detect Band** or select your Fitbit band from the list.

### Option 2: Build from Source
Ensure you have the Android SDK (API 34) and JDK 17 installed:

```bash
git clone https://github.com/ashu-sa/FitbitVibeBridge.git
cd FitbitVibeBridge
./gradlew assembleDebug

# Install directly to your connected Android phone via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎛️ Vibration Pattern Specs

| Pattern Name | Wire Protocol Code | Haptic Description | Recommended Use Case |
| :--- | :---: | :--- | :--- |
| **Urgent Alert** | `URGENT` | 4 rapid, high-intensity pulses | Incoming phone calls, critical alerts |
| **Continuous Ring** | `URGENT` (pulsed loop) | Persistent repeating pulse buzz | Phone call ringing until answered/rejected |
| **Double Pulse** | `DOUBLE` | 2 crisp, distinct pulses | Direct messages (WhatsApp, Telegram) |
| **Single Pulse** | `SINGLE` | 1 subtle, clean pulse | Casual notifications, news, system alerts |
| **Triple Pulse** | `TRIPLE` | 3 rhythmic pulses | Group chats, calendar events |
| **Long Gap** | `LONG_GAP` | 2 widely-spaced pulses | Reminders, to-dos, timers |

---

## ❓ Frequently Asked Questions

### Does it vibrate when my phone is locked?
**Yes, 100%.** AirBridge runs with Android background partial wake-locks. When a call or notification arrives while your phone is locked or in your pocket, AirBridge connects directly via BLE and vibrates your Fitbit instantly without waking or unlocking your screen.

### Why does Google Health say "Synced X min ago"?
This is Google Health's normal battery-saver schedule. It syncs in 15–30 minute background intervals. AirBridge releases the Bluetooth connection immediately after pulsing, ensuring Google Health sync is never blocked.

### Does this app affect UPI or Banking apps?
**Not at all.** Unlike older notification tools, AirBridge requires **NO Accessibility Service**, **NO overlay permissions**, and **NO screen capture**. Android banking and financial apps detect zero security risks.

### What if connection fails or Bluetooth is interrupted?
If Bluetooth drops or your band was out of range, tap **`[ ↺ Reset ]`** or **`[ 🔍 Auto-Find Band ]`** at the top of the dashboard. AirBridge flushes stuck Android GATT caches, clears stale DTLS handshakes, and re-links automatically.

---

## 🔒 Security & Privacy

* **Zero Internet Permissions**: The app does not request `android.permission.INTERNET`. All data processing is strictly on-device.
* **No Cloud / No Analytics**: No tracking, telemetry, or remote telemetry servers.
* **Open Source**: Complete transparency under the Apache 2.0 License.

---

## 📄 License

```
Copyright 2026 Ashutosh Sahoo

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
