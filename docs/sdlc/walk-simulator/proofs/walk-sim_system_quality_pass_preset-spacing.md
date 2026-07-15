# Proof — per-preset lane-spacing optima (v1.7)

Measured on the **live Overpass graph** (env-gated route diagnostic, 2026-07-02) for the OPEN 20 km harvest
sweep. Selection rule = **widest spacing that keeps harvest coverage ≥ 0.95** (AC-24a), which maximizes
distinct swept area (≈ maxR²·coverage). `coverage` = fraction of inner-disc nodes within the 500 m harvest
reach of the route; `revisit` = fraction of resampled points re-walking ground swept ≥ 500 m earlier.

## Shinjuku / Okubo — LatLng(35.6975, 139.7005) · 12,382 nodes / 2.5 km fetch (~630/km², dense alley grid)
| spacing m | maxR m | revisit | coverage |     |
|-----------|--------|---------|----------|-----|
| 800       | 1859   | 0.160   | 0.976    |     |
| **850** (old default) | 1859 | 0.165 | **0.942** | ← GAP + high deadhead |
| 950       | 1925   | 0.120   | 0.906    | ← GAP |
| 1000      | 1859   | 0.120   | 0.932    | ← GAP |
| **1100** (chosen) | 1941 | **0.066** | **0.964** | widest gap-free; beats 850 on all axes |
| 1200      | 2039   | 0.125   | 0.882    | ← GAP |

## Roppongi crossing — LatLng(35.6628, 139.7314) · 8,112 nodes / 2.5 km fetch (~410/km²)
| spacing m | maxR m | revisit | coverage |     |
|-----------|--------|---------|----------|-----|
| **850** (old default) | 2089 | 0.044 | **0.948** | ← GAP |
| 1000      | 1975   | 0.060   | 0.992    |     |
| 1100      | 2007   | 0.026   | 0.989    |     |
| **1200** (chosen) | 2184 | **0.018** | **0.966** | widest gap-free; best distinct-area index 4.61 |
| 1300      | 2194   | 0.035   | 0.884    | ← GAP |

**Why the old ≤1000 m (2×reach) cap was wrong for dense cores:** on a dense graph the spiral's connectors
wiggle through the grid, so **connector deadhead — not ring spacing — bounds coverage**. Tightening below the
optimum *raises* revisit and *lowers* coverage (Shinjuku 850 → revisit 0.165 / cov 0.942). Density does NOT
predict the optimum (Roppongi is sparser than Shinjuku yet wants a *wider* 1200 m), so spacing is measured
per-city, not derived from a formula. Only the two measured cities override the 850 m default.

## Xinyi / Taipei 101 — LatLng(25.0339, 121.5645) · 6,360 nodes / 2.5 km fetch (~324/km²) · **10 km budget**
| spacing m | maxR m | revisit | coverage |     |
|-----------|--------|---------|----------|-----|
| 700       | 1147   | 0.121   | 1.000    | high deadhead |
| **800** (chosen) | 1366 | **0.022** | **0.992** | reaches the 10 km max radius, cleanest |
| 850 (old default) | 1366 | 0.047 | 0.978 |     |
| 950       | 1366   | 0.069   | 0.993    | same maxR as 800, more deadhead |
| 1050      | 1748   | 0.140   | 0.815    | ← GAP |

**Route length also sets the optimum, not just density.** At 10 km the spiral is BUDGET-limited (reaches only
~1366 m), so spacings 800-950 all reach the same radius — the tiebreak is deadhead, won by the *tighter* 800 m.
Wider spacing (≥1050) just opens gaps without extending reach. Contrast the same pin at 20 km (spiral reaches
~1930 m, optimum ~950 m). So a shorter route wants a *tighter* spacing. 800 m still beats the 850 m default at
both lengths (10 km areaIx tie + lower deadhead; 20 km areaIx 3.59 vs 3.30). Chosen 800 m for the user's 10 km
Xinyi target.

