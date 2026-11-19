"""Loopback-only synthetic RAG fixture with pgvector and managed embeddings."""

import json
import logging
import socket
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import NAMESPACE_URL, UUID, uuid5

import uvicorn
from sqlalchemy.orm import sessionmaker

from pipesor_rag_retrieval.api import create_app
from pipesor_rag_retrieval.bootstrap import build_embedding_provider, build_postgresql_engine
from pipesor_rag_retrieval.configuration import Settings
from pipesor_rag_retrieval.corpus import CorpusImport, ImportAuthorizedCorpus, ImportStatus
from pipesor_rag_retrieval.domain import AuthorizationScope, CorpusKind
from pipesor_rag_retrieval.persistence import PostgreSQLCorpusStore

PROVENANCE = "synthetic://pipesor/local-dev/runner-connectivity/v1"
KIND = CorpusKind.AUTHORIZED_TECHNICAL_DOCUMENTATION


def seed(settings, scope):
    engine = build_postgresql_engine(settings)
    try:
        importer = ImportAuthorizedCorpus(
            PostgreSQLCorpusStore(sessionmaker(bind=engine, expire_on_commit=False)),
            build_embedding_provider(settings),
        )
        result = importer.execute(CorpusImport(
            source_id="local-dev/runner-connectivity", source_version="1",
            title="Generic runner connectivity", provenance=PROVENANCE,
            authorization_reference="local-dev:synthetic-corpus-authorized",
            corpus_kind=KIND, scope=scope, correlation_id="local-dev-rag-import",
            chunks=("From the affected runner, check DNS resolution and network reachability before retrying a remote source operation.",),
        ))
        assert result is ImportStatus.CREATED
        with engine.connect() as connection:
            assert connection.exec_driver_sql(
                "SELECT vector_dims(embedding), embedding_model, embedding_model_version FROM knowledge.knowledge_chunks"
            ).all() == [(1536, "text-embedding-3-small", settings.embedding_model_version)]
        print("PIPESOR_RAG_IMPORT one synthetic chunk persisted with 1536 managed embedding dimensions", flush=True)
    finally:
        engine.dispose()


def verify(scope, port):
    url = f"http://127.0.0.1:{port}/internal/v1/retrieval/query"
    payload = {
        "contract_version": "1.0", "correlation_id": "hybrid-scope-check",
        "query": "How should a build runner troubleshoot a hostname lookup failure?",
        "authorization_scope": {
            "tenant_id": str(scope.tenant_id), "github_installation_id": scope.github_installation_id,
            "github_repository_id": scope.github_repository_id, "pipeline_id": str(scope.pipeline_id),
            "scope_revision": scope.scope_revision,
        },
        "allowed_corpus_kinds": [KIND.value], "top_k": 1,
    }

    def retrieve(caller):
        request = Request(url, data=json.dumps(payload).encode(), headers={
            "Content-Type": "application/json", "X-Pipesor-Caller": caller,
            "X-Correlation-ID": payload["correlation_id"],
        })
        with urlopen(request, timeout=20) as response:
            return json.load(response)

    try:
        retrieve("unapproved-local-caller")
    except HTTPError as error:
        assert error.code == 403
    else:
        raise AssertionError("Unauthorized caller gained access")
    match = retrieve("recommendation_service")
    assert match["status"] == "matches" and len(match["matches"]) == 1
    assert match["matches"][0]["provenance"] == PROVENANCE + "#chunk=0"
    payload["authorization_scope"]["scope_revision"] += "-denied"
    denied = retrieve("recommendation_service")
    assert denied["status"] == "no_match" and denied["matches"] == []
    print("PIPESOR_RAG_HTTP authorized match with provenance; wrong scope no match; wrong caller 403", flush=True)


def main():
    envelope = json.loads(Path(sys.argv[1]).read_text())
    capture = envelope["capture_context"]
    pipeline = envelope["pipeline"]
    repo = pipeline["github_repository_id"]
    workflow = pipeline["github_workflow_id"]
    scope = AuthorizationScope(
        tenant_id=UUID(capture["tenant_id"]),
        github_installation_id=capture["github_installation_id"],
        github_repository_id=repo,
        pipeline_id=uuid5(NAMESPACE_URL, f"urn:pipesor:v1:pipeline:github:{repo}:{workflow}"),
        scope_revision=capture["scope_revision"],
    )
    port_file = Path(sys.argv[2])
    if sys.argv[3:] == ["verify"]:
        verify(scope, int(port_file.read_text()))
        return
    settings = Settings.from_environment()
    assert settings.runtime_environment == "test" and settings.authentication_mode == "simulated"
    assert settings.storage_backend == "postgresql" and settings.embedding_backend == "azure_openai"
    assert settings.database_url is not None and "@127.0.0.1:" in settings.database_url.get_secret_value()
    seed(settings, scope)
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    app = create_app(settings=settings)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen()
        port_file.write_text(str(listener.getsockname()[1]))
        uvicorn.Server(uvicorn.Config(app, access_log=False)).run(sockets=[listener])


if __name__ == "__main__":
    main()
