# Codex handoff — Etar Calendar / Outlook + ICS + widget redesign

## Objective
Continue work on the existing repository and branch without losing prior decisions:
- Repository: https://github.com/Manolobernales/Etar-Calendar
- Working branch: `ics-subscriptions`
- Existing draft PR: https://github.com/Manolobernales/Etar-Calendar/pull/1 (base `master`; do not merge without user approval)
- Benchmark: [docs/GRAPHICAL_WIDGET_BENCHMARK_GITHUB.md](GRAPHICAL_WIDGET_BENCHMARK_GITHUB.md)
- Outlook/ICS research: [docs/DEEP_RESEARCH_OUTLOOK_WIDGET_AND_ICS_AUDIT.md](DEEP_RESEARCH_OUTLOOK_WIDGET_AND_ICS_AUDIT.md) and [docs/ANDROID_OUTLOOK_WIDGET_RESEARCH_PLAN.md](ANDROID_OUTLOOK_WIDGET_RESEARCH_PLAN.md)

## Working rules
1. Inspect the current branch and recent commits before editing. Do not assume this handoff file describes the current code perfectly.
2. Work incrementally on `ics-subscriptions`; preserve existing user work and do not force-push.
3. Do not merge the PR, change repository visibility, or perform destructive actions without explicit approval.
4. For each change: explain intent in commit messages, run relevant tests/build, report exact commands and outcomes. Never claim CI/build passed unless observed.
5. Prefer native Android Calendar Provider sync for Outlook if the user has enabled it; ICS is a read-only fallback and cannot provide all Outlook features.
6. Keep user-facing guidance in Spanish and concise.

## Priority A — finish the graphical widget benchmark and implement the first redesign
The audit/benchmark has been documented but the redesign itself has not yet been implemented or validated on a device.
- Inspect `app/src/main/res/layout/appwidget.xml`, `widget_item.xml`, loading/empty layouts, colors/styles/dimens, month and week layouts, and the Java/Kotlin providers that populate RemoteViews.
- Preserve all view IDs referenced from code.
- Improve agenda row hierarchy and space use: event title first, date/time clearly readable, location secondary, sensible line limits, consistent margins, touch targets and contrast. Adapt to small/resized widgets and launcher sizing.
- Keep light/dark/theme behavior consistent with existing resources; avoid hardcoded colors where resource values exist.
- Update preview/loading/empty states if necessary.
- Inspect Android 12+ behavior and widget previews/resizing.
- Add/adjust tests where feasible. Capture screenshots or provide reproducible device/emulator validation steps; XML inspection alone does not prove the rendered design.
- Do not switch to Jetpack Glance wholesale unless a separate, evidence-based case is made; first improve existing RemoteViews implementation safely.
- Use the weighted visual score from the benchmark: readability 25%, hierarchy 20%, responsiveness 20%, contrast/accessibility 15%, customization 10%, Android coherence 10%.

## Priority B — audit ICS correctness and data safety
Existing branch work includes URL validation, URL registry, worker, bounded download, snapshots, Calendar Provider import, deduplication/reconciliation and timezone handling. Audit actual code before relying on this summary.
Critical risks to verify and fix:
- A failed or incomplete feed parse must never cause deletion of previously imported events. Preserve last-known-good data unless the entire feed parses and reconciliation is safe.
- Multiple EXDATE/RDATE values and RRULE exception handling may be incomplete.
- Unknown TZID values must not silently produce incorrect GMT times; handle/report unsupported zones explicitly.
- Validate RFC 5545 all-day DTSTART/DTEND exclusive-end semantics.
- Validate HTTPS redirects, response-size/time limits, duplicate UID handling, recurrence expansion bounds, cancellation, retries and logging without leaking feed secrets.
- Test malformed/truncated feeds, empty valid feeds vs parse failures, server errors, redirect loops, timezone/DST changes, recurring events, cancellations and removed events.
- Avoid destructive reconciliation when feed data is ambiguous or partial.

## Priority C — build and CI
- Existing note: local Mac Intel environment has Oracle JDK 25.0.5 while the project requires Java toolchain 21; user prefers not to replace Java 25 locally.
- CI workflow was adjusted to JDK 21 in commit `0cd88756312b6b2f1170c45d736bac49d60789e8`. Verify the current workflow and inspect actual GitHub Actions runs.
- Do not infer build success from workflow configuration. Run or inspect CI and fix actual failures.
- Report whether Android SDK/toolchain requirements prevent local validation.

## Suggested execution order
1. Inspect branch/PR status, workflow and latest commits.
2. Implement a small, safe first-pass agenda widget redesign, preserving RemoteViews IDs.
3. Run build/tests and inspect CI; fix failures caused by changes.
4. Audit ICS data-loss risks before adding features.
5. Summarize commits, tests, remaining risks and device checks still needed.

## Reference benchmark
The benchmark compares Etar with repositories/projects including MinCal, Todo Agenda, Calander and Asala Calendar, alongside Android widget documentation and relevant examples. It is a practical code/design benchmark, not a verified popularity ranking of the “20 best” widgets. See the linked benchmark for details and sources.

## Completion report format
Return:
- Changes made (files and commits)
- Build/tests with exact results
- CI status with links if available
- Visual validation status (real screenshot/device vs code-only)
- Remaining risks and next recommended action
