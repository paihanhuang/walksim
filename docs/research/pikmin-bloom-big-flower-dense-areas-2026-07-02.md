# Highest big-flower-density areas for a ~20 km Pikmin Bloom harvest route

Date: 2026-07-02 · Method: deep-research harness (fan-out search → adversarial verification → cited synthesis; 100 agents, ~2.3M tokens) · Owner: researcher

## The honest headline first

**No direct data exists.** Niantic publishes no big-flower heatmap, and — critically — **not a single Pikmin Bloom community/player report of a specific flower-dense hotspot survived adversarial verification.** Several concrete candidate claims were *refuted* 0-3 (MagFone's "Central Park NYC #1", Zaragoza Spain "most PokéStops in one place", "UK is the 3rd market"). So this entire ranking rests on **three indirect but verified proxies**, not on observed in-game big-flower counts. Treat it accordingly.

## The three verified proxies (all HIGH confidence, direct-mechanic where noted)

1. **Big flowers spawn at fixed Niantic Wayspots** — the *same* curated POI database that generates PokéStops/Gyms (Pokémon GO) and Portals (Ingress). So PokéStop/Gym density is a legitimate proxy. *Caveat:* Pokémon GO applies S2-L17 cell selection (~1 stop/cell), so stop counts **saturate and under-discriminate exactly the densest cores.** (pikminwiki Big_Flower; Niantic Wayfarer; Wikipedia)
2. **A big flower only *blooms* (becomes harvestable) after ~300 flowers accumulate within its 40 m radius**, and flowers are planted only along a moving player's path. So harvestable density = fixed POI density × cumulative foot-traffic. High-pedestrian, high-active-player areas win. (pikminwiki Big_Flower)
3. **Japan is overwhelmingly the #1 market** — 43% of launch downloads, 53% of launch spend, **~68% of lifetime revenue**; US 2nd (18%), Taiwan 3rd (3%). Japanese neighborhoods are the default priority. (Sensor Tower / AppMagic via PocketGamer.biz)

**Rejected proxy:** residential population density — the globally densest residential districts are in Egypt/Bangladesh/Nigeria (non-markets), and standard density lists *undercount* the Japanese/Taiwanese commercial wards that actually matter. Not used.

## Ranked top 10 (excludes your existing presets)

### Tier 1 — evidence-grounded
| # | Area | Start pin | Confidence | Why |
|---|---|---|---|---|
| 1 | **Osaka — Minami / Dotonbori-Shinsaibashi** | 34.6687, 135.5013 | **HIGH** | Japan's busiest non-Tokyo pedestrian retail cluster (~110 M pedestrians/yr, CBRE); Shinsaibashi-suji = ~600 m covered arcade of 150-180+ shops. #1 market, not excluded. Best-evidenced single answer; absorbs a full 20 km loop. |
| 2 | **Osaka — Namba** (America-mura, Nipponbashi/Den-Den-Town) | 34.6659, 135.5020 | **HIGH** | Same Minami mega-cluster, immediately south of #1 — could be one combined loop. |
| 3 | **Osaka — Kita/Umeda + Midosuji corridor** | 34.7025, 135.4959 | MEDIUM | Second major Osaka retail concentration; Midosuji links it to Minami for an extended loop. |
| 4 | **Tokyo — Akihabara** (+Kanda/Jimbocho) | 35.6984, 139.7730 | MEDIUM | Dense PokéStops, ~19 gyms within 500 m of the station; #1 market; **not** among your excluded Tokyo presets. Best non-excluded Tokyo option. |
| 5 | **Seoul — Myeong-dong** (+Namdaemun/Dongdaemun) | 37.5636, 126.9848 | MEDIUM | Strongest non-Japan foot-traffic proxy (~2 M/day floating population); tempered by lower Korean adoption. |

### Tier 2 — directional only (LOW; not verified at neighborhood level)
| # | Area | Basis |
|---|---|---|
| 6 | **Hong Kong — Mong Kok / Causeway Bay** | Google-Trends interest 100 (search interest, not player density) |
| 7 | **Taipei — Ximending** | Taiwan = #3 revenue market (excludes your Xinyi preset) |
| 8 | **Singapore — Orchard Rd / Bugis** | Trends interest 62 |
| 9 | **Nagoya — Sakae** or **Fukuoka — Tenjin** | Japan-dominance; major non-excluded metros |
| 10 | **Kyoto — Kawaramachi/Gion** or **Yokohama — Minato Mirai** | Japan-dominance; dense tourist foot-traffic |

## Bottom line
**Osaka's Minami cluster (#1-2) is by far the best-evidenced pick** and the strongest single recommendation. **Akihabara (#4)** is the best non-excluded *Tokyo* option. Ranks 5-10 are extrapolations from adoption + general urban density, **not** verified big-flower/waypoint counts — verify any of them on-device (a settled recon) before trusting.

## Sources
pikminwiki.com/Big_Flower · en.wikipedia.org/wiki/Pikmin_Bloom · wayfarer.nianticlabs.com · pocketgamer.biz (Pikmin Bloom revenue/adoption, 3 articles) · activeplayer.io/pikmin-bloom · japan.travel Dotonbori-Shinsaibashi · en.wikipedia.org/wiki/Myeong-dong · pogomap.info (crowd-sourced, incomplete) · magfone.com (Akihabara waypoint density; its NYC ranking was refuted)
