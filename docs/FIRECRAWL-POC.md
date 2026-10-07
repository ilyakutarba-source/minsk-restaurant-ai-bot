# V2 experimental catalog ingestion — controlled PoC

Feature-branch developer tooling only. Firecrawl is not a production capability.
No Spring beans, HTTP endpoints, AI tools, application configuration, DB writes,
schema changes or import integration are added. The CLI starts no Spring context.

Boundary: official restaurant website → Firecrawl → unapproved candidate JSON →
manual validation/approval → a separately requested future import task.
Telegram/Mini App/search never invoke Firecrawl. Runtime boundaries: [ARCHITECTURE](ARCHITECTURE.md).

## Official contract

Checked 2026-10-07: [v2 scrape API](https://docs.firecrawl.dev/api-reference/endpoint/scrape),
[schema-based JSON mode](https://docs.firecrawl.dev/features/llm-extract).
The client uses `POST https://api.firecrawl.dev/v2/scrape`, bearer authentication,
and `formats: [{type: "json", schema, prompt}]`; response extraction reads `data.json`.
`onlyMainContent=false` retains contact/footer facts. One page per brand, no crawl,
map, link following, separate menu downloads, repair calls or application retries.
HTTP redirects are disabled. Connect/request timeouts: 10s/75s; provider timeout: 60s.
Response reading is capped at 1 MB. These transport settings are not a guaranteed
total deadline for a slowly streamed response. JSON extraction may cost more than a
plain scrape; this task makes no assumption about account credits or pricing.

## Candidates and review

`RestaurantImportCandidate` is a plain immutable record, not a JPA entity or an
existing `MenuDataset`. JSON has name/address/cuisine candidates/raw opening-hours
text/menu URL/up to 10 explicit BYN menu prices/source URL/fetchedAt/status/missingFields.
Relative menu URLs resolve against the requested source. HTTPS URLs with credentials,
query strings or fragments are rejected; edit the target to a clean public page.
The source URL always comes from the controlled input, never generated provenance.
Responses and exception payloads are not logged or copied into failure evidence.

EXTRACTED means all six requested field groups were extracted; PARTIAL means usable
name/address with missing optional groups; INVALID covers failed/malformed responses,
provider warnings/error pages or missing identity. All statuses remain **UNAPPROVED**.
`fetchedAt` records acquisition completion (attempt completion on failure), never human
verification. No catalog/check/hours/menu verifiedAt fields or estimated check are produced.
Missing fields stay empty. Prices with missing/foreign currency, invalid type,
nonpositive value or more than two decimals are omitted without conversion or rounding.
Hours are not converted into production opening intervals; cuisine strings are not enums.

Duplicate preview compares normalized name + normalized address: Unicode whitespace,
lowercase with Locale.ROOT, basic punctuation removal. Invalid empty identities are
excluded. Duplicates are reported by zero-based indexes, never merged or silently removed.
This does not resolve spelling variants, street abbreviations, transliteration or geocoding.
It compares the current candidate batch, not the DB. A future curator must compare
against an explicit existing-catalog snapshot before approving any import.

`tools/firecrawl/targets.json` contains **10 planned distinct brands**, not extracted or
approved records. Belarusian/Italian/Georgian/Japanese/Asian sites are represented.
Some landing pages have multiple branches, image/PDF menus or availability issues;
inability to isolate one concrete Minsk venue must remain missing/INVALID. Review the
target pages before the live run; source browsing is not a Firecrawl test. No fallback
address/brand/cuisine is filled from the target label.

## Offline tests

```powershell
.\mvnw.cmd '-Dtest=Firecrawl*Test' test
.\mvnw.cmd clean test
```

Fixtures under `src/test/resources/ingestion/firecrawl/` are hand-authored synthetic
documented API envelopes. They are not live captures or evidence of restaurant facts
or Firecrawl extraction quality. HTTP tests contact only loopback servers, never Firecrawl.
The isolation test checks repository interactions, full catalog/menu/hours snapshots,
and absence of Firecrawl beans in the runtime context.

## Explicit live run

Requires JDK 21 (`JAVA_HOME`) and `FIRECRAWL_API_KEY` supplied through the local process
environment/secret manager. Never paste the key in chat, a command argument, Git or logs.
No key means `USER_INPUT_REQUIRED` and quality verdict `LIVE_GATE_NOT_RUN`.
Ordinary Maven tests never select or run this CLI, even if a key is present.

From the repository root, after reviewing the ten targets and available credits:

```powershell
New-Item -ItemType Directory -Force probes/v2-02-evidence | Out-Null
.\mvnw.cmd -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=probes/v2-02-evidence/classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Compile/classpath failed' }
$pocClasspath = 'target/classes;' + (Get-Content probes/v2-02-evidence/classpath.txt -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" -cp $pocClasspath `
  by.ilya.restaurantbot.ingestion.firecrawl.FirecrawlPoc `
  tools/firecrawl/targets.json probes/v2-02-evidence/live-candidates.json
```

Maximum ten targets/distinct normalized brand labels and distinct URLs per invocation;
all inputs are validated before the first call. Existing output is never overwritten;
do not rerun just to improve a result. The program saves one candidate per attempted
brand, safe failure codes and duplicate indexes. Interruption stops immediately without
retry; an interrupted output may be incomplete. Do not import this JSON directly.
Keep results and run evidence in ignored local `probes/v2-02-evidence/`.

For each live target record useful/missing fields, extraction status, source and factual
errors found during manual review. Then choose GOOD_ENOUGH / USABLE_WITH_MANUAL_REVIEW /
TOO_INCONSISTENT. Without live results choose LIVE_GATE_NOT_RUN, never infer quality
from fixtures. V2-03 requires a separate request and acceptance decision.
