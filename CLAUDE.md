# Athena Podcasts

A minimalist, monochrome Android podcast player (Kotlin + Jetpack Compose + Media3), sibling of Athena Launcher (`../athena-launcher`).

## Spec-driven workflow
Read in this order before changing anything:
1. `.specify/memory/constitution.md` – principles that always apply
2. `specs/001-podcasts-core/spec.md` – what and why (user stories, requirements)
3. `specs/001-podcasts-core/plan.md` – how (architecture, APIs, decisions)
4. `specs/001-podcasts-core/tasks.md` – ordered work; tick items when done

Rules:
- Do not implement behaviour that is not in `spec.md`. If something is missing or ambiguous, update the spec (or add to "Open questions") first.
- Work task by task, in order; each task should leave the app building.
- Requirement IDs (FR-xxx, NFR-xxx) are referenced from tasks and tests. Keep them stable.
- Mark spec items `[NEEDS CLARIFICATION]` rather than guessing.

## Targets
- minSdk 36 (Android 16), target/compile 37 (Android 17). Test on the API 37 emulator.
- Kotlin, Compose, single activity, Media3, WorkManager, Room, DataStore.
- Network only for feeds and episode downloads. No streaming, no analytics.

## Conventions
- Keep v1 very simple: plain visible buttons on the item they act on (Refresh, Download, Play, Played/Unplayed…); no gestures. Add gestures later via a spec.
- App name: Athena Podcasts. Package / application id: `se.anders.athenapodcaster`. Licence: MIT.
- Feed URLs with tokens and credentials are secrets: never log or print them; log through the redaction helper.
- Only downloaded files are played; the player has no network data source.
- Look and feel mirrors Athena Launcher (theme tokens, fonts, text size). Copy from `../athena-launcher/app/src/main/kotlin/se/anders/athenalauncher/ui/theme/Theme.kt`; do not diverge without updating the spec.
- Don't copy code from AntennaPod (GPL-3.0); use it only as a behavioural reference.
- Changes needed in the launcher (appearance bridge) are made in that repo, following its own spec workflow.
