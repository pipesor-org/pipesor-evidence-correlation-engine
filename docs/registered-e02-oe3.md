# OE3 evidence inventory

The three versioned synthetic scenarios contain one physical log line each, three total across E01/E02/E03. E02 registration replay reuses those bytes. Redeliveries, the second scope member, and alternate run attempts in the owner-chain test are negative controls and are not counted as additional independent logs or scenarios.

| Scenario | Reference cause and input limit | Observed outcome |
| --- | --- | --- |
| E01 checkout DNS | Runner failed to resolve `git.example.invalid`. Reason for DNS failure unknown. | Deterministic result identified failed name resolution and cited its log and guide. Historical managed evidence also exists. |
| E02 dependency | Synthetic offline index lacks the requested `org.example:fixture-lib:9.9.9`. Origin of the request is unknown. | Deterministic result cited one log and one scoped dependency guide. Historical GPT-4o suggested the missing offline version and cited one log and one guide. |
| E03 ambiguous build | Exit code 1 with command and stderr deliberately omitted. A specific cause is unsupported. | Deterministic and historical managed results stayed inconclusive, with log evidence and no knowledge citation. |

| Capability | Verified evidence and limit |
| --- | --- |
| Ingestion boundary | Pinned protected GitHub-shaped fixtures enter Log/Event and NLP. No webhook capture count or live GitHub evidence from these runs. |
| Processing | Scoped membership, redaction/structured NLP, durable correlation, receipt delivery and rejection controls in the owner-chain replay. |
| RAG | Authorized corpus matches and no-match for an alternate scope in PostgreSQL/pgvector. Deterministic trial and historical managed embedding receipts are separate. |
| Diagnosis | Admitted structured hypothesis with evidence links, knowledge provenance and limitations. Historical managed outcomes in [increment 3](preliminary-validation-increment-3.md) and external supplemental results in [increment 5](preliminary-validation-increment-5-results.md) are separate from the three-synthetic-scenario baseline. |
| Report/dashboard and review | Existing React views exercise scoped reads, original diagnosis and immutable local P3 review history. Restart is separately tested on retained PostgreSQL. General product diagnosis discovery remains gated. |

The ten-scenario, 1,000-log and 80% targets remain to be measured. Before evaluating them, pin a corpus with at least ten independent scenarios, record the expected cause or deliberate ambiguity separately from RAG and prompts, define and count distinct input log records at admission, and track rejects and redeliveries separately. Use the same approved inference deployment and bounded ledger, save one result and its citations per scenario, and have reviewers score blindly against agreed cause and evidence criteria. Publish denominator, exclusions and failure counts alongside any accuracy figure. See [increment 2](preliminary-validation-increment-2.md) for the original deterministic receipts. Its pending-managed statement predates increment 3.
