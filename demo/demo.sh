#!/bin/bash
# A guided tour of the app, driven over adb with touch dots showing. It only browses: it never places
# orders, sends messages, changes status or opens account settings.
#
#   demo/demo.sh                  run the tour on the connected phone
#   demo/demo.sh out.mp4          run it and save a screen recording
set -u
PKG=com.wfmarket.app
OUT=${1:-}
REC=""
TOUCHES=$(adb shell settings get system show_touches | tr -d '\r')

cleanup() {
    adb shell settings put system show_touches "${TOUCHES/null/0}"
    stop_recording
}

stop_recording() {
    [ -n "$REC" ] || return 0
    adb shell pkill -INT screenrecord
    wait "$REC"
    REC=""
    sleep 1
    adb pull /sdcard/wfm_demo.mp4 "$OUT" >/dev/null && adb shell rm /sdcard/wfm_demo.mp4
    echo "Saved $OUT"
}
trap cleanup EXIT

say() { printf '\n== %s\n' "$1"; }

# Taps the first on-screen element with this exact label. Stops the tour if the app isn't in front, so
# a stray tap can never land in another app.
tap() {
    python3 - "$1" <<'PY' || exit 1
import re, subprocess, sys
run = lambda c: subprocess.run(c, shell=True, capture_output=True, text=True).stdout
if "com.wfmarket.app" not in run("adb shell dumpsys window | grep mCurrentFocus"):
    sys.exit("The app isn't in the foreground, so the tour stopped.")
run("adb shell uiautomator dump /sdcard/wfm_ui.xml")
xml = run("adb exec-out cat /sdcard/wfm_ui.xml")
run("adb shell rm /sdcard/wfm_ui.xml")
for node in re.findall(r"<node [^>]*?>", xml):
    text = re.search(r' text="([^"]*)"', node).group(1)
    desc = re.search(r'content-desc="([^"]*)"', node).group(1)
    if sys.argv[1] in (text, desc):
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        run(f"adb shell input tap {(a + c) // 2} {(b + d) // 2}")
        sys.exit(0)
print(f"(couldn't find '{sys.argv[1]}', skipping)")
PY
}

scroll_down() { adb shell input swipe 540 1800 540 800 450; sleep "${1:-1.5}"; }
scroll_up() { adb shell input swipe 540 800 540 1800 450; sleep "${1:-1}"; }
swipe_carousel() { adb shell input swipe 900 850 250 850 400; sleep 1.8; }
type_slowly() {
    for ((i = 0; i < ${#1}; i++)); do
        c=${1:$i:1}
        [ "$c" = " " ] && c=%s
        adb shell input text "$c"
    done
}

adb shell settings put system show_touches 1
# Typing over adb leaves the phone in key mode, which would open the search bar on launch. A touch resets it.
adb shell input swipe 540 1200 545 1205 60
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity" >/dev/null
if [ -n "$OUT" ]; then
    sleep 0.5
    adb shell screenrecord --size 720x1600 --bit-rate 8000000 --time-limit 170 /sdcard/wfm_demo.mp4 &
    REC=$!
fi
sleep 4

say "Home: the latest Prime Access and the current Prime Resurgence"
swipe_carousel; swipe_carousel
# Tapping a card at the side brings it into focus.
adb shell input tap 850 850; sleep 1.8
swipe_carousel
say "Latest orders from players, sell and buy"
tap Buy; sleep 1.5
tap Sell; sleep 1
scroll_down; scroll_up

say "Searching the catalogue"
adb shell input tap 540 230; sleep 1
type_slowly "nidus prime"; sleep 1.5
tap "Nidus Prime Set"; sleep 3

say "Item page: live orders"
scroll_down; scroll_up
say "Item page: price history charts"
tap Statistics; sleep 3
scroll_down 2; scroll_down 2; scroll_up 0.8; scroll_up 0.8
say "Item page: description, tax, wiki and set parts"
tap Info; sleep 2
scroll_down; scroll_up
adb shell input keyevent 4; sleep 1.5

say "Auctions: rivens, liches and sisters"
tap Auctions; sleep 3
scroll_down
tap Popular; sleep 3
adb shell input tap 540 800; sleep 3
scroll_down
adb shell input keyevent 4; sleep 1.5

say "Messages"
tap Messages; sleep 2

say "Ducanator: best ducats per platinum"
tap Ducanator; sleep 3
scroll_down
tap Day; sleep 2.5

say "Profile and settings"
tap Profile; sleep 3
tap History; sleep 2
tap Settings; sleep 2
scroll_down 2
adb shell input keyevent 4; sleep 1.5

say "Back home"
tap Market; sleep 2.5
stop_recording
say "Tour finished"
