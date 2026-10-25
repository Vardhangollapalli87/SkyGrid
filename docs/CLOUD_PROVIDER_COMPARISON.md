# SkyGrid zero-cost cloud stack verification

**Research checked:** 2026-10-25 (provider pages are live and limits/pricing can change). This is provider research only. No accounts, billing details, trials, or resources were created. No connection or deployment was tested.

**Project verification addendum (2026-10-25):** The existing local development Neon database is separate from the provisional production stack below. It reports PostgreSQL 18.6; PostGIS is enabled, and live `ST_SRID` and `ST_Intersects` checks passed. Mission Service additive schema and spatial repository/API tests also passed against this database. This verifies the current development connection only; it does not approve the production provider, regions, quotas, or cost estimate.

## Conditional recommendation (provisional; owner approval required)

| Component | Provisional choice | Cost target | Qualification |
|---|---|---:|---|
| PostgreSQL/PostGIS | Neon Free, PostgreSQL 17 candidate | $0 / ₹0 per month within free caps | Neon documents PostGIS installation and PG14–18 support (PG19 rolling out in some regions). Verify actual PostGIS version and `CREATE EXTENSION postgis` on chosen project before committing migrations. Free tier has no IP Allow/private networking; owner must accept a public-reachable DB endpoint protected by TLS and credentials. |
| Kafka | Aiven for Apache Kafka Free | $0 / ₹0 per month within free limits | Standard Kafka clients, TLS, small caps, no SLA; idle/first-use poweroff and limited region selection can interrupt event flow. |
| Two Spring services | OCI Always Free Ampere A1 VM, both services as separate containers/processes on the same VM | $0 / ₹0 while within Always Free quota | Closest zero-cost host fit: current free quota is 2 OCPU/12 GB RAM total, enough in aggregate for a small demo. Not two separately hosted machines; shared host failure/maintenance and idle-reclamation risk. OCI signup generally requires a payment card verification hold, so this conflicts with the current no-billing-details restriction if account creation is later approved. ARM64 Java 25 image, region capacity, TLS endpoint and external Kafka/DB connectivity need verification. |
| Frontend | Vercel Hobby static hosting | $0 / ₹0 within Hobby limits | Fits an individual, non-commercial portfolio project. Not suitable if the use becomes commercial or team/business activity. |
| Evidence | Defer S3 uploads for MVP | $0 / ₹0 | Do not build attachments into MVP. AWS's S3 free allowance is time-limited/account-dependent and requests/transfer/storage can become chargeable. Keep attachment schema/API optional/planned. |

This combination is **not guaranteed to be continuously available or entirely free under all conditions**. It is a conditional experimental 4–5-day demo candidate if free resource eligibility, placement, and actual connectivity checks pass. Two backend deployments can fit on one VM, retaining two independently deployable Spring applications, but they share one machine and one public host. No provider offers are guaranteed to be available in the user's account or chosen region. The database endpoint is publicly reachable and protected by TLS plus database credentials; Neon Free does not include IP Allow or private networking. If publicly reachable managed data endpoints are unacceptable, this zero-cost candidate does not meet the security boundary without a paid feature or a different provider/plan.

## Neon Free — PostgreSQL and PostGIS

### Published limits checked

Neon's current plan table lists Free at $0/month with 100 projects, 10 branches/project, 100 CU-hours/project/month, autoscaling up to 2 CU (8 GB RAM), scale-to-zero after 5 minutes, 1 GB storage/project (20 GB account total), and 5 GB public network transfer/project/month. A project can run a 0.25 CU compute for 400 hours before using the 100 CU-hour allowance. Allowance resets monthly; do not configure a higher max compute without measuring consumption. A JVM service with persistent pooled DB connections may cause more compute use than an idle serverless function; measure actual CU-hour burn.

Neon's API documentation says PostgreSQL 14–18 are supported, with 19 rolling out to enabled regions. Select PG17 as the conservative candidate for the project; do not choose a version just because the API exposes it. Neon documents installing PostGIS with `CREATE EXTENSION IF NOT EXISTS postgis;`; extension versions vary by server version. Before Phase 1, confirm availability and version using the provider's extension catalog/`pg_available_extensions` and `PostGIS_Full_Version()` on the actual selected PG17 project.

Neon provides direct and pooled hostnames. The built-in PgBouncer uses transaction pooling. That is suitable for ordinary HikariCP app connections when session-state assumptions are avoided, but migration tooling should use the direct endpoint because transaction pooling has session feature limitations. Keep connection pools small, validate/reconnect stale connections, and avoid setting per-session search path; use schema-qualified names or role-level defaults.

