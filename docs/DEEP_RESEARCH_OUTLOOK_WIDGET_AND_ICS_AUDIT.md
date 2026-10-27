# Deep research: Outlook Android, calendar widgets, and Etar ICS implementation

Research date: 2026-10-27
Repository branch: `ics-subscriptions`
Purpose: determine the lowest-risk way to show Outlook + Google events in an Android home-screen widget, then audit the current implementation against official behavior and known failure modes.

## Executive conclusion

**Do not assume a remote ICS subscription is the right first solution.** Microsoft Outlook for Android has its own configurable agenda/calendar widget. Outlook also supports optional synchronization to Android's native Calendar Provider for supported accounts, but that switch can be disabled by default or blocked by enterprise policy. Etar reads Android Calendar Provider and already has an agenda widget. The preferred architecture is therefore: (1) verify Outlook native calendar sync and Etar's existing widget; (2) only if the native route is unavailable, continue a clearly labeled read-only ICS fallback; (3) do not claim parity with Outlook or Microsoft Graph synchronization.

## 1. Source inventory (official / primary references)

### Microsoft Outlook end-user support
1. Add the Outlook Calendar widget: https://support.microsoft.com/en-us/outlook/how-do-i-add-the-outlook-for-android-calendar-widget-to-my-home-screen
2. Resize Outlook Calendar widget: https://support.microsoft.com/en-us/outlook/how-do-i-resize-the-outlook-for-android-calendar-widget-on-my-home-screen
3. Optimize Outlook mobile and add widget: https://support.microsoft.com/en-us/outlook/optimize-the-outlook-mobile-app-for-your-ios-or-android-phone
4. Outlook Android help hub (includes widget, shared calendar, missing Google calendars topics): https://support.microsoft.com/en-us/outlook/outlook-for-android-help
5. Outlook mobile FAQs: https://support.microsoft.com/en-us/outlook/outlook-for-ios-android-faqs
6. Troubleshoot Outlook mobile sync/sign-in: https://support.microsoft.com/en-us/outlook/install-mobile/troubleshoot-outlook-mobile-issues
7. Troubleshoot calendar/contact sync: https://support.microsoft.com/en-us/outlook/can-t-sync-calendar-and-contacts-with-my-phone-or-tablet
8. Spanish calendar sync to default calendar app: https://support.microsoft.com/es-es/outlook/how-do-i-sync-my-outlook-calendar-to-the-default-calendar-app-s
9. Microsoft Outlook support index: https://support.microsoft.com/en-us/outlook

### Microsoft admin / enterprise configuration
10. Outlook for iOS/Android configuration with Intune: https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/outlook-for-ios-and-android/outlook-for-ios-and-android-configuration-with-microsoft-intune
11. Configure Microsoft 365 apps (Allow Calendar Sync): https://learn.microsoft.com/microsoft-365/solutions/apps-config-step-2?view=o365-worldwide
12. Microsoft Intune app protection policy overview: https://learn.microsoft.com/en-us/mem/intune/apps/app-protection-policy
13. Microsoft 365 admin center: https://admin.microsoft.com/

### Android platform primary references
14. App widgets overview: https://developer.android.com/develop/ui/views/appwidgets/overview
15. Advanced app widget behavior and update timing: https://developer.android.com/develop/ui/views/appwidgets/advanced
16. App widget configuration: https://developer.android.com/develop/ui/views/appwidgets/configuration
17. Calendar Provider reference: https://developer.android.com/identity/providers/calendar-provider
18. Calendar Provider common intents: https://developer.android.com/guide/components/intents-common#Calendar
19. Runtime permissions: https://developer.android.com/training/permissions/requesting
20. WorkManager periodic work: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
21. WorkManager retry and backoff: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#retry_backoff
22. Android background execution limits: https://developer.android.com/about/versions/oreo/background
23. Doze and App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
24. App widget host and resizing behavior: https://developer.android.com/develop/ui/views/appwidgets/overview#widget_sizing
25. Android security best practices: https://developer.android.com/privacy-and-security/security-best-practices

### Calendar formats, standards, and Etar source
26. RFC 5545 iCalendar specification: https://www.rfc-editor.org/rfc/rfc5545.html
27. RFC 5545 information page: https://www.rfc-editor.org/info/rfc5545/
28. Etar upstream repository and README: https://github.com/Etar-Group/Etar-Calendar
29. Etar upstream known ICS import issue #653: https://github.com/Etar-Group/Etar-Calendar/pull/653
30. Etar upstream issues: https://github.com/Etar-Group/Etar-Calendar/issues
31. Etar upstream pull requests: https://github.com/Etar-Group/Etar-Calendar/pulls
32. Etar feature request for month/week widgets #2042: https://github.com/Etar-Group/Etar-Calendar/pull/2042
33. Microsoft Graph calendar events API (if future authenticated integration is ever considered): https://learn.microsoft.com/en-us/graph/api/resources/event?view=graph-rest-1.0
34. Microsoft Graph calendar delta query (incremental sync concept): https://learn.microsoft.com/en-us/graph/delta-query-events
35. ICS recurrence rule reference: https://www.icalendar.org/iCalendar-RFC-5545/3-8-5-3-recurrence-rule.html