**On-device (Pixel XL, Android 10, 2026-07-02):** both routes swept hyper-dense flower fields; Roppongi
crossing is flower-dense at the start pin; the LSPosed step-hook credited steps (`SD deliver … on main`). The
UI picker → Roppongi → START ran at 35.66294, 139.73142, playing, spacing 1200 (same intent path as the
proven adb `spacing_s=1200` run). Xinyi 10 km @ 800 ran at 25.033, no fallback/OOM, avatar amid a hyper-dense
flower field with big lily/white/daisy flowers at the 101 core; Pikmin step count climbed 434 → 554 live.

## All presets — tuned (spacing, length), and WHY length matters as much as spacing (2026-07-03)

Sweeping spacing × length on each live graph showed a fixed 20 km is WRONG: `sweepRoute` retraces once the
route exceeds an area's fresh-street capacity, so the optimum length varies per preset. Rule: the LONGEST
route that stays gap-free (cov ≥ 0.95) with low re-walk (revisit ≤ ~0.10) = most distinct flowers, no
back-and-forth. Selecting a preset now sets the duration to `routeLengthKm / speed` so the pair is applied
together (fixes the earlier duration-blindness where Okubo 1100 / Roppongi 1200 gapped at the 4.7 km default).

| preset | spacing | length | cov | revisit | note |
|---|---|---|---|---|---|
| Shibuya | 850 | 6 km | 1.00 | 0.00 | retraces hard past 6 km (rev 0.28 @ 9 km) |
| Okubo | 1100 | 20 km | 0.96 | 0.07 | dense alley grid |
| Roppongi | 1200 | 20 km | 0.97 | 0.02 | |
| Azabudai | 1100 | 20 km | 0.98 | 0.03 | |
| Chuo-ku | 850 | 12 km | 1.00 | 0.11 | 12 km @ 850 beats 20 km @ 1100; ~11% re-walk is the graph's floor |
| ~~Odaiba~~ DROPPED | — | — | — | ~0.35 | removed (user 2026-07-04): sparse bay net re-walks ≥33% at ANY length, can't meet AC-24(b) |
| Ueno | 850 | 16 km | 1.00 | 0.07 | densest measured, clean to 16 km |
| Osaka Minami/Namba | 1000 | 20 km | 0.98 | 0.00 | |
| Xinyi | 800 | 10 km | 0.99 | 0.02 | user's 10 km target |
| Seoul | 850 | 12 km | 1.00 | 0.09 | retraces past 12 km |

Also newly measured/retuned SPACING this round (not just length): Azabudai 850→1100, Osaka Minami/Namba
850→1000 (rows above).

**The regression this fixes (measured):** at the app's ~4.7 km default duration (60 min × 1.3 m/s), the v1.7
wide spacings GAP, while 850 is perfect:
| @ 4.7 km | Okubo | Roppongi |
|---|---|---|
| tuned spacing (1100/1200) | cov **0.950** GAP | cov **0.895** GAP |
| plain 850 | cov 1.000 | cov 1.000 |
Option 1 fixes it: selecting these presets now sets 20 km, where 1100/1200 are optimal.

**No regression:** every preset's tuned (spacing, length) is gap-free (or its graph's clean floor) AND ≥ its
old default-tap coverage.

**AC-24(b) conformance — honest accounting (not spun):** AC-24(b) requires ≥80% new ground (revisit ≤0.20).
- Chuo-ku 0.11 and all others **satisfy** AC-24(b) (≤0.20).
- **Odaiba — DROPPED (user 2026-07-04), breach RESOLVED.** Its sparse bay street net (~100 nodes/km²) made
  AC-24(b) (≤0.20 revisit) unsatisfiable at any length (0.34-0.42 across 4-10 km). Rather than grant a
  graph-limited exemption (the AC-3 pattern), the user removed Odaiba as a harvest preset — so there is no
  Odaiba route and the AC-24(b) breach is moot. All remaining 10 presets satisfy AC-24(b).