TLS is supported; Neon recommends `sslmode=verify-full` for strongest server identity validation. Never disable certificate verification in production.

### SkyGrid fit and risks

- PostGIS spatial types, geometry operations, GiST indexes and meter-based geography operations are documented by Neon. The exact PostGIS version is not fixed in current docs; verify extension/version at provisioning time.
- 1 GB storage is adequate for small seeded demo data and assessment history if evidence binaries are not stored in the database.
- 100 CU-hours are usage-dependent. A 5-day continuously active minimum-size 0.25 CU compute would use about 30 CU-hours (0.25 × 24 × 5), leaving headroom, but service start/stop behavior and actual billed compute must be checked. Autoscaling higher than 0.25 increases burn proportionally.
- The free public egress allowance is 5 GB/month; exceeding it suspends compute until next cycle or upgrade. Neon PrivateLink is Scale-only and paid, so this stack uses public TLS connections.
- Neon’s current plan table places IP Allow and private networking on Scale, not Free. Neon Free therefore cannot enforce a database source-IP allowlist. Require TLS identity verification, strong unique credentials and least-privilege roles, and use synthetic demo data only. If source-IP restriction is mandatory, reselect a database plan/provider and recalculate cost before provisioning.
- The docs show PG17 free availability and PG18 available; the API's range is current, but the final create-project choices are account/region dependent. Recheck the live create-project list.

## Aiven Free — Apache Kafka

### Published limits and capabilities

Aiven's Kafka Free tier is a non-trial $0 service with no credit card required, available as one Kafka service per organization. Its official free-tier docs list ingress up to 250 KiB/s and egress up to 250 KiB/s; up to five topics, each with two partitions; fixed retention (pricing/free Kafka pages say up to three days); a limited number of users/ACLs; limited cloud/region choices; no Kafka Connect, MirrorMaker 2, tiered storage, some integrations, or SLA. Standard Kafka clients connect in the same way as to paid Aiven Kafka. Free tier is created in the Console; cloud provider/regions are constrained and Aiven may change them.

TLS is enabled by default for Aiven Kafka. The platform supports SASL/PLAIN over TLS and SCRAM-SHA-256/512; use TLS and the strongest mechanism exposed for the selected free service. Aiven's docs say authentication options exist, but the free tier also lists some integrations as unavailable and limits users/ACLs. Confirm the precise auth mode the Free service presents in its generated client configuration. Confirm the Spring Boot 3.5.16 managed Kafka client version against the broker and perform producer, consumer-group, TLS/auth, retry and reconnect smoke tests.

### Idle and event-flow risk

Aiven powers off a Free service after no ongoing Kafka activity; first-use services that have no initial activity can also power off within a few hours. It sends a notification and allows manual power-on. An actively polling consumer may count as activity, but don't assume that without a test. When Mission Service is asleep/disconnected, hazard events can wait only within Kafka's short retention window; build startup/reconnect handling and keep the demo operationally supervised.

The 250 KiB/s limits are ample for the MVP's small JSON events. Five topics/two partitions per topic are sufficient for the single `hazard-events` topic. Three-day retention is not a durable event archive. The service has no SLA and is unsuitable for safety-critical or production use.

Network traffic is not charged separately by Aiven on classic service pricing, but Aiven warns the application cloud provider may charge traffic to/from its services. OCI's Always Free outbound data allowance is 10 TB/month; Neon has its separate 5 GB public database egress cap. Use TLS over public endpoints; no private inter-provider network is established by this plan.

## Two-service hosting comparison

### Candidate: OCI Always Free Ampere A1

OCI's current Always Free documentation lists up to 1,500 OCPU-hours and 9,000 GB-hours/month for Ampere A1, equivalent to 2 OCPUs and 12 GB memory for Always Free accounts; resources must be in the tenancy home region. At a 31-day month, 2 OCPU × 744 h = 1,488 OCPU-hours and 12 GB × 744 h = 8,928 GB-hours, just inside the stated monthly allowances. There is a 10 TB/month outbound data allowance and 200 GB block volume total for block/boot volumes. It is not an application PaaS: prepare a Linux ARM64 host, firewall, process/container supervision, updates, logs, health checks and TLS termination. Docker is allowed only in the deployment-preparation phase as already approved.