Note: Microsoft Graph is a separate, authenticated API integration—not a public ICS URL. It would require OAuth, token lifecycle, tenant/account handling, consent, privacy review, and a substantially different sync design.

## 2. Reverse-engineering the current product architecture

### Expected data flow if using native Outlook sync

Outlook account/server -> Outlook for Android sync engine -> Android Calendar Provider -> Etar calendar query/filter -> Etar Agenda widget -> Android launcher.

Consequences:
- Etar should not need Outlook credentials, Microsoft Graph permissions, or an independent polling loop if the provider contains the events.
- Outlook's native sync setting and account policy are upstream dependencies. If they are disabled, Etar cannot display those Outlook events from the provider.
- Android work profile and personal profile can have separate providers. A work calendar may be invisible to a personal-profile app/widget by design.
- The Outlook widget itself is an alternative if the user's only requirement is seeing Outlook calendars; however, showing Google + Outlook together depends on which accounts/calendars Outlook itself exposes and the widget configuration.

### Current ICS fallback data flow

URL stored in private preferences -> WorkManager fetch over HTTPS -> bounded response -> private ICS snapshot -> minimal VEVENT parser -> Android Calendar Provider calendar created for the feed -> Etar queries that provider -> Etar widget displays imported events.

Observed implementation choices in this branch:
- HTTPS URL validation and size-bounded downloads.
- WorkManager periodic refresh and immediate work after adding a feed.
- A per-feed local calendar and `CUSTOM_APP_URI` event identity to avoid duplicates.
- TZID-aware basic date parsing.
- Attempted stale-event reconciliation and feed calendar removal.

### Key failure discovered from source inspection

The current importer performs destructive stale-event reconciliation after it parses VEVENT blocks, while the worker's completeness check relies on finding `BEGIN:VCALENDAR` and `END:VCALENDAR`. That is not a full syntax/semantic validation of all events. A malformed or partially parseable but superficially bracketed feed could produce an incomplete `seenUris` set and cause valid existing events to be deleted. **This must be fixed before release**: stage the full parse, validate structural balance and required fields, and do not reconcile deletions when parse diagnostics indicate an incomplete/invalid feed. Preserve last-known-good events on network or parse failure.

### Other engineering risks found

- The parser maps properties by name to a single value, losing repeated properties such as multiple EXDATE/RDATE and potentially losing parameters beyond the first; this is not full RFC 5545 support.
- Recurrence (`RRULE`) is written to Calendar Provider but recurrence exceptions and cancellation semantics are not fully reconciled. Recurring events need dedicated tests.
- ICS `DTEND` for all-day events is end-exclusive under RFC 5545. Confirm the Calendar Provider interpretation and test all-day event boundaries.
- `VTIMEZONE` definitions are not fully interpreted by a simple Java `TimeZone.getTimeZone(tzid)` lookup. Unknown IDs may silently resolve to GMT; must not silently misrepresent local times.
- `DURATION`, `STATUS:CANCELLED`, `RECURRENCE-ID`, `SEQUENCE`, `METHOD:CANCEL`, attachments, alarms and organizer/attendee semantics need explicit support decisions.
- Feed URL changes create a different digest/account identity. Redirect URL canonicalization and URL changes need migration or clear remove/re-add behavior.
- Redirect targets must remain HTTPS and validated; no credential-bearing URLs; avoid logging private feed URLs because ICS URLs may contain access tokens.
- `WorkManager` periodic work is inexact and can be deferred by Doze, battery saver, manufacturer restrictions, or connectivity. A six-hour interval is a minimum scheduling request, not a guarantee of a refresh exactly every six hours.
- Calendar Provider behavior varies by device and provider. Deleting a calendar should be tested with the actual Android provider and with user-created events never mixed into a feed-owned calendar.
- A debug APK is not a release APK; release signing, package ID, update/install behavior and backup/migration need separate decisions.

## 3. Thirty suites of frequent problems and test scenarios

Each suite should become one or more reproducible test cases. A suite is not proof that a bug exists; it is a risk category to test.

