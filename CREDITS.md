# Third-party media credits

Most exercise demo media comes from two sources baked into the app's own
media pipeline and already covered by their own licenses/terms:

- [free-exercise-db](https://github.com/yuhonas/free-exercise-db) — public
  domain (Unlicense). Referenced by id (`freeExerciseDbId` in `programs/*.json`);
  images are fetched directly from that repo, never copied into this one.
- [wger](https://wger.de) — free, keyless public exercise database.
  Referenced by id (`wgerId`); fetched live from wger's own API.
- [ExerciseDB](https://exercisedb.dev) (via RapidAPI / Ascend API) —
  commercial, requires your own free API key (Settings → Add exercise
  demos). Never bundled into this repo — it's their product, not ours to
  redistribute.

## One-off hotlinks (`externalMediaUrl`)

A handful of exercises have no entry in any of the above but a verified,
individually-checked free match exists elsewhere. These are linked directly
(never downloaded into this repo) via the `externalMediaUrl` field on
`Exercise`, and credited here:

| Exercise | Source | Author | License |
|---|---|---|---|
| Jumping Jack | [Jumpingjacks.gif](https://commons.wikimedia.org/wiki/File:Jumpingjacks.gif), Wikimedia Commons | [Wensceslao](https://commons.wikimedia.org/wiki/User:Wensceslao) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| Diamond Push-up | [Diamondpushups1.jpg](https://commons.wikimedia.org/wiki/File:Diamondpushups1.jpg), Wikimedia Commons | [Erick76470](https://commons.wikimedia.org/wiki/User:Erick76470) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| Downward Facing Dog | [Downward-Facing-Dog.JPG](https://commons.wikimedia.org/wiki/File:Downward-Facing-Dog.JPG), Wikimedia Commons | [Iveto](https://commons.wikimedia.org/w/index.php?title=User:Iveto) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) |
| Crunch Floor | [Sit-ups or Crunch.gif](https://commons.wikimedia.org/wiki/File:Sit-ups_or_Crunch.gif), Wikimedia Commons | [Zimmermanns](https://commons.wikimedia.org/wiki/User:Zimmermanns) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) |

CC BY-SA requires attribution and share-alike for derivatives; this table
is that attribution. No modifications were made to the linked file.