OCI may reclaim an Always Free VM if CPU p95, network utilization and (for A1) memory utilization are all below 20% over a 7-day period. The workload could meet these idle criteria between demonstrations, so the VM is not a guaranteed always-on host. OCI also warns of home-region capacity shortages. The account signup guide says most users must provide a payment card for identity verification; a temporary authorization may appear although the card is not charged unless upgrading. This is a gating issue under the current instruction not to enter billing details; no signup was attempted.

The A1 quota is enough to host two small Spring JVMs together, but do not assume their memory footprint. Build Java 25/Linux ARM64 artifacts and measure resident memory, cold startup, CPU, health checks and Kafka reconnect before selecting final JVM/container memory limits. The two services remain separate applications/ports and can be rolled independently, but host failure takes down both. A separate always-free A1 host for each is not supported by the current total quota.

### Alternatives and why not as the primary zero-cost recommendation

| Host | Current free limits | SkyGrid assessment |
|---|---|---|
| Render Free | 512 MB RAM, 0.1 CPU per web service; spins down after 15 minutes without inbound HTTP/WebSocket traffic; 750 instance-hours shared by the workspace/month; Hobby workspace includes 5 GB outbound bandwidth; ephemeral files; external database/API traffic can trigger suspension if unusually high. | Two services kept awake continuously require about 1,488 hours per 31-day month, almost double the 750-hour workspace allowance. Mission's long-running Kafka consumer is outbound activity and does not constitute incoming HTTP; idle spin-down disconnects the consumer. Java deployment uses custom Docker (allowed only in deployment phase). Not a reliable always-on two-service host at $0. |
| Koyeb Free | One free Web Service per organization, 512 MB RAM, 0.1 vCPU, 2 GB SSD; scales to zero after one hour without traffic. Free plan signup requires card verification, with a $29 temporary preauthorization described in its FAQ. | One free service cannot host two independent deployments; background worker service isn't eligible for the free instance. Not selected. |
| Vercel Functions | Free Hobby tier is aimed at personal use, but ephemeral request functions are not a suitable persistent Kafka consumer host. | Use Vercel only for the static React/Vite frontend. |

### Cross-provider network plan

No private interconnect is confirmed across OCI + Neon + Aiven. Neon says private AWS PrivateLink and IP Allow are Scale-only (paid); Aiven's free tier has limited clouds/regions and does not promise peering for the free service. Proposed MVP path is public Internet with TLS, strict database credentials, Aiven TLS/SASL credentials, minimal exposed ports, and a public HTTPS API endpoint. Aiven advertises a configurable IP filter, but verify whether and how it is available on the selected Free service. Neon Free does not provide source-IP allowlisting according to its current plan comparison. Do not describe this architecture as private networking.

OCI must reach Neon PostgreSQL (normally TCP 5432 or the provider-advertised port) and Aiven's advertised Kafka TLS port. Hazard Service needs S3 only if evidence is later approved. Browser clients need HTTPS to the API; Vercel origin must be set in CORS. There may be three different regions. Check the overlap of OCI home region, Neon free project regions, Aiven free regions, and Vercel/API latency before opening any account; the Aiven Free region set is limited and can change. Test DNS, TCP, TLS trust, auth, JDBC migration, Kafka metadata/produce/consume and reconnect from the actual OCI host. Secure public TLS is plausible without buying peering, but private networking is not assured or included.

## Vercel Hobby — React/Vite/Leaflet frontend

Vercel Hobby is free and intended for personal, non-commercial projects. A non-monetized individual portfolio demo fits that description; re-evaluate if used for business/commercial activity. Official docs list 100 GB fast data transfer, 100 deployments/day, 45-minute max build, one concurrent build, 100 MB static upload max/deployment, and limits that pause features until reset when exceeded. A static Vite SPA needs no server functions, so avoid Functions and Vercel storage. Keep API base URLs in public `VITE_*` values only; the browser bundle exposes all Vite variables. Usage exceeding Hobby allowances pauses features rather than creating on-demand charges. Use Vercel's HTTPS deployment URL as the actual CORS origin only after it exists.

## AWS S3 attachments — defer

Defer evidence attachments for the first demo. New AWS customers after July 15, 2025 receive a six-month free account plan and up to $200 credits; AWS S3 pages also describe 5 GB, 20,000 GET, 2,000 PUT and 100 GB transfer-out monthly for one year for new accounts. The exact offer depends on account age/plan, and ordinary S3 pricing includes storage, request, retrieval, and transfer components. This is not a permanent, universally guaranteed zero-cost allowance. Since evidence is optional, omitting upload avoids another account, IAM policy, billing exposure, cross-cloud transfer path and schedule risk. Keep metadata/API/S3 support planned and add only after explicit MVP value review.

