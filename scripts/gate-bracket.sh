#!/usr/bin/env bash
# Time ./gradlew allTests at several of Gradle's worker caps, and record what
# the machine was doing while it ran.
#
# --max-workers is the one lever that bounds every suite's test forks at once:
# a Test task's maxParallelForks is per task, but each fork takes a worker
# lease from this pool, so the cap decides how many run together across the
# three suites. On a machine with little memory that is what decides whether
# the end-to-end browsers survive the functional suite running beside them.
#
# Each run archives its own test-history/<stamp>/summary.txt, failing runs
# included, so the per-suite walls and failure counts come from there rather
# than from parsing Gradle's output.
#
# Each run clears the three suites' results first, because Gradle holds a Test
# task up to date when nothing it depends on has changed. Without that, the
# second run of the gate runs no tests, archives the first run's result XML
# under a fresh stamp, and reports per-suite walls from a run that happened
# hours ago -- a row that looks like a measurement and is not one.
#
# Usage:
#   scripts/gate-bracket.sh                 # 6, 8 and 12, one run each
#   scripts/gate-bracket.sh 4 6 8 12        # those caps, one run each
#   scripts/gate-bracket.sh -r 3 6 12       # three runs of each, median taken
#
# One run each side cannot see a change smaller than the gate's own spread,
# which is tens of seconds. Use -r 2 or more before believing a difference.

set -u

cd "$(dirname "$0")/.."

repeats=1
while getopts "r:h" option; do
    case "$option" in
        r) repeats="$OPTARG" ;;
        h) awk 'NR > 1 { if (!/^#/) exit; sub(/^# ?/, ""); print }' "$0"; exit 0 ;;
        *) echo "try -h" >&2; exit 2 ;;
    esac
done
shift $((OPTIND - 1))

