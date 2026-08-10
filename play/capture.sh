#!/usr/bin/env bash
# WM Keyboard — Play Store screenshot capture rig.
# Run from the repo root:  bash play/capture.sh
# Captures land in play/screencaps/. Re-run any time; existing shots are
# overwritten only when you retake them.
set -u

PKG=com.wasimaster.wmkeyboard
OUT="$(cd "$(dirname "$0")" && pwd)/screencaps"
mkdir -p "$OUT"

adb get-state >/dev/null 2>&1 || { echo "No device. Plug in + authorize ADB."; exit 1; }
echo "Device: $(adb shell getprop ro.product.model | tr -d '\r')"

# ---------------------------------------------------------------- helpers --
snap() { # snap <name>  -> play/screencaps/<name>.png
  adb exec-out screencap -p > "$OUT/$1.png" && echo "  ✔ saved $1.png"
}

type_text() { # type_text "ami valo achi"   (types into the focused field)
  adb shell input text "$(printf %s "$1" | sed 's/ /%s/g')"
}

demo_on() { # clean status bar: 1:00, full battery, wifi, no notifications
  adb shell settings put global sysui_demo_allowed 1
  adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0100 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
}

demo_airplane() { # status bar shows the airplane icon (for the offline slide)
  adb shell am broadcast -a com.android.systemui.demo -e command network -e airplane show >/dev/null
}

demo_off() { adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null; }

shot() { # shot <name> <<'EOF' ...instructions... EOF
  local name=$1; shift
  echo; echo "── $name ─────────────────────────────────────────────"
  cat
  while true; do
    read -r -p "  [Enter]=capture  s=skip  q=quit > " k
    case "$k" in
      s) echo "  skipped"; return ;;
      q) demo_off; exit 0 ;;
      *) snap "$name"
         read -r -p "  r=retake, anything else continues > " k2
         [ "$k2" = "r" ] || return ;;
    esac
  done
}

# ------------------------------------------------- find the preview screen --
echo
echo "Activities in $PKG (look for a preview/demo screen you can 'am start'):"
adb shell dumpsys package "$PKG" | grep -iE "$PKG/[a-zA-Z0-9_.]*(preview|demo|test)" | sort -u || true

demo_on
echo
echo "Status bar is now in demo mode (1:00, 100%, no notifications)."
echo "Open the WM Keyboard app's test field ('Type here') unless a step says otherwise."

# ------------------------------------------------------------------ shots --
shot kb-english-dark <<'EOF'
  English QWERTY, dark/AMOLED theme, number row ON, toolbar visible.
  Focus the test field so the keyboard is up. Empty field.
EOF

shot kb-bengali-typing <<'EOF'
  Switch to Bengali (Avro phonetic). Focus the test field.
  The script will now type 'ami valo achi' for you after you press Enter —
  capture happens 1s later so the suggestion strip shows আমি ভালো আছি.
EOF
# retype + snap with suggestions up (belt and braces)
type_text "ami valo achi"; sleep 1; snap kb-bengali-typing

shot toolbox-grid <<'EOF'
  Open the toolbox (grid icon in the toolbar) so the full tool grid shows.
EOF

shot clipboard-panel <<'EOF'
  Open the clipboard panel. Ideally have: a pinned clip, a screenshot
  thumbnail, and an OTP chip (send yourself a code first if convenient).
EOF

shot emoji-search <<'EOF'
  Emoji panel with search open; query 'party' (or 'হাসি' for the bn set).
EOF

shot voice-panel <<'EOF'
  Voice input panel (Whisper engine selected if the chooser shows it).
EOF

shot ai-panel <<'EOF'
  AI writing panel (any provider configured, or the tone/rewrite actions).
EOF

shot tools-calculator <<'EOF'
  Calculator / converter tool open with a sample expression, e.g. 12*7.5.
EOF

shot theme-light <<'EOF'
  Any light theme on the English keyboard (for the theme fan).
EOF

shot theme-photo <<'EOF'
  A photo-background theme (for the theme fan).
EOF

demo_airplane
shot kb-airplane <<'EOF'
  Status bar now shows the AIRPLANE icon. English keyboard up, any panel
  that proves offline power (AI panel or voice panel is ideal).
EOF

demo_off
echo
echo "Done. Shots are in play/screencaps/ — tell Claude and they'll be pulled"
echo "into the artwork pipeline."
