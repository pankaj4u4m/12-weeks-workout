---
name: TwelveWeek
description: An offline-first home workout guide built around the next useful movement.
colors:
  band-cobalt: "#315CFF"
  motion-coral: "#FF5B38"
  recovery-mint: "#B9E6D0"
  training-ink: "#17211D"
  daylight: "#F4F5EF"
  dark-ground: "#111714"
  white: "#FFFFFF"
  light-primary-container: "#DCE4FF"
  light-on-primary-container: "#10205F"
  light-secondary-container: "#FFDED5"
  light-on-secondary-container: "#5B1607"
  light-tertiary: "#247255"
  light-on-tertiary-container: "#073824"
  light-surface-variant: "#E6ECE7"
  light-on-surface-variant: "#4A5751"
  light-outline: "#748079"
  light-outline-variant: "#C6CEC8"
  light-error: "#BA1A1A"
  dark-primary: "#B6C4FF"
  dark-on-primary: "#00258C"
  dark-primary-container: "#183FAF"
  dark-secondary: "#FFB5A2"
  dark-on-secondary: "#6B1B08"
  dark-secondary-container: "#8D2E17"
  dark-on-secondary-container: "#FFDBD1"
  dark-tertiary: "#9DD5BA"
  dark-on-tertiary: "#003825"
  dark-tertiary-container: "#15523C"
  dark-on-tertiary-container: "#B9F1D5"
  dark-on-surface: "#E5EDE7"
  dark-surface: "#18201C"
  dark-surface-variant: "#27312C"
  dark-on-surface-variant: "#C1CBC4"
  dark-outline: "#89958D"
  dark-outline-variant: "#3C4841"
  dark-error: "#FFB4AB"
  dark-on-error: "#690005"
typography:
  display-large:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "64sp"
    fontWeight: 600
    lineHeight: "64sp"
    letterSpacing: "-1.2sp"
  display-medium:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "48sp"
    fontWeight: 600
    lineHeight: "50sp"
    letterSpacing: "-0.8sp"
  headline-large:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "36sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "-0.4sp"
  headline-medium:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "30sp"
    fontWeight: 600
    lineHeight: "34sp"
  headline-small:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "26sp"
    fontWeight: 600
    lineHeight: "30sp"
  title-large:
    fontFamily: "Barlow Semi Condensed, Roboto, sans-serif"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "28sp"
  title-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "0.15sp"
  title-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
  body-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.5sp"
  body-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0.25sp"
  body-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
    letterSpacing: "0.4sp"
  label-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
  label-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: "16sp"
    letterSpacing: "0.5sp"
  label-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "11sp"
    fontWeight: 600
    lineHeight: "16sp"
    letterSpacing: "0.5sp"
rounded:
  extra-small: "8dp"
  small: "12dp"
  medium: "14dp"
  large: "16dp"
  extra-large: "28dp"
spacing:
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.band-cobalt}"
    textColor: "{colors.white}"
    typography: "{typography.label-large}"
    rounded: "{rounded.medium}"
    padding: "16dp 20dp"
    height: "56dp"
  button-tonal:
    backgroundColor: "{colors.light-primary-container}"
    textColor: "{colors.light-on-primary-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.medium}"
    padding: "12dp 16dp"
    height: "48dp"
  card-training:
    backgroundColor: "{colors.white}"
    textColor: "{colors.training-ink}"
    rounded: "{rounded.large}"
    padding: "16dp"
    width: "100%"
  card-next-workout:
    backgroundColor: "{colors.light-primary-container}"
    textColor: "{colors.light-on-primary-container}"
    rounded: "{rounded.large}"
    padding: "20dp"
    width: "100%"
  input-outlined:
    backgroundColor: "{colors.white}"
    textColor: "{colors.training-ink}"
    rounded: "{rounded.extra-small}"
    height: "56dp"
    width: "100%"
  chip-filter:
    backgroundColor: "{colors.white}"
    textColor: "{colors.training-ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.extra-large}"
    padding: "8dp 16dp"
    height: "48dp"
  guided-stage:
    backgroundColor: "{colors.light-surface-variant}"
    textColor: "{colors.training-ink}"
    rounded: "{rounded.large}"
    width: "100%"
  progress-band:
    backgroundColor: "{colors.light-outline-variant}"
    textColor: "{colors.band-cobalt}"
    rounded: "{rounded.extra-large}"
    height: "12dp"
    width: "100%"
