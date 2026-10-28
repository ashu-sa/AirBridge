# Install AirBridge Calls or Full

Choose **Calls** for regular SIM calls without notification access, or **Full** for SIM calls plus Priority Apps and app-call alerts. Both include schedules and vibration controls. Full uses notification events and metadata, without extracting message text. Calls has no notification-listener component.

- [Calls APK](https://github.com/ashu-sa/AirBridge/releases/download/v1.3.4/AirBridge-Calls-1.3.4.apk)
- [Full APK](https://github.com/ashu-sa/AirBridge/releases/download/v1.3.4/AirBridge-Full-1.3.4.apk)

Calls has a separate package ID. It can coexist with Full, but enable alerts in only one edition at a time for the same band. Full can update the private-key-signed 1.2.2 release without losing settings. Calls starts with its own settings.

## Option 1: Install the APK on your phone

Download your chosen edition above, open it and follow Android's installation prompts. Allow installation from that source only if Android offers the normal permission prompt.

If Android offers **Install without scanning** or **Install anyway**, that is an optional installer choice for the official APK; wording and availability vary. If it offers only **Scan app** and **Don’t install**, there is no skip button on that screen—scan it or review the optional controls below. [Google describes the scan prompt here](https://developers.google.com/android/play-protect/warning-dev-guidance#app_scan_recommended).

If Play Protect shows “App blocked to protect your device,” allowing unknown sources alone does not resolve that separate block. Calls is the alternative without notification access. If you still choose Full, the optional steps below explain temporarily pausing scanning and restoring it immediately. USB installation is another option.

## Option 2: Optional temporary Play Protect pause for Full

Google recommends keeping Play Protect enabled. Pausing it reduces protection and does not establish that any APK is harmless. AirBridge has not been approved by Google. Download the official Full APK first, and use these steps only if you choose to accept that risk.

1. Open **Google Play Store → profile icon → Play Protect → Settings**.
2. Turn **Scan apps with Play Protect** off and review the phone's confirmation. Some versions offer **Pause** instead. If the setting is unavailable or controlled by an administrator, do not try to circumvent that restriction.
3. Install the official **AirBridge Full** APK you downloaded from this repository.
4. Immediately return to the same screen, turn **Scan apps with Play Protect** back **on**, and run a Play Protect scan—even if installation failed.

Menu names vary. This is not a guaranteed installation fix: Android may still block, flag or remove the app after scanning resumes. Do not ignore a new warning on the assumption that offline software cannot cause harm. Choose **Calls** if you only need SIM-call alerts and do not want notification access.

These are [Google's documented on/off controls](https://support.google.com/googleplay/answer/2812853); no security controls are changed by AirBridge itself.

## Option 3: Install over USB from your own computer

This uses Google's official Android Debug Bridge (ADB), an Android developer installation tool. It is an alternative installation method, not a guarantee of acceptance by Play Protect or your phone's security policy. It does not remove notification features or change AirBridge's permissions.

1. On your computer, download the APK above and [Google's Platform Tools](https://developer.android.com/tools/releases/platform-tools) for Windows, macOS or Linux. Extract Platform Tools and put the APK in its `platform-tools` folder.
2. On the phone, enable Developer options (usually tap **Build number** seven times under **About phone / Software information**), then enable **USB debugging**. Connect by USB and approve the prompt for your own computer.
3. Open a terminal in the extracted `platform-tools` folder and run:

The commands below install Full. Replace `AirBridge-Full-1.3.4.apk` with `AirBridge-Calls-1.3.4.apk` for Calls.

**Windows PowerShell**

```powershell
.\adb.exe devices
.\adb.exe install -r .\AirBridge-Full-1.3.4.apk
```

**macOS / Linux**

```sh
./adb devices
./adb install -r ./AirBridge-Full-1.3.4.apk
```

The device should appear as `device`, and installation should report `Success`. If multiple devices appear, use `adb -s DEVICE_SERIAL install -r AirBridge-Full-1.3.4.apk` with the appropriate executable prefix above.

If installation reports a certificate mismatch, a previous debug build may be installed. Replacing that build requires uninstalling it first, which deletes its settings. Releases signed with the 1.2.2 private release key can update one another normally. Do not uninstall if you are unsure which app is installed.

If Android still blocks installation, stop and keep the exact error for a compatibility report. Do not disable the verifier, forge the installer identity or assume this guide guarantees installation. USB installation of the private-key release has not yet been verified on the phone that showed the block.

After installing, turn off USB debugging and revoke USB debugging authorizations if you no longer need them. Windows may require your phone manufacturer's USB driver; Linux may require device access setup. See [Android's device setup guide](https://developer.android.com/studio/run/device).

## Enable alerts after installation

Open either edition, choose your band and grant Bluetooth and phone-state access. Calls is now ready for SIM-call setup and does not need notification access. It may ask to show its own status notifications; that permission does not grant access to other apps’ notifications. For Full, in Android Settings, enable **Notification access** for AirBridge, then turn on **Priority Apps** and/or **App calls** and select the apps you want. Test delivery before relying on it.

Some Android versions place a separate restriction on notification access for sideloaded apps. If Android offers **App info → menu → Allow restricted settings**, review that prompt before proceeding. This setting concerns post-install notification access; it does not clear a Play Protect installation block. If access is unavailable or a device administrator prohibits it, notification features cannot run.

## Verify the download

The release includes [SHA256SUMS.txt](https://github.com/ashu-sa/AirBridge/releases/download/v1.3.4/SHA256SUMS.txt) covering both APKs. Compare the checksum for the edition you download.

A matching checksum confirms the downloaded file matches this published build; it is not a security certification.

References: [official ADB installation instructions](https://developer.android.com/tools/adb), [Play Protect warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance), [Android restricted settings](https://support.google.com/android/answer/12623953).
