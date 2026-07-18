# ONEMEP-FEATURE-SERVICE

Projects and Master Data bounded context for ONE-MEP. Owns the tables under the Jira epics
**Project** (ONEMEP-11) and **Master Data** (ONEMEP-25); deliberately separated from
`ONEMEP-IDENTITY-SERVICE`, which owns only authentication/authorization data.

| Property | Value |
|---|---|
| Port | `8086` |
| Context path | `/feature-service` |
| Eureka app name | `onemep-feature-service` |
| DB schema | `onemep_dev` (shared with identity; **own** Flyway history table `flyway_feature_history`) |
| Auth | Stateless RS256 JWT validation (identity's **public** key only) |

## Domain

| Story | Endpoint base | Table |
|---|---|---|
| Projects Listing / Add / Edit / Overview (ONEMEP-12/13/14/15) | `/projects` | `project_master`, `project_lead_mapping`, `project_member_mapping`, `project_activity_log` |
| Handling Office (configured list for projects) | `/handling-offices` | `handling_office_master` |
| Detailing Level (configured list for projects) | `/detailing-levels` | `detailing_level_master` |
| Tier Listing / Add / Edit (ONEMEP-16/17/18) | `/tiers` | `tier_master` |
| Team Roles Listing / Add / Edit (ONEMEP-19/20/21) | `/team-roles` | `team_role_master` |
| Category Listing / Add / Edit (ONEMEP-22/23/24) | `/categories` | `category_master` |
| Unit Listing / Add / Edit (ONEMEP-26/27/28) | `/units` | `unit_master` |
| Technical Master (ONEMEP-29, provisional) | `/technical-master` | `technical_master` |

### Project domain (ONEMEP-12/13/14/15)

- **Fields:** `name` (max 50, restricted charset), `categoryId` (locked after creation), `type`
  (`CONFIRMED` / `NON_CONFIRMED`), `priority` (mandatory), `lifecycleStatus` (defaults `ACTIVE`),
  `client`, `location`, `handlingOfficeId`, `detailingLevelId`, `description` (max 2000),
  `leadUserIds[]`, `members[]`.
- **Project ID scheme:** Non-confirmed → `NC{id:0000}` (e.g. `NC0012`); Confirmed →
  `{category.seriesCode}{id:0000}` (e.g. series 4 → `40012`). Confirming a Non-confirmed project
  (`PATCH /projects/{id}/type?type=CONFIRMED`) reassigns the ID and **locks** the type — the
  transition is irreversible.
- **Lifecycle:** changing to `ON_HOLD` or `CLOSED` requires a `reason`
  (`PATCH /projects/{id}/lifecycle?lifecycleStatus=ON_HOLD&reason=...`). Lifecycle/priority changes
  send a structured notification to project leads and are recorded in the activity log.
- **Overview** (`GET /projects/{id}/overview`) returns the detail card plus the specs sheets,
  delivery schedule, stakeholder directory, and the activity log.
- **Category** carries an optional unique numeric `seriesCode` used to build confirmed Project IDs.

### Project Overview sub-resources (ONEMEP-15)

| Section | Endpoints | Notes |
|---|---|---|
| Specs sheets | `POST /projects/{id}/spec-sheets` (multipart `file`), `GET .../spec-sheets`, `GET .../spec-sheets/{sheetId}/download`, `DELETE .../spec-sheets/{sheetId}` | Only `.doc/.docx/.pdf`, max **150 MB** (`413 PAYLOAD_TOO_LARGE` beyond that). Bytes stored in Postgres `BYTEA`; listings return metadata only. |
| Delivery schedule | `GET/POST /projects/{id}/delivery-schedule`, `PUT/DELETE .../delivery-schedule/{itemId}` | `status` ∈ `PENDING, IN_PROGRESS, COMPLETED, DELAYED`. |
| Stakeholder directory | `GET/POST /projects/{id}/stakeholders`, `PUT/DELETE .../stakeholders/{stakeholderId}` | `role` ∈ `PROJECT_HEAD, ARCHITECT, STRUCTURE, CLIENT, CONSULTANT, CONTRACTOR, OTHER`. |

> **Gateway note:** Spring Cloud Gateway caps in-memory buffering; to proxy 150 MB uploads set
> `spring.codec.max-in-memory-size` (and any upstream body-size limit) on `ONEMEP-API-GATEWAY`.

## Run locally

The service validates the JWT with the identity service's **public** key. Provide it via
`JWT_PUBLIC_KEY` (defaults to `./keys/jwt-public.pem`). Copy the public key from
`ONEMEP-IDENTITY-SERVICE/keys/jwt-public.pem` (private key is NOT needed here).

```bash
mkdir -p keys && cp ../ONEMEP-IDENTITY-SERVICE/keys/jwt-public.pem keys/
./mvnw spring-boot:run
```

## Commands

```bash
./mvnw clean package -DskipTests   # fast build
./mvnw verify                      # full build + tests
./mvnw spotless:apply              # auto-fix Google Java Format
```

## Notes / follow-ups

- `project_lead_mapping.user_id` / `project_member_mapping.user_id` reference
  `onemep_dev.user_master` (owned by identity) via DB foreign keys. The service now validates the
  referenced users exist (friendly 404) before persisting, but the id lookup is DB-local — a
  gRPC/REST lookup to identity would let it enrich names/emails.
- Authorization is currently coarse (any valid token). Fine-grained RBAC (per-module VIEW/EDIT) lives
  in the identity service; wiring it here needs a shared permission-check mechanism.
- Specs-sheet bytes live in-row (`BYTEA`). For very large volumes, consider offloading to object
  storage (S3/MinIO) and keeping only a reference here.
- `technical_master` is **provisional** — ONEMEP-29/30 (project-level consolidated technical form
  with DID specs, attachments and versioning) still needs a dedicated redesign.