---

# Design System: TwelveWeek

## Overview

**Creative North Star: "Resistance Band Flow"**

Resistance Band Flow treats the app like exercise equipment: direct, durable, and alive with controlled tension. Cobalt carries momentum, coral marks the active edge, mint confirms recovery and completion, and soft mineral surfaces keep the room calm enough for repeated daily use.

This is an Operate interface for a person moving between a phone and the floor. The next unfinished workout receives the first and strongest action; route and week context follow; administration recedes. Material 3 supplies trusted Android structure while the elastic-band mark, condensed display face, tonal color, and disciplined rectangular controls provide identity.

**The Next Rep Rule.** The next unfinished workout is always the strongest action; route, week detail, plan management, and program switching remain subordinate.

**Key Characteristics:**
- Native Android hierarchy with one dominant next action.
- Cobalt, coral, and mint used as functional training signals.
- Barlow Semi Condensed headlines paired with legible Roboto body copy.
- Softly rectangular 12–16dp surfaces and large one-handed controls.
- Elastic-band geometry reserved for progress, orientation, and moments of completion.
- Offline-first behavior with optional connected exercise media.

## Colors

The palette feels energetic without becoming gym-aggressive: saturated training signals sit on mineral daylight or calm charcoal surfaces.

### Primary
- **Band Cobalt**: Primary actions, completed route segments, progress fill, and focused labels. In light theme it resolves to `band-cobalt`; dark theme uses `dark-primary` with its matching on-primary and container roles.
- **Cobalt Wash**: The next-workout card and selected-plan container. It carries emphasis without competing with the filled primary action.

### Secondary
- **Motion Coral**: Current route nodes, progress markers, and small moments of active tension. It is a marker, not a second primary button color.
- **Coral Wash**: Secondary containers and selected-state support; preserve its matching on-container contrast.

### Tertiary
- **Recovery Mint**: Completion, recovery, and positive status surfaces.
- **Deep Mint / Night Mint**: High-contrast tertiary actions or labels in the active light or dark scheme.

### Neutral
- **Daylight**: Light-theme app background.
- **Training Ink**: Light-theme primary text.
- **Dark Ground**: Dark-theme app background.
- **Clean White / Night Surface**: Resting card surfaces for light and dark themes.
- **Cool Training Surface / Night Training Surface**: Media stages, route summaries, and secondary grouping.
- **Muted Training Ink / Night Muted Ink**: Supporting metadata and prescriptions.
- **Utility Outline / Soft Divider**: Boundaries only where grouping needs reinforcement; dividers stay quieter than text.

### Theme role mapping
- Light: background `daylight`; surface `white`; surfaceVariant `light-surface-variant`; on-surface `training-ink`; primary `band-cobalt`; secondary `motion-coral`; tertiary container `recovery-mint`.
- Dark: background `dark-ground`; surface `dark-surface`; surfaceVariant `dark-surface-variant`; on-surface `dark-on-surface`; primary `dark-primary`; secondary `dark-secondary`; tertiary `dark-tertiary`.
- Errors use the scheme-specific `light-error` or `dark-error` pair. Never reuse coral as error red.

**The Semantic Palette Rule.** Compose screens consume Material color roles, never raw color literals; the active light or dark scheme resolves the value.

**The Three Signals Rule.** Cobalt means forward progress and primary action, coral marks the active position, and mint communicates completion or recovery; do not interchange them for decoration.

## Typography

**Display Font:** Barlow Semi Condensed SemiBold (with Roboto and sans-serif fallback)  
**Body Font:** Roboto (with the Android sans-serif fallback)  
**Label Font:** Roboto SemiBold

**Character:** Condensed display type gives movement names, week titles, and progress moments the compact force of printed training cards. Roboto keeps prescriptions, metadata, buttons, filters, and accessibility-scaled body copy familiar on Android.