## Monthly cost estimate

**Currency assumption:** ₹95 per US dollar for planning (rounded from an indicative ₹94.76/USD observed 2026-10-25; actual card conversion, taxes and bank fees can differ). This is not a provider quote.

| Stack scenario | USD/month recurring | INR/month recurring | Assumptions |
|---|---:|---:|---|
| Provisional free demo stack | $0 | ₹0 | Neon Free quotas; Aiven Free; OCI Always Free within home-region compute/block/egress quotas; Vercel Hobby personal-use limits; no S3 uploads, custom domain, paid add-ons, taxes, paid plan or overage. OCI signup card verification does not incur charge according to Oracle, but may show temporary authorization. Availability/uptime not guaranteed. |
| If OCI free capacity/account/idle-reclaim constraint fails: two always-on Render starter services + Aiven Developer Kafka | about $49 | about ₹4,655 | Render published $7/month × 2 for 512 MB services + Aiven Kafka Developer advertised $35/month. Neon stays Free within quotas; no S3. Tax and usage-based network/build overages excluded. This removes Render's Free shared-hours/sleep issue but not need to test memory/connectivity. |
| Render paid services + Aiven Free | about $14 | about ₹1,330 | Two Render 512 MB starter services at $7 each; Aiven Free stays $0 if active and within limits; Neon/Vercel remain within free quotas. Kafka Free idle behavior and limits remain. |

The $0 estimate is the allowed baseline, not a guarantee: quota exhaustion, region/capacity issues, billing account state, policy changes or choosing a paid option can make cost nonzero or stop service. There are no user-specific billing/eligibility checks. Do not enter a paid plan or card during this research phase.

### Price source notes

- Render lists the 512 MB paid web-service plan at $7/month and free service at 512 MB; actual plan and bandwidth charges must be rechecked.
- Aiven Kafka pricing lists Developer at $35/month, Free at $0 with caps.
- AWS storage is excluded from total because the upload is deferred.
- OCI resources are Always Free only within eligibility, quotas and home-region rules; no paid resources are budgeted.

## Official sources checked (2026-10-25)

### Neon

