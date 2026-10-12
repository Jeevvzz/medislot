#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export MEDISLOT_DB=h2
export MEDISLOT_SECURE_COOKIES=false
export PORT="${PORT:-3000}"
exec mvn -f backend/pom.xml clean compile exec:java
