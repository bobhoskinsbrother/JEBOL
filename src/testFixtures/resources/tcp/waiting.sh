#!/bin/sh
set -u

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
port=18611

peer() {
    case $1 in
        bytes-add-to-the-data) (printf 'one'; sleep 0.3; printf 'two') | timeout 10 nc -N -l 127.0.0.1 $port ;;
        a-close-when-nothing-more-arrives) timeout 10 nc -N -l 127.0.0.1 $port < /dev/null ;;
        *) (printf 'some bytes back'; sleep 4) | timeout 10 nc -l 127.0.0.1 $port ;;
    esac
}

echo "# $(date -u +%Y-%m-%dT%H:%MZ), ./r3-head against nc on 127.0.0.1:$port"
for scenario in ends-where-awake-says-true keeps-going-while-false a-truthy-non-logic-does-not-end-it \
        reported-once-each a-settled-port-answers-none no-awake-ends-at-once not-connected-before-connect \
        bytes-add-to-the-data state-and-extra-after-open open-only-while-connected extra-is-the-scripts-own \
        something-else-in-state-is-not-open a-close-when-nothing-more-arrives; do
    peer "$scenario" > /dev/null &
    sleep 0.3
    timeout 20 "$root/r3-head" -s "$here/waiting.r3" "$scenario $port" 2>&1 | grep -- '->'
    wait
    port=$((port + 1))
done
