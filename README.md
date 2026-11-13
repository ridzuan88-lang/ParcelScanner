# Parcel Scanner - Kaicom K7

Android 7.0 (API 24) app, native Java, for the Kaicom K7 built-in scanner.
Scans a tracking number, saves it on the phone (SQLite), uploads it in the background to Google Sheets
and shows **SUCCESS / ERROR** with the tracking number and timestamp.

* **RPU** mode -> Google Sheet tab `RPU`
* **RTS** mode -> Google Sheet tab `RTS`
* Each tab has exactly 3 columns: `A Date` | `B DateTime` | `C Tracking Number`

```
| Date       | DateTime            | Tracking Number |
| 13/11/2026 | 13/11/2026 15:30:25 | NV123456789MY   |
```

---------------------------------------------------------------------

## 1. Google Sheet + Apps Script (do this first)

1. Create a new Google Sheet. Name it anything.
2. **Extensions -> Apps Script**. Delete the default code and paste all of `apps-script/Code.gs`.
3. Change `SECRET_KEY` to your own secret text (example: `Mamu-2026-xyz`). Remember it.
4. Project Settings (gear icon) -> tick **Show "appsscript.json" manifest file** -> open `appsscript.json` and paste
   the contents of `apps-script/appsscript.json` (this sets the time zone to Asia/Kuala_Lumpur).
5. Pick function **setup** at the top and press **Run**. Allow the permissions when asked.
   This creates the `RPU` and `RTS` tabs with the 3 header columns and sets the sheet time zone to Malaysia.
6. **Deploy -> New deployment -> type: Web app**
   * Execute as: **Me**
   * Who has access: **Anyone**
   * Deploy, then copy the **Web app URL** (ends with `/exec`).

> Whenever you edit `Code.gs` later: **Deploy -> Manage deployments -> pencil -> Version: New version -> Deploy**.
> Otherwise the old code keeps running.

## 2. Put the project on GitHub

1. Unzip `ParcelScanner.zip` on your computer.
2. Create a new GitHub repository.
3. Upload **everything inside the unzipped folder** (drag the files and folders into the repository page).
   Make sure these exist in the repository: `.github/workflows/build-apk.yml`, `gradlew`,
   `gradle/wrapper/gradle-wrapper.jar`, `app/`, `settings.gradle`, `build.gradle`.
   (`.github` is a hidden folder on some computers. If it did not upload: *Add file -> Create new file*, type
   `.github/workflows/build-apk.yml` as the name and paste the YAML.)
4. Optional shortcut: open `app/src/main/java/com/chewanninja/parcelscanner/Config.java` on GitHub, paste your Web app URL
   and secret key into `DEFAULT_SCRIPT_URL` / `DEFAULT_SECRET_KEY`, commit. Then you do not have to type them on the K7.

## 3. Build the APK

**GitHub -> Actions -> Build APK -> Run workflow -> (wait ~3-5 min) -> open the finished run -> Artifacts -> ParcelScanner-KaicomK7**

The download is a zip containing `ParcelScanner-KaicomK7.apk` (debug build). Build logs are on the run page
(open the failed step if anything goes red).

## 4. Install on the K7

1. Copy the APK to the K7 (USB cable, Google Drive, email...).
2. Settings -> Security -> allow **Unknown sources** (Android 7 wording).
3. Open the APK file and install.

## 5. First-time setup in the app

1. Open **Settings / Scanner Test**.
2. Enter the **Web app URL** and **Secret key** (skip if you put them in `Config.java`). Press **Save**.
3. Press **Test Google Sheets connection** -> you should see `OK: Connected ...`.
4. Press **Scanner Test** and scan a barcode. You should see `ENTER: DETECTED (via KEY_EVENT / EDITOR_ACTION / NEWLINE_CHAR)`.

### If the scanner test says "ENTER: NOT RECEIVED" or shows nothing
* In the K7's own scanner settings (the scanner utility that came with the device - its name varies) set the output mode to
  **keyboard / edit-box input** and the suffix / terminator to **ENTER**.
* If the scanner only sends a **broadcast intent** (nothing is typed), tick *Enable broadcast scanner input* in Settings and enter
  the action name + extra name from the scanner utility. The Scanner Test page lists every broadcast it receives with all extras,
  which helps to find the right names. Scanner code is modular (`ScannerSource` interface), so a vendor SDK can be added without
  touching the rest of the app.

## 6. Daily use

1. Tap **RPU** or **RTS** (big banner shows the active mode; it cannot be switched while a scan is being submitted).
2. Scan. No buttons needed. Result card shows: SUCCESS / DUPLICATE / ERROR, tracking number, timestamp.
3. Scan the next parcel straight away - uploads run in the background.

## How it behaves

| Topic | Behaviour |
|---|---|
| Timestamp | Created by Google Apps Script (Asia/Kuala_Lumpur). Stored as real date / date-time values. |
| Offline | Scan is saved on the K7 and retried every 15 s. The row's timestamp is the time the server received it, not the time of the scan. Until uploaded the app shows the device time marked `(device)` / `*`. |
| Duplicates | Checked on the device (per mode) and on the server using column C only (no extra columns). Duplicate = not added again. RPU and RTS are independent. |
| Retry safety | If the reply is lost, the retry returns the original result instead of writing a second row. |
| Invalid barcode | Must be 6-40 characters, letters / digits / `-`. Anything else (e.g. a URL QR code) is rejected, nothing saved. |
| Scan timeout | Settings: half-received input with no ENTER is discarded after N ms (default 2000, 0 = off). |
| ENTER handling | Hardware key event (ACTION_DOWN + KEYCODE_ENTER), editor action, and CR/LF characters all handled; a guard prevents processing the same ENTER twice. |

## Hardware validation

This build has **not** been tested on a real Kaicom K7 by me. Use the Scanner Test page on the device before relying on it.
