#!/usr/bin/env bash
set -euo pipefail

correlation=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
peers=${1:-"$correlation/.."}
python_bin=$("${PYTHON_BIN:-python3.14}" -c 'import drain3, sys; print(sys.executable)')
fixture=$(mktemp -d /tmp/pipesor-owner-chain.XXXXXXXX)
trap 'rm -rf -- "$fixture"' EXIT

stage() {
    local peer=$1 revision
    revision=$(git -C "$peers/pipesor-$peer" rev-parse --verify 'main^{commit}')
    printf '%s fixture revision %s\n' "$peer" "$revision"
    mkdir -p "$fixture/$peer"
    git -C "$peers/pipesor-$peer" archive "$revision" | tar -xf - -C "$fixture/$peer"
}

for peer in pipeline-service log-event-service recommendation-service nlp-log-processor; do
    stage "$peer"
done

host="$fixture/pipeline-service"
resources="$host/src/test/resources"
classes="$host/target/test-classes"
copy_peer() {
    local project=$1 location=$2 namespace=$3
    (cd "$project" && ./mvnw -B -ntp -DskipTests clean compile)
    mkdir -p "$classes" "$resources/$location"
    cp -R "$project/target/classes/$namespace" "$classes/"
    cp "$project"/src/main/resources/db/migration/*.sql "$resources/$location/"
}

copy_peer "$correlation" correlation-migrations org
copy_peer "$fixture/log-event-service" log-event-migrations org
copy_peer "$fixture/recommendation-service" recommendation-migrations com

cp "$fixture/log-event-service/src/test/resources/protected-workflow-evidence-v2.json" "$resources/"
cp "$correlation/tools/local-handoff/"{FullOwnerChainTest,OwnerChain}.java "$host/src/test/java/"

cd "$host"
PYTHONPATH="$fixture/nlp-log-processor/src" ./mvnw -B -ntp -Dtest=FullOwnerChainTest \
    "-Dpipesor.fixture.python=$python_bin" \
    "-Dpipesor.fixture.nlp=$correlation/tools/local-handoff/send_nlp_fixture.py" test