### Hierarchy
- **Display Large** (600, 64sp/64sp, -1.2sp): Timer values, repetition counts, and rare completion-scale numerals.
- **Display Medium** (600, 48sp/50sp, -0.8sp): Onboarding and workout-complete statements.
- **Headline Large** (600, 36sp/40sp, -0.4sp): Screen titles and the active exercise name.
- **Headline Medium** (600, 30sp/34sp): Next-workout title and major completion state.
- **Headline Small** (600, 26sp/30sp): Route and current-week section headings.
- **Title Large** (600, 24sp/28sp): Program cards and metric values.
- **Title Medium / Small** (Roboto 600, Material 3 sizes): Workout rows, filter groups, compact screen context, and dense card titles.
- **Body Large / Medium / Small** (Roboto 400, Material 3 sizes): Instructions, prescriptions, progress facts, and supporting metadata.
- **Label Large / Medium / Small** (Roboto 600, Material 3 sizes): Buttons, section labels, timer units, and route endpoints.

Use `sp` through the Material type scale. Allow text wrapping and vertical growth under larger system font sizes; never reduce type to preserve a fixed card height.

**The Condensed Emphasis Rule.** Barlow Semi Condensed belongs to display, headline, and title-large roles; instructions, metadata, labels, and controls stay in Roboto.

## Layout

The spatial model is phone-first, edge-to-edge, and vertically progressive. Compact screens use 20dp horizontal content padding, 12–24dp vertical rhythm, full-width cards, and a three-destination Material navigation bar: Today, Plan, Programs. Today orders content as next unfinished workout, 12-week route, then the current-week workout list. Deeper week and workout screens preserve that program → week → workout → movement orientation.

At widths below 720dp, keep the bottom navigation bar and single-column flow. At 720dp and above, replace the bottom bar with a Material navigation rail and let plan content use an adaptive grid with cards no narrower than 150dp. Do not merely stretch a phone card stack across a tablet.

Use `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` for the app shell and `windowInsetsPadding(WindowInsets.safeDrawing)` for full-screen onboarding and fallback states. Guided mode keeps status and navigation bars visible. IME, display cutout, and navigation insets must never cover fields, media controls, or the previous / Done / next bar.

Core rhythm: 4dp for close text relationships, 8–12dp within compact groups, 14–16dp between sibling controls, 20dp page gutters and emphasized-card padding, and 22–24dp between major sections.

**The Safe Frame Rule.** Every screen is edge-to-edge but all actionable and readable content remains inside safeDrawing insets; guided mode explicitly keeps system bars visible.

## Elevation & Depth

The system is flat by default. Resting depth comes from Material surface, surfaceVariant, primaryContainer, and tertiaryContainer tones rather than ornamental shadow. A 2dp primary outline may identify a selected card; soft dividers separate repeated rows. Native Material pressed, focused, and dialog elevation remains intact.

**The Tonal Elevation Rule.** Differentiate resting surfaces with Material surface and container tones; do not add arbitrary drop shadows.

## Shapes

The form language mixes softly rectangular equipment-like surfaces with circular status details. Training cards and the enlarged movement stage use 16dp corners; plan cards use the themed large shape (16dp); main buttons and timer controls use the medium shape (14dp); compact fields use 8dp; small containers use 12dp. The 28dp extra-large shape supports chips and other bounded capsules only.

Primary actions remain visibly rectangular even at 56dp height. Circles belong to icon buttons, completion checks, and week-route nodes. The three elastic-band strokes use 14dp, 10dp, and 7dp rounded lines; progress uses a 12dp rounded track with a coral position marker.

**The Bounded Corner Rule.** Cards stay within 12–16dp and primary controls use the 14dp medium shape; reserve circular geometry for icons, status markers, and route nodes.

## Components

### Buttons
- **Primary:** Full-width where it starts, resumes, finishes, or returns from a workout; minimum 56dp high, medium 14dp corners, filled primary color, direct verb-first label.
- **Tonal:** Timer and adjacent low-risk controls; minimum 48dp high. Timer copy reflects state: “Start 30s timer,” “Pause timer,” or “Resume timer.”
- **Icon:** Previous and next controls are 56×56dp in guided mode. Disabled endpoints remain visible. Every icon-only control has a specific TalkBack label.
- **States:** Use Material enabled, pressed, focused, and disabled states. Never indicate state through color alone.

### Cards / Containers
- **Training Card:** 16dp corners, 16dp internal padding, surface background, optional 2dp primary selection border.
- **Next Workout:** Primary-container background, 20dp internal padding, strong headline, factual duration/movement metadata, progress band, then one full-width Start or Resume action.
- **Program Card:** 16dp corners and 18dp padding. Selection uses primary-container fill, 2dp primary outline, “Current plan” copy, and a check icon.
- **Movement Stage:** Fills the flexible center of guided mode, uses a 16dp surfaceVariant frame, and shows media edge-to-edge or a quiet band mark with the prescription value centered.