### Outlook account and sync (suites 1–8)
1. **Account protocol mismatch** — Outlook account configured as IMAP/POP: email works but calendar sync is unavailable. Verify account type and do not diagnose this as Etar's fault.
2. **Native calendar sync toggle off** — Outlook's Sync Calendars option disabled; provider has no Outlook events. Verify toggle and recheck after sync.
3. **OS calendar permission denied** — Outlook cannot write to the native provider. Verify Android permission and app-level setting.
4. **Enterprise policy blocks sync** — Intune App Protection Policy blocks sync to native apps/add-ins. Confirm with administrator; do not attempt to bypass policy.
5. **Work profile separation** — Outlook runs in managed profile but Etar/widget runs in personal profile. Confirm profile context; do not assume cross-profile data access.
6. **Shared calendar missing** — user selected a calendar in Outlook but it is not exposed to native provider/widget. Check Outlook's supported shared-calendar behavior and selection settings.
7. **Google calendars missing in Outlook widget** — Outlook's widget/account calendar selection differs from Android provider visibility. Verify actual widget configuration rather than assuming all provider calendars are shown.
8. **Account token/session expired or server outage** — sync stops while app remains installed. Use Microsoft troubleshooting, account reset/re-auth only when appropriate, and verify service status.

### Widget / launcher / Android OS (suites 9–15)
9. **Widget absent from picker** — app install/version/launcher state or widget provider metadata problem. Restart launcher/device only after checking app installation.
10. **Widget cannot be resized** — launcher grid or provider min/max dimensions conflict. Test narrow/wide and multiple launcher grid sizes.
11. **Widget stale after events change** — distinguish Outlook sync delay, provider update delay, widget refresh delay and launcher caching.
12. **Battery optimization / Doze defers refresh** — verify last update and constraints; do not promise exact periodic timing.
13. **Background data disabled / Data Saver** — online feed fails in background but works manually. Test foreground/background network policy.
14. **Force-stop / app standby / OEM task killer** — scheduled work may not run until system permits it. Test common device power modes.
15. **Widget layout and density issues** — small/large screen, font scaling, dark/light theme, landscape/foldable and locale. Ensure content does not clip and empty states are legible.

### Android Calendar Provider / permissions (suites 16–20)
16. **Read calendar permission denied** — app shows no events or provider queries fail. Test first-run permission denial and later grant.
17. **Write calendar permission denied** — feed calendar creation/import fails; surface actionable error rather than success toast.
18. **Provider/account unavailable** — no suitable local calendar or provider returns null/throws. Preserve downloaded snapshot and report import failure.
19. **Duplicate events after retry** — same UID/feed repeated or changed event fields. Verify stable identity and idempotent updates.
20. **Calendar visibility/account ownership confusion** — feed calendar hidden, wrong account type or event created in wrong calendar. Verify calendar ID, account name/type, visibility and sync-adapter URI.

### ICS parsing and semantics (suites 21–27)
21. **Line endings and folding** — CRLF/LF, folded lines, UTF-8 and long DESCRIPTION values. Unfold according to RFC before parsing.
22. **Escaped text and property delimiters** — escaped commas/semicolons/newlines/backslashes and colon inside values. Split property at the correct first unescaped delimiter and unescape values.
23. **Timezone and DST** — UTC `Z`, `TZID`, floating local time, missing/unknown TZID, DST boundaries, custom VTIMEZONE. Never silently shift times.
24. **All-day events and exclusive DTEND** — DTSTART;VALUE=DATE with end date; test one-day and multi-day events across month/year boundaries.
25. **Missing/invalid DTSTART or DTEND / DURATION** — skip invalid event safely; use RFC-supported duration defaults only when correct; do not import zero-length or inverted events silently.
26. **Recurrence and exceptions** — RRULE, RDATE, EXDATE, RECURRENCE-ID, THISANDFUTURE, modified/cancelled instances. Avoid pretending that storing RRULE alone covers all semantics.
27. **Cancellations and deleted events** — STATUS:CANCELLED, METHOD:CANCEL, events absent from a full snapshot. Reconcile only after a complete valid parse; a feed parse failure must never delete prior events.

### Network, privacy, CI, and release (suites 28–30)
28. **HTTP failures and redirect security** — 301/302/307/308, redirect loops, downgrade to HTTP, missing Location, 401/403/404/429/5xx, timeout, captive portal and TLS failure. Bound redirects, bytes and duration; retry transient failures with backoff.
29. **Private feed URL leakage / resource exhaustion** — ICS URLs may contain bearer-like secrets. Do not log full URLs, keep snapshots private, cap response size and event count, validate HTTPS destinations and avoid SSRF-style URL abuse.
30. **Build/release integrity** — JDK toolchain mismatch, Gradle/AGP incompatibility, Android SDK missing, CI not triggered, artifact absent, debug/release package mismatch, unsigned release and no device test. Require a green CI run, downloadable APK artifact, checksum, version metadata and manual smoke test before declaring completion.

