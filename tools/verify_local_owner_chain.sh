#!/usr/bin/env bash
set -euo pipefail

correlation=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
peers=${1:-"$correlation/.."}
python_bin=$("${PYTHON_BIN:-python3.14}" -c 'import drain3, uvicorn, sys; print(sys.executable)')
fixture=$(mktemp -d /tmp/pipesor-owner-chain.XXXXXXXX)
rag_pid=
cleanup() {
    if [[ -n "$rag_pid" ]]; then
        kill "$rag_pid" 2>/dev/null || true
        wait "$rag_pid" || true
    fi
    rm -rf -- "$fixture"
}
trap cleanup EXIT

stage() {
    local peer=$1 ref=$2 revision
    revision=$(git -C "$peers/pipesor-$peer" rev-parse --verify "${ref}^{commit}")
    printf '%s fixture revision %s\n' "$peer" "$revision"
    mkdir -p "$fixture/$peer"
    git -C "$peers/pipesor-$peer" archive "$revision" | tar -xf - -C "$fixture/$peer"
}

for peer in pipeline-service log-event-service nlp-log-processor; do
    stage "$peer" main
done
stage recommendation-service "${PIPESOR_DEMO_RECOMMENDATION_REF:-main}"
stage rag-retrieval "${PIPESOR_DEMO_RAG_REF:-main}"

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
cp "$correlation/tools/local-handoff/"{FullOwnerChainTest,OwnerChain,DemoDiagnosis}.java "$host/src/test/java/"

cp "$correlation/tools/local-handoff/checkout-failure.log" "$resources/"

env -i PYTHONPATH="$fixture/rag-retrieval/src" "$python_bin" \
    "$correlation/tools/local-handoff/serve_rag_fixture.py" \
    "$resources/protected-workflow-evidence-v2.json" "$fixture/rag-port" &
rag_pid=$!
for attempt in {1..100}; do
    [[ -s "$fixture/rag-port" ]] && break
    kill -0 "$rag_pid"
    sleep 0.1
done
rag_port=$(<"$fixture/rag-port")

cd "$host"
PYTHONPATH="$fixture/nlp-log-processor/src" ./mvnw -B -ntp -Dtest=FullOwnerChainTest \
    "-Dpipesor.fixture.rag.port=$rag_port" \
    "-Dpipesor.fixture.python=$python_bin" \
    "-Dpipesor.fixture.nlp=$correlation/tools/local-handoff/send_nlp_fixture.py" test
