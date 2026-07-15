# Proof — Weekly-Challenge "Step Challenges" picks up walksim injection

- **Level/aspect:** acceptance · function
- **Result:** PASS
- **Date:** 2026-07-13
- **Device:** Pixel 7 Pro `cheetah`, Android 17, Vector (LSPosed) module `com.pikmin.stephook` (rebuilt this session)
- **Account:** Palantir (shown as "You" in the group Step Challenge "Walk 100000 steps!")
- **Walk:** Xinyi, Taipei preset (25.03390, 121.56450), speed 1.31 m/s, `PaceProvider playing=1`, ~100 steps/min

## Requirement verified
User directive (verbatim): *"verify your solution by actually checking the step counts shown in 'Step Challenges' of 'Weekly Challenges', and make sure that it does pick up walksim's injection. otherwise dont claim work done."*

## Procedure
1. Clean reboot → confirmed module auto-loads in **both** processes at boot:
   `StepHook: loaded in com.nianticlabs.pikmin (feeds sensor type=18)` and
   `StepHook: loaded in com.google.android.gms (feeds sensor type=19)` → `StepInjector: counter base=0` → `attach type=19 LocalSensorAdapter$ConvertingListenerKitKat`.
2. Started Xinyi walk from the app's own foreground UI (FGS-`location` cannot start from a background/`su` trigger on Android 14+).
3. Read the Step-Challenge "You" value from *Weekly Challenges → two-people tab → scroll → Step Challenges*, refreshing via a Pikmin restart (the on-screen number is server-synced/cached and only refreshes on re-fetch).

## Result — "You" climbs monotonically; all other participants flat

| time  | さいり | hh   | Tra Tra | **You**   | tal   | group total |
|-------|------|------|---------|-----------|-------|-------------|
| 11:31 | 4993 | 5864 | 6800    | **12695** | 16495 | 46847       |
| 11:54 | 4993 | 5864 | 6800    | **12923** | 16495 | 47075       |
| 12:03 | 4993 | 5864 | 6800    | **13740** | 16495 | 47892       |

Only "You" changed (**+1045** net); every group-total delta equals the "You" delta exactly → the increase is uniquely attributable to walksim's injected steps.

## Screenshots (this directory)
- `stepchallenge_acceptance_function_baseline_You12695_1131.png`
- `stepchallenge_acceptance_function_after_You12923_1154.png`
- `stepchallenge_acceptance_function_after_You13740_1203.png`

## Notes
- Root cause + fix: see `../design.md` §"Weekly-Challenge fix (2026-07-13)".
- The landing-page daily count and the Step-Challenges count are **decoupled** (per user); the landing page is not a proxy. This proof reads the challenge value directly.
- Credited portion is bounded by Google Fit's server-side aggregation/validation + sync lag; the value nonetheless climbs reliably.
