# Install AirBridge with notification alerts

The full APK includes Priority Apps and app-call alerts. Both need Android notification access; AirBridge uses notification events and metadata, without extracting message text. No reduced-feature edition is required for the options below.

## Option 1: Install the APK on your phone

[Download AirBridge 1.2.2](https://github.com/ashu-sa/AirBridge/releases/download/v1.2.2/AirBridge-1.2.2.apk), open it and follow Android's installation prompts. Allow installation from that source only if Android offers the normal permission prompt.

If Play Protect shows “App blocked to protect your device,” allowing unknown sources alone does not resolve that separate block. You can consider the optional computer-assisted route below. Keep Play Protect enabled.

## Option 2: Install over USB from your own computer

This uses Google's official Android Debug Bridge (ADB), an Android developer installation tool. It is an alternative installation method, not a guarantee of acceptance by Play Protect or your phone's security policy. It does not remove notification features or change AirBridge's permissions.

1. On your computer, download the APK above and [Google's Platform Tools](https://developer.android.com/tools/releases/platform-tools) for Windows, macOS or Linux. Extract Platform Tools and put the APK in its `platform-tools` folder.
2. On the phone, enable Developer options (usually tap **Build number** seven times under **About phone / Software information**), then enable **USB debugging**. Connect by USB and approve the prompt for your own computer.
3. Open a terminal in the extracted `platform-tools` folder and run:

**Windows PowerShell**

```powershell
.\adb.exe devices
.\adb.exe install -r .\AirBridge-1.2.2.apk
```

**macOS / Linux**

```sh
./adb devices
./adb install -r ./AirBridge-1.2.2.apk
```

The device should appear as `device`, and installation should report `Success`. If multiple devices appear, use `adb -s DEVICE_SERIAL install -r AirBridge-1.2.2.apk` with the appropriate executable prefix above.

If installation reports a certificate mismatch, a previous debug build may be installed. Replacing that build requires uninstalling it first, which deletes its settings. Releases signed with the 1.2.2 private release key can update one another normally. Do not uninstall if you are unsure which app is installed.

If Android still blocks installation, stop and keep the exact error for a compatibility report. Do not disable the verifier, forge the installer identity or assume this guide guarantees installation. USB installation of the private-key release has not yet been verified on the phone that showed the block.

After installing, turn off USB debugging and revoke USB debugging authorizations if you no longer need them. Windows may require your phone manufacturer's USB driver; Linux may require device access setup. See [Android's device setup guide](https://developer.android.com/studio/run/device).

## Enable alerts after installation

Open AirBridge, choose your band and grant Bluetooth access. In Android Settings, enable **Notification access** for AirBridge, then turn on **Priority Apps** and/or **App calls** and select the apps you want. Test delivery before relying on it.

Some Android versions place a separate restriction on notification access for sideloaded apps. If Android offers **App info → menu → Allow restricted settings**, review that prompt before proceeding. This setting concerns post-install notification access; it does not clear a Play Protect installation block. If access is unavailable or a device administrator prohibits it, notification features cannot run.

## Verify the download

The release includes [SHA256SUMS.txt](https://github.com/ashu-sa/AirBridge/releases/download/v1.2.2/SHA256SUMS.txt). The APK's SHA-256 is:

```text
87b5ef2cca751bdebc63832faa76ee61a1daac5ad9fa783e50154667c26ca9cd
```

A matching checksum confirms the downloaded file matches this published build; it is not a security certification.

References: [official ADB installation instructions](https://developer.android.com/tools/adb), [Play Protect warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance), [Android restricted settings](https://support.google.com/android/answer/12623953).
