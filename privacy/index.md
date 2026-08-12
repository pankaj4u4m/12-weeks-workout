---
title: TwelveWeek Privacy Policy
---

# TwelveWeek Privacy Policy

_Last updated: 2026-08-12_

TwelveWeek is a personal workout-execution app. This page explains, plainly, what data the app touches and where it goes.

## The short version

- No account, no sign-up, no login.
- No analytics, no ads, no trackers, no crash-reporting SDKs.
- The developer does not operate a server and never receives your data.
- Everything the app needs to run — your programs and your progress — lives on your device.

## What the app stores on your device

- **Program progress**: which exercises/workouts/weeks you've completed. Stored locally via Android's standard app storage, included in Android's own encrypted auto-backup (so it survives a device restore), never transmitted anywhere.
- **Selected program**: which of the bundled or imported programs you're currently following.
- **Optional ExerciseDB API key**: if you choose to connect exercise videos, the API key you paste in is encrypted on-device (Android Keystore-backed) and used only to call ExerciseDB directly from your phone. It is never sent to the developer or any server other than ExerciseDB's own API.

## Network calls the app makes

All of these are made directly from your device to the third party — never through a TwelveWeek server, because there isn't one:

| Destination | What for | Trigger |
|---|---|---|
| `github.com` (public repo) | Refreshing/syncing the bundled 12-week program library | Every app launch, if online |
| `wger.de` public exercise API | Free exercise demo videos/photos | Viewing an exercise, if online |
| ExerciseDB via RapidAPI | Exercise demo videos/photos | Only if you've connected your own free API key |

If you're offline, the app keeps working from the last data it already has — no blocked screens.

## What we don't do

- We don't collect your name, email, or any account identifier — there's no account.
- We don't sell or share data, because we don't collect any to begin with.
- We don't use cookies, ad identifiers, or cross-app tracking.
- We don't target this app at children.

## Permissions

- **Internet** — for the program sync and exercise media described above.
- **Vibrate** — for workout timer haptics.

Neither permission is used to collect or transmit personal data.

## Changes

If this policy changes, the "Last updated" date above will change. Material changes will be reflected in the app's Play Store listing notes.

## Contact

Questions about this policy: **pankaj4u4m@gmail.com**