- [Plans and quotas](https://neon.com/docs/introduction/plans) — free compute, scale-to-zero, storage, egress, project/branch limits; current comparison places IP Allow/private networking on Scale. Neon announced updated Free storage/project limits on [Oct 2, 2026](https://neon.com/blog/neon-free-plan-1-gb-per-project).
- [PostGIS extension guide](https://neon.com/docs/extensions/postgis) — PostGIS enablement and spatial operations; version list is dynamic.
- [Neon API: supported PostgreSQL versions](https://api-docs.neon.tech/reference/createproject) — PG14–18, PG19 rolling to enabled regions.
- [Neon connection pooling](https://neon.com/docs/connect/connection-pooling) — pooled/direct endpoints and transaction-pool limitations.
- [Secure connections](https://neon.com/docs/connect/connect-securely) — TLS settings and `verify-full` recommendation.
- [Network transfer](https://neon.com/docs/introduction/network-transfer) — 5 GB free egress cap and private transfer only on paid Scale plan.
- [IP Allow announcement](https://neon.com/blog/restrict-access-to-your-neon-database-with-ip-allow) — Neon describes IP Allow as a paid-plan feature; the current plan comparison lists it on Scale.

### Aiven Kafka

- [Kafka Free tier technical details](https://aiven.io/docs/products/kafka/free-tier/kafka-free-tier) — eligibility, topic/partition limits, rates, retention policy, standard clients, idle power-off, regions and SLA.
- [Kafka authentication types](https://aiven.io/docs/products/kafka/concepts/auth-types) — TLS default and SASL mechanisms.
- [Aiven service pricing](https://aiven.io/docs/platform/concepts/service-pricing) — free tier and traffic charging relationship.
- [Kafka pricing](https://aiven.io/pricing/kafka) — current Free/Developer public price indications.
- [Aiven cloud regions](https://aiven.io/docs/platform/reference/list_of_clouds) — broader available regions; Free plan's selected subset still needs checking.

### Backend hosting and alternatives

- [OCI Always Free resource limits](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) — A1 compute, block volume, bandwidth, home region and idle reclamation.
- [OCI Free Tier eligibility](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm) and [signup payment verification](https://docs.oracle.com/en-us/iaas/Content/GSG/Tasks/signingup_topic-Sign_Up_for_Free_Oracle_Cloud_Promotion.htm) — trial vs Always Free and card verification/hold.
- [Render Free services](https://render.com/docs/free) — RAM/CPU, 15-minute idle spin-down, 750 workspace hours and restrictions.
- [Render compute plans](https://render.com/docs/compute-plans) and [current pricing](https://render.com/pricing) — current 512 MB paid plan and $7 price.
- [Render outbound bandwidth](https://render.com/docs/outbound-bandwidth) — Hobby 5 GB monthly included bandwidth and additional network pricing.
- [Koyeb pricing FAQ](https://www.koyeb.com/docs/faqs/pricing) and [instance types](https://www.koyeb.com/docs/reference/instances) — one free service, size, sleep, and payment verification preauthorization.

### Frontend and optional storage

- [Vercel Hobby plan](https://vercel.com/docs/plans/hobby) — personal/non-commercial terms and free usage limits.
- [Vercel plan comparison](https://vercel.com/docs/plans) and [Vercel limits](https://vercel.com/docs/limits) — static bandwidth/build/deploy limits.
- [AWS Free Tier update](https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/), [S3 FAQ](https://aws.amazon.com/s3/faqs/), [S3 pricing](https://aws.amazon.com/s3/pricing/) — account eligibility, temporary free offers and paid storage/request/transfer components.

### Currency and Java runtime context

- [USD/INR indicative rate history](https://exchangeratetool.com/historical/usd-inr) — reported ₹94.76/USD for 2026-10-25; planning uses rounded ₹95/USD.
- [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) — Spring Boot 3.5.16 supports Java through 25. This does not prove OCI ARM64 Java runtime or project build compatibility.

## Blockers and uncertainties to resolve

1. **OCI identity verification vs user restriction:** OCI states most signups require payment-card verification and a temporary hold can occur. The current phase forbids entering billing details. Do not create this account unless the owner explicitly accepts this step later.
2. **Region intersection:** Aiven Free regions are limited; Neon region availability is organization-specific; OCI home region is fixed. Confirm an acceptable region combination and latency before selecting OCI home region.
3. **Availability:** OCI can reclaim idle Always Free VMs; Aiven Free can power Kafka off after idle and has no SLA; Neon scales to zero; none guarantees stable always-on demo endpoints.
4. **PostGIS extension compatibility:** Neon supports enabling `postgis`; the existing development project is verified on PostgreSQL 18.6. The separate PG17 deployment candidate and its extension package/version still require verification if selected.
5. **Cross-cloud security/network:** No private cross-provider link is in the free candidate. Neon Free lacks IP Allow and private networking; endpoints use public TLS with credentials. Confirm this is acceptable for synthetic demo data. Verify Aiven Free IP filter/auth availability, outbound connectivity and provider egress treatment.
6. **Java 25 ARM64:** Spring Boot 3.5.16 framework compatibility was checked previously; the exact JRE image and project dependency build on OCI A1 ARM64 remain untested.
7. **Two JVM sizing:** OCI total quota appears adequate on paper; run both services together and measure real RSS, CPU, heap, startup and under-load behavior before selecting deployment.
8. **Vercel eligibility:** Hobby only for personal, non-commercial activity. Confirm owner usage remains eligible.
9. **S3 account offer:** Free allowance changes with AWS account age and plan; defer upload.
10. **Data safety:** Neon and Aiven Free are demo tiers, not durable production data services; retention, backup and SLA limitations mean use synthetic demo data only.

## Decision required before provisioning

Approve or reject this exact conditional stack: **Neon Free (PostgreSQL 17 + verified PostGIS; publicly reachable over TLS with credential controls, no IP Allow/private networking), Aiven Kafka Free, one OCI Always Free Ampere A1 host for the two separate Spring services, Vercel Hobby, and no S3 evidence uploads.** Approval must explicitly accept (a) OCI card verification/possible temporary authorization, (b) one shared backend host with idle-reclamation risk, (c) Aiven Kafka idle poweroff/no SLA, (d) public TLS cross-cloud networking and Neon Free's lack of source-IP restriction, and (e) no guarantee that the ₹0 tiers are available in a compatible region. If public-reachable database access is unacceptable, reject this stack and revise the provider/plan or cost target. If any condition is unacceptable, do not provision.

Before production provisioning: inspect provider-specific regions/quotas and current prices; verify the selected database/PostGIS version and TLS JDBC connection; run Spring Kafka client TLS/auth, produce/consume and reconnect tests; verify both Java 25 services fit the selected host and survive restart; confirm public HTTPS/CORS with Vercel; measure monthly quota consumption; document observed outcomes. Mission CRUD and the PostGIS geospatial foundation are now implemented; future work remains phase-gated.