caps=("$@")
if [ ${#caps[@]} -eq 0 ]; then
    caps=(6 8 12)
fi

where="build/gate-bracket"
mkdir -p "$where"
results="$where/results.tsv"
printf "cap\trun\twall_s\texit\ttestsWall\tunitWall\tunitFailed\tfunctionalWall\tfunctionalFailed\tendToEndWall\tendToEndFailed\tpeakBrowserRSS_MB\tpeakWorkerRSS_MB\tpeakSwap_MB\tpeakLoad1\n" > "$results"

sampler=""
stopSampling() {
    if [ -n "$sampler" ]; then
        kill "$sampler" 2>/dev/null || true
        sampler=""
    fi
}
trap 'stopSampling; echo; echo "stopped early; $results holds the runs that finished" >&2; exit 130' INT TERM

sampleWhileItRuns() {
    local into="$1"
    printf "time\tbrowserRSS_MB\tworkerRSS_MB\tswap_MB\tload1\n" > "$into"
    while true; do
        local snapshot browsers workers swap load
        snapshot="$(ps -axo rss,command)"
        browsers=$(printf '%s\n' "$snapshot" \
            | grep -E "Google Chrome|[Ff]irefox|chromedriver|geckodriver" \
            | awk '{total += $1} END {printf "%.0f", total / 1024}')
        workers=$(printf '%s\n' "$snapshot" \
            | grep -E "GradleWorkerMain|jebol\.mainClassesDirs|GradleDaemon" \
            | awk '{total += $1} END {printf "%.0f", total / 1024}')
        swap=$(sysctl -n vm.swapusage 2>/dev/null | awk '{gsub(/M/, "", $6); print $6+0}')
        load=$(sysctl -n vm.loadavg 2>/dev/null | awk '{print $2+0}')
        printf "%s\t%s\t%s\t%s\t%s\n" \
            "$(date +%H:%M:%S)" "${browsers:-0}" "${workers:-0}" "${swap:-0}" "${load:-0}" >> "$into"
        sleep 5
    done
}

newestArchive() {
    ls test-history 2>/dev/null | tail -1
}

readSummary() {
    local summary="$1"
    if [ ! -f "$summary" ]; then
        printf "?\t?\t?\t?\t?\t?\t?"
        return
    fi
    awk '
        function wallOn(line) {
            for (at = 1; at <= NF; at++) {
                if ($at == "wall") { value = $(at + 1); gsub(/,/, "", value); return value }
            }
            return "?"
        }
        function orUnknown(value) {
            return value == "" ? "?" : value
        }
        function failedOn(line) {
            for (at = 1; at <= NF; at++) {
                if ($(at + 1) ~ /^failed/) return $at
            }
            return "?"
        }
        /^unitTests:/       { unitWall = wallOn($0);  unitFailed = failedOn($0) }
        /^functionalTests:/ { funcWall = wallOn($0);  funcFailed = failedOn($0) }
        /^endToEndTests:/   { e2eWall = wallOn($0);   e2eFailed = failedOn($0) }
        /first class started to last class finished/ { testsWall = $NF }
        END {
            printf "%s\t%s\t%s\t%s\t%s\t%s\t%s",
                orUnknown(testsWall),
                orUnknown(unitWall), orUnknown(unitFailed),
                orUnknown(funcWall), orUnknown(funcFailed),
                orUnknown(e2eWall), orUnknown(e2eFailed)
        }' "$summary"
}

echo "machine: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)"
echo "cores:   $(sysctl -n hw.physicalcpu 2>/dev/null || echo ?) physical, $(sysctl -n hw.logicalcpu 2>/dev/null || getconf _NPROCESSORS_ONLN) logical"
echo "memory:  $(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1073741824 )) GB"
echo "caps:    ${caps[*]}, $repeats run(s) each"
echo "results: $results"
echo

runsInAll=$(( ${#caps[@]} * repeats ))
runsSoFar=0

for cap in "${caps[@]}"; do
    for run in $(seq 1 "$repeats"); do
        runsSoFar=$(( runsSoFar + 1 ))
        samples="$where/samples-${cap}-${run}.tsv"
        log="$where/allTests-${cap}-${run}.log"
        before="$(newestArchive)"

        echo "== --max-workers=$cap, run $run of $repeats =="
        echo "   clearing the three suites' results so all three run"
        ./gradlew cleanUnitTests cleanFunctionalTests cleanEndToEndTests \
            --console=plain > "$where/clean-${cap}-${run}.log" 2>&1 \
            || echo "   the clean failed: $where/clean-${cap}-${run}.log" >&2

        sampleWhileItRuns "$samples" &
        sampler=$!

        started=$(date +%s)
        ./gradlew allTests --max-workers="$cap" --console=plain > "$log" 2>&1
        status=$?
        wall=$(( $(date +%s) - started ))

        stopSampling

        heldUpToDate=$(grep -cE "^> Task :(unitTests|functionalTests|endToEndTests) UP-TO-DATE" "$log")
        if [ "$heldUpToDate" -gt 0 ]; then
            echo "   WARNING: $heldUpToDate of the three suites did not run." >&2
            echo "   This row times a build that ran no tests; ignore it." >&2
        fi

        archived="$(newestArchive)"
        summary=""
        if [ -n "$archived" ] && [ "$archived" != "$before" ]; then
            summary="test-history/$archived/summary.txt"
        fi

        peaks=$(awk -F'\t' '
            NR > 1 {
                if ($2 + 0 > browsers) browsers = $2 + 0
                if ($3 + 0 > workers) workers = $3 + 0
                if ($4 + 0 > swap) swap = $4 + 0
                if ($5 + 0 > load) load = $5 + 0
            }
            END { printf "%d\t%d\t%d\t%.1f", browsers, workers, swap, load }' "$samples")

        printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
            "$cap" "$run" "$wall" "$status" "$(readSummary "$summary")" "$peaks" >> "$results"

        printf "   wall %dm%02ds, exit %d, %s\n" \
            $(( wall / 60 )) $(( wall % 60 )) "$status" \
            "$(awk -F'\t' 'END {printf "tests wall %s, failures %s/%s/%s, browsers %s MB, swap %s MB, load %s",
                $5, $7, $9, $11, $12, $14, $15}' "$results")"
        echo "   log $log"

        if [ "$runsSoFar" -lt "$runsInAll" ]; then
            echo "   letting the machine settle"
            sleep 30
        fi
    done
done

trap - INT TERM

echo
echo "=== every run ==="
column -t -s$'\t' "$results" 2>/dev/null || cat "$results"

if [ "$repeats" -gt 1 ]; then
    echo
    echo "=== median wall by cap ==="
    awk -F'\t' 'NR > 1 && $4 == 0 { walls[$1] = walls[$1] " " $3 }
        END {
            for (cap in walls) {
                count = split(walls[cap], each, " ")
                n = 0
                for (at = 1; at <= count; at++) if (each[at] != "") sorted[++n] = each[at] + 0
                for (a = 1; a < n; a++) for (b = a + 1; b <= n; b++)
                    if (sorted[b] < sorted[a]) { hold = sorted[a]; sorted[a] = sorted[b]; sorted[b] = hold }
                middle = sorted[int((n + 1) / 2)]
                printf "cap %-3s median %dm%02ds over %d run(s)\n", cap, middle / 60, middle % 60, n
                delete sorted
            }
        }' "$results"
fi

echo
echo "Reliability counts for as much as the clock here: a cap that is faster and"
echo "fails an end-to-end test has not won. Check the failure columns first."
