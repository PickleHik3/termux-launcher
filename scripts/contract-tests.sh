#!/usr/bin/env bash
# The contract tests: the source scans, generated docs and registry invariants that a change in any
# package can break (an import cycle, a new overlay, a declaration without phrases or an upgrade
# fate, a stale settings corpus). Run them before every handoff and every merge; a scoped test run
# alone misses them.
#
#   scripts/contract-tests.sh [--regenerate] [--build] [PATTERN...]
#
#   --regenerate  rewrite the settings corpus and reference first (after adding or changing a
#                 declaration), then check
#   --build       also run :app:assembleDebug
#   PATTERN       extra :app test filters, e.g. 'com.termux.app.tiling.*'
#
# Prints the failing classes and exits with Gradle's code.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
if [ ! -f local.properties ] && [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
fi

# The one list. A test belongs here when it guards the whole tree, not one feature.
APP_CONTRACTS=(
    com.termux.app.window.WindowSourceRulesTest
    com.termux.app.window.LayeredGlassSourceRulesTest
    com.termux.app.place.layout.LayoutSourceRulesTest
    com.termux.app.chrome.OverlayExitStatedTest
    com.termux.app.chrome.TerlBackOrderSourceTest
    com.termux.app.look.OneLookSourceTest
    com.termux.app.activities.SettingsDoorSourceTest
    com.termux.app.wall.WallDisplaySeamSourceTest
    com.termux.app.wall.WallHomeSeamSourceTest
    com.termux.app.terminal.AgentScreenRulesTest
    com.termux.app.settings.SettingsCorpusTest
    com.termux.app.settings.SettingsReferenceTest
    com.termux.app.settings.ListenerSettingsHaveAReaderTest
    com.termux.app.fragments.settings.MigratedKeysReachTheRegistryTest
    com.termux.app.fragments.settings.SettingsRegistryScreensTest
    com.termux.app.upgrade.TerlUpgradeFatesTest
    com.termux.app.terminal.io.ExtraKeyEligibilityTest
    com.termux.launcherctl.LauncherToolRegistryTest
)
SHARED_CONTRACTS=(
    'com.termux.shared.termux.settings.registry.*'
)

regenerate=0
build=0
patterns=()
for arg in "$@"; do
    case "$arg" in
        --regenerate) regenerate=1 ;;
        --build) build=1 ;;
        *) patterns+=("$arg") ;;
    esac
done

app_args=()
for t in "${APP_CONTRACTS[@]}" "${patterns[@]}"; do app_args+=(--tests "$t"); done
shared_args=()
for t in "${SHARED_CONTRACTS[@]}"; do shared_args+=(--tests "$t"); done

if [ "$regenerate" = 1 ]; then
    REGENERATE_SETTINGS_CORPUS=1 REGENERATE_SETTINGS_REFERENCE=1 ./gradlew -q :app:testDebugUnitTest \
        --tests com.termux.app.settings.SettingsCorpusTest \
        --tests com.termux.app.settings.SettingsReferenceTest > /dev/null 2>&1
    echo "regenerated the settings corpus and reference (commit them)"
fi

tasks=(:termux-shared:testDebugUnitTest "${shared_args[@]}" :app:testDebugUnitTest "${app_args[@]}")
[ "$build" = 1 ] && tasks+=(:app:assembleDebug)

LOG=$ROOT/build/contract-tests.log
mkdir -p "$ROOT/build"
./gradlew --continue "${tasks[@]}" > "$LOG" 2>&1
code=$?

python3 -I - "$ROOT" <<'PY'
import glob, sys, xml.etree.ElementTree as E
root = sys.argv[1]
bad = []
for m in ('app', 'termux-shared'):
    for p in glob.glob(f'{root}/{m}/build/test-results/testDebugUnitTest/*.xml'):
        r = E.parse(p).getroot()
        if int(r.get('failures')) + int(r.get('errors')):
            bad.append(r.get('name'))
for name in sorted(bad):
    print('FAILED', name)
if any(n.endswith(('SettingsCorpusTest', 'SettingsReferenceTest')) for n in bad):
    print('hint: scripts/contract-tests.sh --regenerate, then commit the corpus and reference')
PY
grep -m3 -E 'error:|What went wrong' "$LOG" || true
echo "contract tests exit=$code (log $LOG)"
exit $code
