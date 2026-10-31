#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "usage: scripts/serve-example.sh <app directory> [port]" >&2
    echo "  builds the web module, assembles the app as a site in build/site, and serves it" >&2
    echo "  e.g. scripts/serve-example.sh examples/blend2d 8000" >&2
    exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
app=${1%/}
port=${2:-8000}
root=$(cd "$(dirname "$0")/.." && pwd)

[[ -f "$root/$app/main.reb" ]] || { echo "no main.reb in $root/$app" >&2; exit 1; }
[[ $port =~ ^[0-9]+$ ]] || usage

cd "$root"
./gradlew -q assembleSite -PsiteDir="$app"

echo "serving $app on port $port; Ctrl-C stops it"
for address in $(hostname -I); do
    [[ $address == *:* ]] || echo "  http://$address:$port/"
done
exec python3 -m http.server "$port" --bind 0.0.0.0 --directory "$root/build/site"