## 4. Recommended implementation sequence

### Phase A — product validation (no new sync code)
1. On the Android phone, confirm Outlook account type and enable Sync Calendars if available.
2. Confirm Outlook events appear in the system Calendar Provider using the phone's default calendar app or another trusted provider-aware app.
3. Confirm Google events are visible there too.
4. Add Etar's existing Agenda widget and configure visible calendars.
5. Record exact failures (account type, Android version, work/personal profile, Outlook sync setting, visible calendars, launcher, battery settings).

### Phase B — make CI honest and reproducible
1. CI must install JDK 21 because the current project explicitly declares `jvmToolchain(21)` and Java source/target 21.
2. Run Gradle wrapper validation and `./gradlew :app:assembleDebug`.
3. Upload APK artifact only on success; include commit SHA and checksum in release notes.
4. Keep Java 25 as the Gradle runtime only if compatibility is confirmed; do not change the project's language/toolchain target just to silence the local JDK error.
5. Treat no workflow run as no validation. Confirm repository Actions settings and inspect the run list before claiming success.

### Phase C — harden ICS fallback if native sync cannot satisfy the requirement
1. Replace the ad-hoc parser with a maintained RFC 5545 parser or isolate a robust parser module and add tests.
2. Parse the entire feed into an immutable validated model before any Calendar Provider mutations.
3. Make import reconciliation staged and failure-safe. Preserve last-known-good events unless a complete feed validates.
4. Implement and test UID stability, recurrence exceptions, cancellation, all-day boundaries, time zones, repeated properties and text escaping.
5. Keep feed calendars isolated; never mix feed-owned events with user-owned events.
6. Explain in UI that public ICS is read-only and refresh timing is best-effort, not real-time Outlook sync.

## 5. Architecture decision matrix

| Approach | Benefits | Limitations | Recommendation |
|---|---|---|---|
| Outlook native widget | Lowest effort, official, no custom sync code | Primarily Outlook's own account/calendar view; combined account visibility depends on Outlook widget selection | Try first if Outlook events alone are sufficient |
| Outlook Sync Calendars -> Android provider -> Etar widget | One provider feeds Etar; can combine provider calendars where available | Toggle/permission required; enterprise policy/work-profile restrictions; account type matters | Preferred if both Outlook and Google events appear in provider |
| Public HTTPS ICS -> Etar provider | Works with published feeds; independent of Outlook app | Read-only, delayed, possible private URL token, limited recurrence/exception semantics | Fallback only, hardened before release |
| Microsoft Graph integration | Authenticated, richer event model, incremental delta support | OAuth/consent/token lifecycle, app registration, tenant policies, privacy and ongoing maintenance | Separate future project; not a quick ICS fix |

## 6. Release gate / acceptance criteria

- [ ] There is a written, tested decision on native sync vs ICS fallback.
- [ ] CI is triggered on the current branch and reports success.
- [ ] `app-debug.apk` exists as a downloadable artifact from that successful run.
- [ ] Parser tests cover all relevant suites 21–27.
- [ ] Failure tests prove that a bad/partial feed does not delete valid existing events.
- [ ] On-device smoke test confirms Google and Outlook events are visible in the widget and updates do not create duplicates.
- [ ] Offline, denied-permission, work-profile and battery-restricted behavior is documented.
- [ ] Release notes state limitations; no claim of Microsoft-certified or real-time Outlook synchronization.

## 7. Current status

This document and the CI JDK correction are committed to the `ics-subscriptions` branch. GitHub Actions previously reported zero workflow runs, so no build is yet verified. The ICS implementation has not been tested on a real device. The next high-value action is to validate Outlook's native Sync Calendars path on the user's Android phone before expanding the ICS code.

## 8. Source notes

Official Microsoft docs describe Outlook's widget configuration and native calendar sync, including the fact that sync may be off by default and can be controlled by Intune policy. Android docs describe widget update limits and recommend WorkManager for longer asynchronous work. RFC 5545 defines recurrence, timezone, all-day and event-property semantics. Etar's README explicitly says it reads synchronized calendars from Android Calendar Provider and documents known issues in its ICS import feature.
## 9. Expanded reference set and reverse-engineering notes

