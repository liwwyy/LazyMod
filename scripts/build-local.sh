#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
# The build task verifies/tests before writing its receipt and invoking local installation.
./gradlew build "$@"