### Progress and route
- **Progress Band:** A 12dp rounded track with animated cobalt fill and a coral marker. Supply `ProgressBarRangeInfo`; clamp the fraction to 0–1.
- **Week Band:** A 44dp-high route with 6dp rounded track segments, cobalt completed sections, coral current node, outlined future nodes, W1 and W12 endpoints, and a semantic “Week N of 12” description.
- **Resistance Band Mark:** Three crossing strokes in cobalt, coral, and mint. Use it at onboarding, empty media, and completion—not as ambient wallpaper.

### Navigation
Compact width uses Today, Plan, and Programs in a Material navigation bar. Expanded width uses the same destinations in a rail. Preserve System Back through week detail, workout preview, guided exit, onboarding plan choice, and dialogs. Detail screens do not retain main navigation merely to fill space.

### Guided workout behavior
Show one exercise only. The top row provides Exit, week/workout context, ordinal progress, and optional “Add demos.” The enlarged stage owns remaining space. Beneath it, show section, movement name, and prescription, then the single previous / centered Done / next bar. The center action expands; adjacent icon controls remain 56×56dp. Resume at the first incomplete movement and persist Done immediately. Final Done becomes “Finish workout,” then show a factual completion state.

**The One Movement Rule.** Guided sessions show one movement at a time with an enlarged stage, then one fixed-order previous / centered Done / next bar beneath it.

### Timers and media
Timed movements initialize stopped. The user starts, pauses, and resumes. At zero, persist completion, provide brief haptic feedback, and advance after a short acknowledgment. Repetition movements never simulate a timer.

Exercise video and images are optional enhancement. Empty or offline media falls back to the band mark and prescription value. “Add demos” may open the ExerciseDB connection dialog, but its API key is never required for plans, progress, timers, or completion. Explain storage and network use factually; offer “Not now.”

**The User-Started Timer Rule.** A timer never begins on entry or movement change; the user starts it, may pause and resume it, and completion advances only after the countdown reaches zero.

**The Media Is Enhancement Rule.** Plans, progress, timers, and guided completion never require an account, API key, or network; demos are optional and user-invoked.

### Fields, chips, and dialogs
Use Material OutlinedTextField for the optional API key with visible label, password masking, explicit Show/Hide semantics, inline error copy, and supporting privacy text. Program filters use Material FilterChip with a check icon plus selected color. Destructive resets require a Material dialog that names scope and irreversibility; cancel remains available.

### Accessibility and system behavior
Every actionable target is at least 48×48dp with 8dp separation; primary workout actions are at least 56dp. Provide TalkBack labels for icon-only controls, semantic progress values, non-color completion cues, and meaningful media descriptions. Support system font scaling without clipping. Honor reduced motion by replacing progress interpolation and movement transitions with an immediate state change or simple crossfade. Maintain strong contrast in both themes and keep system bars persistent.

### Copy style
Use short, calm, literal language. Lead controls with verbs; expose current state in labels; keep progress factual (“3 of 8 movements,” “Week 4 of 12”). Avoid motivational claims, health outcomes, punishment language, urgency, jargon, and invented social proof. Error messages say what happened and what the user can do next.

## Do's and Don'ts

### Do:
- **Do** place the next unfinished workout before route and current-week detail.
- **Do** use Material semantic roles so light and dark schemes remain coherent.
- **Do** keep every touch target at least 48×48dp and primary workout controls at least 56dp high.
- **Do** pair color with text, icon, shape, or progress semantics.
- **Do** preserve safeDrawing insets, persistent system bars, system Back, and TalkBack labels.
- **Do** write short, factual, action-led copy such as “Start workout,” “Pause timer,” and “Done.”

### Don't:
- **Don't** frame TwelveWeek as a website, landing page, analytics dashboard, or account-driven service.
- **Don't** gate plans, progress, timers, or workout completion behind sign-in, connectivity, or an API key.
- **Don't** auto-start timers or demos, and don't let media dominate the exercise name, prescription, or completion controls.
- **Don't** scatter floating cards, excessive pills, gradients, glass effects, or decorative shadows across the interface.
- **Don't** shrink workout controls below one-handed reach targets or hide them behind system bars.
- **Don't** use the elastic-band motif as generic wallpaper; it must communicate motion, route, progress, or completion.