Additional primary references checked during the second pass:
- Microsoft: Outlook Android calendar widget setup: https://support.microsoft.com/en-us/outlook/how-do-i-add-the-outlook-for-android-calendar-widget-to-my-home-screen
- Microsoft: Outlook Android widget resizing: https://support.microsoft.com/en-us/outlook/how-do-i-resize-the-outlook-for-android-calendar-widget-on-my-home-screen
- Microsoft: Outlook mobile optimization and widget setup: https://support.microsoft.com/en-us/outlook/optimize-the-outlook-mobile-app-for-your-ios-or-android-phone
- Microsoft: Outlook mobile troubleshooting: https://support.microsoft.com/en-us/outlook/install-mobile/troubleshoot-outlook-mobile-issues
- Microsoft: calendar/contact sync troubleshooting: https://support.microsoft.com/en-us/outlook/can-t-sync-calendar-and-contacts-with-my-phone-or-tablet
- Microsoft Learn: Outlook native calendar sync and Intune policy controls: https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/outlook-for-ios-and-android/outlook-for-ios-and-android-configuration-with-microsoft-intune
- Microsoft Learn: Allow Calendar Sync policy: https://learn.microsoft.com/microsoft-365/solutions/apps-config-step-2?view=o365-worldwide
- Android Developers: Calendar Provider overview: https://developer.android.com/identity/providers/calendar-provider
- Android Developers: persistent background work: https://developer.android.com/develop/background-work/background-tasks/persistent
- Android Developers: define periodic work and constraints: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android Developers: managing unique work: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- Android Developers: advanced widget update behavior: https://developer.android.com/develop/ui/views/appwidgets/advanced
- RFC Editor: RFC 5545: https://www.rfc-editor.org/rfc/rfc5545.html
- Etar upstream issue tracker: https://github.com/Etar-Group/Etar-Calendar/issues
- Etar upstream pull request tracker: https://github.com/Etar-Group/Etar-Calendar/pulls

### Reverse-engineering conclusions

1. **Widget rendering and event synchronization are separate subsystems.** A widget can be stale even when Outlook itself is current, and Outlook can be stale even if the widget correctly renders the provider. Diagnostics must inspect each boundary separately.
2. **The native-sync switch is an architectural boundary.** Microsoft's documentation explicitly says Sync Calendars exports Outlook events to Android's native Calendar app and requires user participation/permissions. Enterprise policy can block availability. This makes provider inspection the decisive experiment.
3. **A six-hour WorkManager request is not a six-hour freshness guarantee.** Android documents periodic work as deferrable, with exact execution depending on constraints and system optimization. UI should say 'last checked' and expose manual refresh rather than promise real-time synchronization.
4. **ICS import is a data reconciliation problem, not just a download problem.** Parsing must be complete and validated before writes/deletes. Download success, HTTP 200, or presence of VCALENDAR markers is insufficient to authorize destructive reconciliation.
5. **Calendar Provider is a shared system database.** Calendar and event mutations require the correct permissions; event ownership, calendar IDs, account type, time zone and sync-adapter semantics need explicit handling.
6. **Do not implement Microsoft Graph merely to solve widget display.** Graph could offer richer authenticated access, but requires a registered app, OAuth, consent, token refresh, privacy and tenant policy handling. Native sync is the lowest-risk supported path if it meets the need.
7. **No reliable reverse engineering of Outlook's private internals is needed or appropriate.** The supported contract is public UI, documented sync settings, Android Calendar Provider, official widget behavior and standards-compliant ICS. Avoid scraping Outlook's private databases, intercepting credentials, or relying on undocumented endpoints.

### Reproducible diagnostic trace to collect on the phone

Before changing more code, record this sequence with timestamps:
1. Create or identify a test event in Outlook with a unique title, e.g. `SYNC-PROBE-2026-10-27-01`.
2. Verify it appears in Outlook mobile and Outlook on the web.
3. Enable Outlook > Settings > account > Calendar > Sync Calendars (if available) and grant requested permission.
4. Verify the probe appears in the phone's native Calendar app and identify which calendar/account owns it.
5. Verify Etar displays the probe when that calendar is enabled.
6. Add Etar Agenda widget and verify the probe appears.
7. Edit the probe title/time in Outlook and record the time each layer updates.
8. Repeat with a Google event and a work-profile account if applicable.
9. If the probe fails at step 3, investigate account type/policy; if at step 4, investigate provider sync/permissions; if at step 5, investigate Etar filters/provider query; if at step 6, investigate widget update/rendering.

### Known limitation of this audit

This is a source-and-documentation audit, not a completed device reverse-engineering session. No device logs, Microsoft account policy, Android provider database, or successful CI run were available in this session. Findings are hypotheses grounded in official docs and source inspection until the probe sequence and CI are completed.