"""Loopback-only RAG runtime with one generic synthetic knowledge chunk."""

import json
import logging
import socket
import sys
from pathlib import Path
from uuid import UUID

import uvicorn

from pipesor_rag_retrieval.api import create_app
from pipesor_rag_retrieval.application import RetrieveAuthorizedKnowledge
from pipesor_rag_retrieval.configuration import Settings
from pipesor_rag_retrieval.domain import AuthorizationScope, CorpusKind, KnowledgeChunk
from pipesor_rag_retrieval.infrastructure import (
    DeterministicEmbeddingProvider,
    InMemoryKnowledgeRepository,
)


def main():
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    envelope = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    capture = envelope["capture_context"]
    scope = AuthorizationScope(
        tenant_id=UUID(capture["tenant_id"]),
        github_installation_id=capture["github_installation_id"],
        github_repository_id=envelope["pipeline"]["github_repository_id"],
        scope_revision=capture["scope_revision"],
    )
    embeddings = DeterministicEmbeddingProvider()
    content = "Check name lookup and network configuration from the affected runner."
    chunk = KnowledgeChunk(
        id=UUID("44444444-4444-4444-4444-444444444444"),
        document_id=UUID("55555555-5555-5555-5555-555555555555"),
        chunk_index=0,
        corpus_kind=CorpusKind.AUTHORIZED_TECHNICAL_DOCUMENTATION,
        title="Runner network checks",
        content=content,
        provenance="fixture:runner-network-checks",
        scope=scope,
        embedding=embeddings.embed(content),
    )
    retrieval = RetrieveAuthorizedKnowledge(InMemoryKnowledgeRepository((chunk,)), embeddings)
    app = create_app(service=retrieval, settings=Settings(runtime_environment="test"))
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        Path(sys.argv[2]).write_text(str(listener.getsockname()[1]), encoding="ascii")
        uvicorn.Server(uvicorn.Config(app, access_log=False)).run(sockets=[listener])


if __name__ == "__main__":
    main()
