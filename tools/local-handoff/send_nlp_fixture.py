"""Isolated synthetic sender using NLP's real v2 processing and delivery path."""
import json
import sys
from urllib.parse import urlsplit

from pipesor_nlp_log_processor.application.integration_ports import (
    AuthorizationDeliveryError,
    DeliveryError,
    ReceivedMessage,
    TransientDeliveryError,
)
from pipesor_nlp_log_processor.application.process_evidence import ProcessEvidence
from pipesor_nlp_log_processor.application.process_log import ProcessLog
from pipesor_nlp_log_processor.domain.integration import InvalidIntegrationMessage
from pipesor_nlp_log_processor.domain.models import ParsedTemplate
from pipesor_nlp_log_processor.infrastructure.correlation_http import (
    CorrelationHttpDelivery,
    RetryPolicy,
    UrlLibHttpTransport,
)
from pipesor_nlp_log_processor.infrastructure.drain_parser import DrainLogParser
from pipesor_nlp_log_processor.infrastructure.sanitizer import PatternLogSanitizer


class FixtureParser:
    def __init__(self, template):
        self.template = template

    def parse(self, sanitized_content):
        return ParsedTemplate(1, self.template)


class FixtureToken:
    def __init__(self, token):
        self.token = token

    def get_token(self):
        return self.token


class LoopbackTransport:
    def __init__(self, endpoint):
        parsed = urlsplit(endpoint)
        if (
            parsed.scheme != "http"
            or parsed.hostname != "127.0.0.1"
            or parsed.port is None
            or parsed.username is not None
            or parsed.password is not None
            or parsed.path not in ("", "/")
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError("The fixture endpoint must be explicit literal loopback")
        self.endpoint = endpoint.rstrip("/")
        self.status = None

    def post(self, url, body, headers, timeout_seconds):
        if url != "https://nlp-fixture.invalid/internal/v1/processed-evidence":
            raise ValueError("Unexpected fixture route")
        response = UrlLibHttpTransport().post(
            self.endpoint + "/internal/v1/processed-evidence",
            body,
            headers,
            timeout_seconds,
        )
        self.status = response.status_code
        return response


def main():
    request = json.load(sys.stdin)
    body = request["body"]
    transport = LoopbackTransport(request["endpoint"])
    delivery = CorrelationHttpDelivery(
        "https://nlp-fixture.invalid",
        30,
        RetryPolicy(1, 0, 30),
        FixtureToken(request["token"]),
        transport,
    )
    processor = ProcessLog(
        FixtureParser(request["template"]) if "template" in request else DrainLogParser(),
        PatternLogSanitizer(),
    )
    received = ReceivedMessage(
        json.dumps(body, ensure_ascii=False).encode("utf-8"),
        body["message_id"],
        body["correlation_id"],
        broker_event_type="protected_workflow_evidence",
        broker_schema_major=2,
    )
    try:
        status = ProcessEvidence(processor, delivery).execute(received)
        result = {"status": status.value}
    except (DeliveryError, InvalidIntegrationMessage) as error:
        kind = "permanent"
        if isinstance(error, TransientDeliveryError):
            kind = "transient"
        elif isinstance(error, AuthorizationDeliveryError):
            kind = "authorization"
        result = {"status": "error", "code": error.code, "kind": kind}
    result["http_status"] = transport.status
    json.dump(result, sys.stdout, ensure_ascii=False, separators=(",", ":"))
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
