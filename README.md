# ONEMEP-FEATURE Service

Projects and Master Data bounded context for ONE-MEP. Owns the tables under the Jira epics
**Project** (ONEMEP-11) and **Master Data** (ONEMEP-25); deliberately separated from
`ONEMEP-IDENTITY-SERVICE`, which owns only authentication/authorization data.

| Property | Value |
|---|---|
| Port | `8086` |
| Context path | `/feature-service` |
| Eureka app name | `dim-feature-service` |
| DB schema | `onemep_dev` (shared with identity; **own** Flyway history table `flyway_feature_history`) |
| Auth | Stateless RS256 JWT validation (identity's **public** key only) |

## Domain

| Story | Endpoint base | Table |
|---|---|---|
| Projects Listing / Add / Edit / Overview (ONEMEP-12/13/14/15) | `/projects` | `project_master`, `project_lead_mapping`, `project_member_mapping` |
| Tier Listing / Add / Edit (ONEMEP-16/17/18) | `/tiers` | `tier_master` |
| Team Roles Listing / Add / Edit (ONEMEP-19/20/21) | `/team-roles` | `team_role_master` |
| Category Listing / Add / Edit (ONEMEP-22/23/24) | `/categories` | `category_master` |
| Unit Listing / Add / Edit (ONEMEP-26/27/28) | `/units` | `unit_master` |
| Technical Master (ONEMEP-29, provisional) | `/technical-master` | `technical_master` |

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

- `project_lead_mapping.user_id` and `project_member_mapping.user_id` reference
  `onemep_dev.user_master` (owned by identity) via DB foreign keys. This service stores the raw id
  and does not currently validate the user exists beyond the FK — consider a gRPC/REST lookup to the
  identity service for friendlier validation.
- Authorization is currently coarse (any valid token). Fine-grained RBAC (per-module VIEW/EDIT) lives
  in the identity service; wiring it here needs a shared permission-check mechanism.
- `technical_master` is **provisional** — ONEMEP-29 has no description yet.
