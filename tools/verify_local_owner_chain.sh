#!/usr/bin/env bash
set -euo pipefail

correlation=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
peers=${1:-"$correlation/.."}
python_bin=$("${PYTHON_BIN:-python3.14}" -c 'import drain3, uvicorn, sys; print(sys.executable)')
fixture=$(mktemp -d /tmp/pipesor-owner-chain.XXXXXXXX)
rag_pid=
rag_db=
rag_mode=${PIPESOR_DEMO_RAG_MODE:-fixture}
llm_mode=${PIPESOR_DEMO_LLM_MODE:-fixture}
case "$llm_mode" in
    fixture) ;;
    foundry)
        [[ "$rag_mode" == hybrid ]] || { echo "Foundry inference requires hybrid RAG mode" >&2; exit 2; }
        : "${PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR:?Set a protected directory holding request-budget receipts}"
        [[ "$PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR" == /* &&
            "$(stat -c %a "$PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR")" == 700 ]] || exit 2
        ;;
    *) exit 2 ;;
esac
case "$rag_mode" in fixture|hybrid) ;; *) exit 2 ;; esac
cleanup() {
    if [[ -n "$rag_pid" ]]; then
        kill "$rag_pid" 2>/dev/null || true
        wait "$rag_pid" || true
    fi
    if [[ -n "$rag_db" ]]; then
        docker rm -f "$rag_db" >/dev/null || true
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
cp "$correlation/tools/local-handoff/"{FullOwnerChainTest,OwnerChain,DemoDiagnosis,FoundryDiagnosticFixture}.java "$host/src/test/java/"

cp "$correlation/tools/local-handoff/checkout-failure.log" "$resources/"

rag_env=(PYTHONPATH="$fixture/rag-retrieval/src")
rag_server=serve_rag_fixture.py
if [[ "$rag_mode" == hybrid ]]; then
    : "${PIPESOR_RAG_AZURE_OPENAI_ENDPOINT:?Set the approved managed embedding endpoint}"
    : "${PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT:?Set the approved embedding deployment}"
    : "${PIPESOR_RAG_EMBEDDING_MODEL_VERSION:?Set the approved embedding model version}"
    rag_server=serve_hybrid_rag.py
    rag_db=$(docker run --rm -d -p 127.0.0.1::5432 \
        -e POSTGRES_PASSWORD=pipesor-local-only -e POSTGRES_DB=pipesor_rag \
        pgvector/pgvector:pg17@sha256:b513c48566e4777cd3370451400000c78eda6a08131d68f4c31d4a76f27bd8fd)
    for attempt in {1..30}; do
        docker exec "$rag_db" pg_isready -h localhost -U postgres -d pipesor_rag >/dev/null 2>&1 && break
        sleep 1
    done
    docker exec "$rag_db" psql -v ON_ERROR_STOP=1 -U postgres -d pipesor_rag -c 'CREATE EXTENSION vector'
    db_address=$(docker port "$rag_db" 5432/tcp)
    rag_env+=(PATH="$PATH" HOME="$HOME" AZURE_CONFIG_DIR="${AZURE_CONFIG_DIR:-$HOME/.azure}" \
        PIPESOR_RAG_ENVIRONMENT=test PIPESOR_RAG_STORAGE_BACKEND=postgresql \
        PIPESOR_RAG_EMBEDDING_BACKEND=azure_openai \
        PIPESOR_RAG_DATABASE_URL="postgresql+psycopg://postgres:pipesor-local-only@$db_address/pipesor_rag" \
        PIPESOR_RAG_AZURE_OPENAI_ENDPOINT="$PIPESOR_RAG_AZURE_OPENAI_ENDPOINT" \
        PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT="$PIPESOR_RAG_AZURE_OPENAI_DEPLOYMENT" \
        PIPESOR_RAG_EMBEDDING_MODEL_VERSION="$PIPESOR_RAG_EMBEDDING_MODEL_VERSION")
    (cd "$fixture/rag-retrieval" && env -i "${rag_env[@]}" "$python_bin" -m alembic upgrade head)
fi

env -i "${rag_env[@]}" "$python_bin" \
    "$correlation/tools/local-handoff/$rag_server" \
    "$resources/protected-workflow-evidence-v2.json" "$fixture/rag-port" &
rag_pid=$!
for attempt in {1..450}; do
    [[ -s "$fixture/rag-port" ]] && break
    kill -0 "$rag_pid"
    sleep 0.1
done
rag_port=$(<"$fixture/rag-port")
if [[ "$rag_mode" == hybrid ]]; then
    env -i "${rag_env[@]}" "$python_bin" "$correlation/tools/local-handoff/$rag_server" \
        "$resources/protected-workflow-evidence-v2.json" "$fixture/rag-port" verify
fi

cd "$host"
PYTHONPATH="$fixture/nlp-log-processor/src" ./mvnw -B -ntp -Dtest=FullOwnerChainTest \
    "-Dpipesor.fixture.rag.port=$rag_port" \
    "-Dpipesor.fixture.rag.mode=$rag_mode" \
    "-Dpipesor.fixture.llm.mode=$llm_mode" \
    "-Dpipesor.fixture.python=$python_bin" \
    "-Dpipesor.fixture.nlp=$correlation/tools/local-handoff/send_nlp_fixture.py" test
