#!/usr/bin/env bash
set -euo pipefail

correlation=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
recommendation=${1:-"$correlation/../pipesor-recommendation-service"}
recommendation_revision=$(git -C "$recommendation" rev-parse --verify 'main^{commit}')
fixture=$(mktemp -d /tmp/pipesor-handoff.XXXXXXXX)
trap 'rm -rf -- "$fixture"' EXIT

printf 'Recommendation fixture revision %s\n' "$recommendation_revision"
git -C "$recommendation" archive "$recommendation_revision" | tar -xf - -C "$fixture"

cd "$correlation"
./mvnw -B -ntp -DskipTests clean compile

# Keep the peer artifact and test resources on the fixture classpath only.
mkdir -p "$fixture/target/test-classes/org" "$fixture/src/test/resources/correlation-migrations"
cp -R target/classes/org/pipesor "$fixture/target/test-classes/org/"
cp src/main/resources/db/migration/*.sql "$fixture/src/test/resources/correlation-migrations/"
cp docs/contracts/nlp-processed-evidence-v2.examples.json "$fixture/src/test/resources/"
cp tools/local-handoff/DurablePeerHandoffTest.java "$fixture/src/test/java/"

cd "$fixture"
./mvnw -B -ntp -Dtest=DurablePeerHandoffTest test
