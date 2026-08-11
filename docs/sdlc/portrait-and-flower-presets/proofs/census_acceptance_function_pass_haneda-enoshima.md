# Proof — in-Pikmin big-flower census, Haneda Airport + Enoshima (R2/R3)

**Date:** 2026-08-11 · **Device:** Pixel 7 Pro (cheetah), Pikmin Bloom **v150.0**, account *wasai* (Lv 59).
**Method:** the v1.9 Ueno census protocol — teleport the avatar with walk-sim `hold_s=1` (live re-pointable, so
no STOP → real-GPS detour between hops), **dwell 20 s** so big flowers finish loading, screenshot, count
*blooming big flowers* (large blooms on stalks) per frame; mushrooms and the planted ground carpet excluded.
Frames as 4-up montages in `census/`.

**Counting is per-frame presence, not per-flower position.** Pikmin exposes no flower coordinates and the tilted
~300 m view cannot be read back to a position, so a "site" is a sampled point observed to bear big flowers.
Sample spacing (~400–700 m) is matched to the 250 m harvest reach, so touring the sites covers their flowers.

## Haneda Airport vicinity — 16 points, 20 flowers, **1.25 / frame**

| # | lat,lng | area | flowers |
|---|---|---|---|
| 1 | 35.5494,139.7857 | Terminal 1 | 1 |
| 2 | 35.5533,139.7876 | Terminal 2 | 1 |
| 3 | 35.5449,139.7699 | Terminal 3 / Intl | 1 |
| 4 | 35.5570,139.7690 | Shin-Seibijo | 0 |
| 5 | 35.5533,139.7452 | Tenkubashi | 2 |
| 6 | 35.5468,139.7462 | Anamori-inari | 3 |
| 7 | 35.5490,139.7440 | Haneda Innovation City | 3 |
| 8 | 35.5525,139.7405 | Otorii | 2 |
| 9 | 35.5455,139.7520 | Anamori south / riverside | 2 |
| 10 | 35.5510,139.7530 | Haneda 4-chome | 1 |
| 11 | 35.5570,139.7480 | Haneda Asahi-cho | 1 |
| 12 | 35.5430,139.7620 | T3 south (avatar in water) | 1 |
| 13 | 35.5480,139.7760 | mid-airport apron | 0 |
| 14 | 35.5560,139.7800 | T2 north | 0 |
| 15 | 35.5600,139.7560 | Seibijo west | 0 |
| 16 | 35.5440,139.7440 | Kojiya south (avatar in water) | 2 |

**Finding:** the terminals are nearly barren (0–1/frame); the **landside west** (Anamori-inari, HICity,
Tenkubashi, Otorii) carries almost everything. Haneda is a *low*-density area — ~21% of Ueno's 5.9/frame.

Preset survey = the 10 flower-bearing points on land (12 and 16 excluded: the avatar stood in the river/bay and
the flowers seen were across water).

## Enoshima / Katase-Kaigan — 12 points, 16 flowers, **1.33 / frame** (1.78 excluding the 3 pure-sea samples)

| # | lat,lng | area | flowers |
|---|---|---|---|
| 1 | 35.2989,139.4803 | Enoshima island shrine | 3 |
| 2 | 35.2965,139.4795 | Iwaya, island south | 2 |
| 3 | 35.3020,139.4810 | Benten bridge | 2 |
| 4 | 35.3060,139.4830 | Katase-Kaigan beach east | 3 |
| 5 | 35.3095,139.4838 | Katase-Enoshima station | 1 |
| 6 | 35.3080,139.4760 | (open sea) | 0 |
| 7 | 35.3110,139.4700 | (open sea) | 0 |
| 8 | 35.3130,139.4880 | Katase | 3 |
| 9 | 35.3060,139.4910 | Koshigoe | 1 |
| 10 | 35.3140,139.4790 | Katase inland | 1 |
| 11 | 35.3040,139.4700 | (open sea) | 0 |
| 12 | 35.3170,139.4830 | Fujisawa / Katase 1-chome | 0 |

Preset survey = the 8 flower-bearing points. Enoshima is the denser of the two new areas.

## Honest caveats

- Both areas are **well below** the project's existing city presets (Ueno 5.9/frame). Haneda was requested by
  name; Enoshima was chosen as the best available beach candidate and measured to confirm it beat Haneda.
- Tilted ~300 m view undercounts far/occluded flowers (same caveat as the v1.9 census).
- Big flowers bloom and wither with player planting, so counts are a 2026-08-11 snapshot. The *sites* (Niantic
  wayspots) are stable, which is what the tour encodes.
- Counts are single-observer reads of a game render; ±1 per frame is expected.
