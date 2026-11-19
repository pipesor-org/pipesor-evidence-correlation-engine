"""Loopback-only synthetic RAG fixture with pgvector and managed embeddings."""

import json
from threading import Lock
import logging
import os
from dataclasses import replace
import socket
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import NAMESPACE_URL, UUID, uuid5

import uvicorn
from azure.identity import DefaultAzureCredential, get_bearer_token_provider
from openai import OpenAI
from sqlalchemy.orm import sessionmaker

from pipesor_rag_retrieval.api import create_app
from pipesor_rag_retrieval.application import RetrieveAuthorizedKnowledge
from pipesor_rag_retrieval.infrastructure import AzureOpenAIEmbeddingProvider
from pipesor_rag_retrieval.bootstrap import build_embedding_provider, build_postgresql_engine
from pipesor_rag_retrieval.configuration import Settings
from pipesor_rag_retrieval.corpus import CorpusImport, ImportAuthorizedCorpus, ImportStatus
from pipesor_rag_retrieval.domain import AuthorizationScope, CorpusKind
from pipesor_rag_retrieval.persistence import PostgreSQLCorpusStore, PostgreSQLKnowledgeRepository

SCENARIO = os.environ.get("PIPESOR_DEMO_SCENARIO", "local-checkout-dns")
GUIDE = "runner-connectivity" if SCENARIO == "local-checkout-dns" else "dependency-index"
PROVENANCE = f"synthetic://pipesor/local-dev/{GUIDE}/v1"
GUIDANCE = (
    "From the affected runner, check DNS resolution and network reachability before retrying a remote source operation."
    if GUIDE == "runner-connectivity" else
    "Compare requested versions against the approved dependency index and check repository settings before changing constraints."
)
KIND = CorpusKind.AUTHORIZED_TECHNICAL_DOCUMENTATION


class MeteredEmbeddings:
    def __init__(self, settings):
        token = get_bearer_token_provider(DefaultAzureCredential(), "https://ai.azure.com/.default")
        self.endpoint = OpenAI(base_url=f"{settings.azure_openai_endpoint}/openai/v1/",
                               api_key=token, max_retries=0).embeddings
        self.lock = Lock()
        self.attempts = 0

    def create(self, **request):
        assert len(request["input"].encode()) <= 512
        with self.lock:
            if self.attempts >= 8:
                raise RuntimeError("Synthetic embedding budget exhausted")
            self.attempts += 1
        response = self.endpoint.create(**request)
        assert response.model == "text-embedding-3-small"
        print("PIPESOR_EMBEDDING_USAGE " + json.dumps({
            "model": response.model, "deployment": request["model"],
            "prompt_tokens": response.usage.prompt_tokens,
            "dimensions": len(response.data[0].embedding),
        }), flush=True)
        return response


def seed(settings, scope, embeddings):
    engine = build_postgresql_engine(settings)
    try:
        importer = ImportAuthorizedCorpus(
            PostgreSQLCorpusStore(sessionmaker(bind=engine, expire_on_commit=False)),
            embeddings,
        )
        result = importer.execute(CorpusImport(
            source_id=f"local-dev/{GUIDE}", source_version="1",
            title=f"Generic guidance for {GUIDE}", provenance=PROVENANCE,
            authorization_reference="local-dev:synthetic-corpus-authorized",
            corpus_kind=KIND, scope=scope, correlation_id="local-dev-rag-import",
            chunks=(GUIDANCE,),
        ))
        assert result is ImportStatus.CREATED
        with engine.connect() as connection:
            assert connection.exec_driver_sql(
                "SELECT vector_dims(embedding), embedding_model, embedding_model_version FROM knowledge.knowledge_chunks"
            ).all() == [(1536, "text-embedding-3-small", settings.embedding_model_version)]
        print(f"PIPESOR_RAG_IMPORT postgres pgvector 1536 {settings.embedding_backend} {settings.embedding_model_version}", flush=True)
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
    authorized = retrieve("recommendation_service")
    payload["authorization_scope"]["scope_revision"] += "-other"
    other = retrieve("recommendation_service")
    match, empty = (other, authorized) if SCENARIO == "E03" else (authorized, other)
    assert match["status"] == "matches" and len(match["matches"]) == 1
    assert match["matches"][0]["provenance"] == PROVENANCE + "#chunk=0"
    assert empty["status"] == "no_match" and empty["matches"] == []
    print(f"PIPESOR_RAG_HTTP authorized={authorized['status']} other={other['status']} wrong-caller=403 "
          f"seeded-chunk={match['matches'][0]['chunk_id']}", flush=True)


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
    assert settings.storage_backend == "postgresql"
    managed = os.environ.get("PIPESOR_DEMO_RAG_MODE") == "hybrid"
    assert settings.embedding_backend == ("azure_openai" if managed else "deterministic")
    assert settings.database_url is not None and "@127.0.0.1:" in settings.database_url.get_secret_value()
    retrieval = None
    if managed and SCENARIO != "local-checkout-dns":
        assert settings.embedding_model_version == "1" and settings.azure_openai_deployment == "pipesor-dev-embeddings"
        assert settings.azure_openai_endpoint == "https://ai-pipesor-dev-04f339b7.services.ai.azure.com"
        embeddings = AzureOpenAIEmbeddingProvider(settings.azure_openai_endpoint, settings.azure_openai_deployment,
            settings.embedding_model_version, timeout_seconds=settings.embedding_timeout_seconds,
            max_retries=0, embeddings=MeteredEmbeddings(settings))
        engine = build_postgresql_engine(settings)
        repository = PostgreSQLKnowledgeRepository(sessionmaker(bind=engine, expire_on_commit=False), settings.candidate_limit)
        retrieval = RetrieveAuthorizedKnowledge(repository, embeddings)
    else:
        embeddings = build_embedding_provider(settings)
    seed(settings, replace(scope, scope_revision=scope.scope_revision + "-other") if SCENARIO == "E03" else scope,
         embeddings)
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    app = create_app(service=retrieval, settings=settings)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        listener.listen()
        port_file.write_text(str(listener.getsockname()[1]))
        uvicorn.Server(uvicorn.Config(app, access_log=False)).run(sockets=[listener])


if __name__ == "__main__":
    main()
