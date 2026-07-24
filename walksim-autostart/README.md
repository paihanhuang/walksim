# walksim-autostart (Magisk module) — BFU-aware

Resumes the WalkSim walk after a device boot. Companion to the permanent `init_boot` root flash
(which auto-restores Magisk → Zygisk → Vector → `stephook`); this module adds the one piece that
can't ride the boot image: starting the walk. Ported from pikmin2 and made **BFU-aware**.

See ADR `docs/sdlc/walk-simulator/adr-0001-adopt-remote-control-port-autostart.md` and the spec
`docs/sdlc/walk-simulator/autostart-port-spec.md`.

## Why it polls (BFU)

On this device the lockscreen gates **CE-encrypted storage** at boot. Until the **first unlock**,
`com.pikmin.walksim` *and* `com.nianticlabs.pikmin` (both CE, not `directBootAware`) are
unavailable — so nothing can start, and nothing could be credited even if it did (Pikmin isn't
running). Boot scripts can't get past that lock via `wm dismiss-keyguard`/`input`. So `service.sh`
**polls `cmd package resolve-activity` until the app is available** (your first swipe), then
launches it. Making WalkSim `directBootAware` was rejected in ADR 0001 — Pikmin is CE-locked too,
so it would credit nothing until unlock anyway.

**Net flow:** reboot → **one swipe** → walk auto-starts. Foreground Pikmin to see the landing count
(the accepted manual step; the GMS Weekly-Challenge counter doesn't need it).

## What it does (`service.sh`, late_start, root)

1. Waits for `sys.boot_completed`.
2. Polls `cmd package resolve-activity com.pikmin.walksim/.MainActivity` until it resolves (CE
   unlocked), capped at ~2 h.
3. Wakes + holds the screen, re-asserts `mock_location` + `ACCESS_FINE_LOCATION`.
4. `am start -n com.pikmin.walksim/.MainActivity --ez autostart true` → the app replays the
   last-used preset via `autostartSpec` (SharedPreferences; defaults to All-areas).

Log: `/data/local/tmp/walksim-autostart.log`.

## Install (device `/data/adb` is write-locked by Shamiko → use the daemon)

```sh
tmp=$(mktemp -d); mkdir -p "$tmp/META-INF/com/google/android"
cp walksim-autostart/module.prop walksim-autostart/service.sh "$tmp/"
printf '#MAGISK\n' > "$tmp/META-INF/com/google/android/updater-script"
cat > "$tmp/META-INF/com/google/android/update-binary" <<'EOF'
#!/sbin/sh
umask 022
OUTFD=$2; ZIPFILE=$3
mount /data 2>/dev/null
. /data/adb/magisk/util_functions.sh
install_module
exit 0
EOF
( cd "$tmp" && zip -r /tmp/walksim-autostart.zip . >/dev/null )
adb push /tmp/walksim-autostart.zip /data/local/tmp/
adb shell su -c 'magisk --install-module /data/local/tmp/walksim-autostart.zip'
adb reboot
```
