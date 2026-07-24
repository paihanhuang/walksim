#!/system/bin/sh
# walksim-autostart — resume the WalkSim walk after every boot.
#
# Runs in Magisk's late_start "service" context (root). The injection stack
# (Magisk -> Zygisk -> Vector -> stephook) auto-restores from the permanently-flashed init_boot;
# this module adds the one thing that can't ride along: (re)starting the walk.
#
# BFU-aware: this device gates CE storage behind the lockscreen at boot, so the WalkSim app AND
# Pikmin (both CE-encrypted, not directBootAware) are unavailable until the FIRST unlock. Nothing
# can start — nor be credited — before then. So we POLL until the app resolves, i.e. until you
# swipe once after boot, then launch it. Net flow: reboot -> one swipe -> walk auto-starts
# (then open Pikmin to credit — Pikmin foreground is the accepted manual step).
#
# Launching the ACTIVITY (not the location FGS) is the only legal FGS start on Android 14+;
# MainActivity reads `--ez autostart true` and replays the last-used preset.

LOG=/data/local/tmp/walksim-autostart.log
PKG=com.pikmin.walksim
ACT="$PKG/.MainActivity"
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }

# 1. Wait for boot to complete.
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
log "boot_completed; polling for CE unlock (first swipe)"

# 2. Poll until the app's components are available (CE unlocked). Cap ~2h so a never-unlocked
#    phone doesn't keep the service alive forever.
i=0
until cmd package resolve-activity --brief "$ACT" 2>/dev/null | grep -q "$PKG/"; do
  i=$((i + 1))
  if [ "$i" -ge 1440 ]; then
    log "gave up after ~2h waiting for unlock"
    exit 0
  fi
  sleep 5
done
sleep 3 # let the unlock settle
log "app available after ~$((i * 5))s (device unlocked)"

# 3. Keep the screen on (walk survives doze) + re-assert injection prereqs.
input keyevent KEYCODE_WAKEUP
svc power stayon true
appops set "$PKG" android:mock_location allow
pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null

# 4. Launch the controller with the autostart flag.
am start -n "$ACT" --ez autostart true >> "$LOG" 2>&1
log "am start issued (autostart=true)"

# 5. Record whether the walk engaged.
sleep 12
log "pace: $(content query --uri content://$PKG.pace/current 2>/dev/null)"
