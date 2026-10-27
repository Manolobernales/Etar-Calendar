# ICS URL subscriptions for Etar

## Goal

Add recurring, read-only subscriptions to remote iCalendar (.ics) URLs while preserving Etar's existing calendar UI, event editing, widgets, and current ICS file import/export.

## Design

1. Keep Etar's existing ICS file import/export behavior unchanged.
2. Store user-provided subscription URLs and stable subscription identifiers separately from Android calendar events.
3. Fetch HTTPS URLs in the background with bounded timeouts and response-size limits. Validate the iCalendar content before applying changes.
4. Parse VEVENT records, including UID, DTSTART, DTEND, SUMMARY, DESCRIPTION, LOCATION, STATUS, RRULE, RDATE, EXDATE, and time zones to the extent supported by the existing parser.
5. Publish each subscription as a dedicated local calendar through Android's Calendar Provider so existing Etar views and widgets can display events without replacing the calendar engine.
6. Track source UID per subscription so refreshes update existing events, remove cancellations, and avoid duplicate imports.
7. Provide a settings screen to add, rename, enable/disable, refresh, and remove subscriptions; show last-sync status and actionable errors.
8. Schedule refreshes using WorkManager with network constraints and backoff. A manual refresh remains available.
9. Never transmit calendar URLs or event contents to third parties. Treat URL contents as untrusted input and require HTTPS by default.

## Implementation sequence

- Phase A: verify build and test baseline on a dedicated branch.
- Phase B: inspect the existing ICS parser/importer and Calendar Provider helpers; reuse compatible code rather than adding a second parser unnecessarily.
- Phase C: implement subscription storage, network fetching, and parser-level tests.
- Phase D: implement idempotent synchronization to a dedicated local calendar.
- Phase E: add settings UI and background scheduling.
- Phase F: build APK in GitHub Actions and test install/sync on a real Android device.

## Acceptance checks

- Existing app features and widgets remain present.
- A valid ICS URL creates one visible subscription calendar.
- Refresh updates an event with the same UID instead of duplicating it.
- Cancelled/deleted events are handled consistently.
- Invalid URLs, HTTP errors, oversized responses, malformed ICS, and offline mode show a useful status without crashing.
- Existing Google/Microsoft calendars and ICS file import/export continue to work.

## Licensing

Etar is GPL-3.0. Any distributed derivative must comply with the applicable GPL-3.0 terms and retain required notices.
