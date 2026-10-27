# Android calendar widget: research and implementation plan

Date: 2026-10-27
Branch: ics-subscriptions

## Product goal to validate

The practical goal is a reliable Android home-screen calendar widget that can show events from both Google Calendar and Microsoft Outlook/Microsoft 365 calendars. The first question is not how to fetch an ICS URL, but whether both account calendars are already synchronized into Android's system Calendar Provider and whether the widget can read them.

## Findings from official documentation

1. Outlook for Android already provides a native calendar widget. Microsoft documents adding its Calendar widget from the Android widget picker and selecting which calendars/accounts it displays:
   - https://support.microsoft.com/en-us/outlook/how-do-i-add-the-outlook-for-android-calendar-widget-to-my-home-screen
   - https://support.microsoft.com/en-us/outlook/training/outlook-android-widgets

2. Outlook can sync its calendar to Android's default calendar apps. Microsoft documents an account setting named Sync calendars; for supported Microsoft 365 and Outlook.com accounts, calendar events can be exported to default calendar apps and changes can sync both ways. Turning this off removes the synced calendars/events from those apps:
   - https://support.microsoft.com/es-es/outlook/how-do-i-sync-my-outlook-calendar-to-the-default-calendar-app-s

3. Android widgets are a presentation layer, not a calendar sync engine. Android's official guidance describes widgets as glanceable views, recommends configuration where users choose the data they want to see, and requires content/layout to adapt to different widget sizes:
   - https://developer.android.com/develop/ui/views/appwidgets/overview
   - https://developer.android.com/develop/ui/views/appwidgets/configuration

4. Etar already uses Android Calendar Provider. The project README says Etar shows synchronized calendars through Android's calendar storage and that a separate sync app is needed for CalDAV. This means provider-backed Outlook and Google calendars should be preferred over duplicating account synchronization inside Etar.

## Recommended decision tree

1. On Android, enable Outlook > Settings > target account > Sync calendars (if the account type supports it).
2. Confirm Outlook events appear in the phone's default Calendar app or another app that reads Android Calendar Provider. Confirm Google calendars are visible there too.
3. Add Etar's existing Agenda widget and configure visible calendars. If it can show both accounts, stop: no custom ICS downloader is needed.
4. If Outlook events do not reach Android Calendar Provider, troubleshoot account type, policy, permissions and sync settings before coding. Some work/school account policies or account types may limit export.
5. Only if the provider route is unavailable, evaluate remote ICS as a separate fallback. ICS is usually read-only and is not equivalent to Outlook's authenticated, bidirectional synchronization. Do not ask users for passwords or embed OAuth secrets in the app.

## Current branch review: risks to resolve before release

- The remote-ICS implementation is a substantial fallback and is not yet validated by an Android build or device test.
- The importer handles only a subset of iCalendar fields; recurring events, cancellations, exceptions, attendee/organizer data, alarms and repeated properties need explicit tests and may not round-trip.
- A downloaded response containing VCALENDAR start/end markers does not prove every event parsed correctly. Reconciliation must never delete existing events after a partial or invalid parse.
- Feed identity is based on a URL digest; URL changes, redirects, UID collisions, time zones and all-day end-exclusive semantics need explicit tests.
- Calendar Provider behavior varies by provider/device; test permissions, visibility, account removal, duplicate prevention, update/deletion reconciliation and offline retry on a real Android device.
- app/build.gradle.kts declares Kotlin jvmToolchain(21) and Java source/target 21. A JDK 25 runtime alone does not satisfy that toolchain requirement unless JDK 21 is also installed or a toolchain resolver is configured.
- The GitHub Actions workflow previously installed only JDK 25, so CI could fail at toolchain resolution even if Gradle itself runs on Java 25.

## Validation plan (in order)

### P0 — establish the simplest supported solution
- [ ] Enable Outlook calendar sync to Android Calendar Provider where supported.
- [ ] Verify Google and Outlook calendars appear in a provider-backed calendar app.
- [ ] Verify Etar's existing agenda widget can select both calendars.
- [ ] Record Android version, device/launcher, Outlook account type (Outlook.com, Microsoft 365 work/school, on-premises), and results.

### P1 — make CI capable of building the current project
- [ ] Install JDK 21 in CI to satisfy the declared toolchain.
- [ ] Run Gradle wrapper validation and :app:assembleDebug.
- [ ] Upload the debug APK only when the build succeeds.
- [ ] Treat a green build as necessary but not sufficient; test on device.

### P2 — harden the ICS fallback only if P0 fails
- [ ] Add parser unit tests for CRLF/LF, folded lines, escaped text, UTC/local/TZID dates, all-day dates, missing DTSTART/DTEND, malformed events, duplicates, recurrence and cancellations.
- [ ] Parse and validate the entire feed before performing destructive reconciliation.
- [ ] Use transactional or staged reconciliation and retain existing events on download/parse failure.
- [ ] Define supported ICS fields and communicate read-only limitations.
- [ ] Add integration/manual test checklist for Android Calendar Provider.

## Official sources

- Microsoft, Outlook for Android Calendar widget: https://support.microsoft.com/en-us/outlook/how-do-i-add-the-outlook-for-android-calendar-widget-to-my-home-screen
- Microsoft, Outlook Android widgets: https://support.microsoft.com/en-us/outlook/training/outlook-android-widgets
- Microsoft, sync Outlook calendar to default calendar apps: https://support.microsoft.com/es-es/outlook/how-do-i-sync-my-outlook-calendar-to-the-default-calendar-app-s
- Android Developers, app widgets overview: https://developer.android.com/develop/ui/views/appwidgets/overview
- Android Developers, configure widgets: https://developer.android.com/develop/ui/views/appwidgets/configuration
- Etar README and build instructions: https://github.com/Etar-Group/Etar-Calendar

## Release gate

Do not call this finished until the debug APK builds in CI, an APK artifact is available, and the widget has been manually verified with both Google and Outlook events on an Android device.