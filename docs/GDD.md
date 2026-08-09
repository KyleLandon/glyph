# ANARCHY ECONOMY

## Game Design Document + Technical Design Specification

**Document Version:** 1.0
**Target Platform:** Minecraft Java Edition 26.2
**Primary Server Software:** Folia
**Proxy:** Velocity
**Language:** Java 21+
**Build System:** Gradle Kotlin DSL
**Primary Database:** PostgreSQL
**Cache / Messaging:** Redis
**Deployment:** Linux + Docker + Pterodactyl
**Architecture Goal:** Highly scalable, server-sided, persistent anarchy/economy Minecraft network

---

# 1. PROJECT DIRECTIVE FOR CURSOR

This document is the project's primary technical and game-design specification.

When implementing this project:

1. Treat this document as the source of truth.
2. Do not arbitrarily change architecture without documenting why.
3. Prefer maintainable custom code over accumulating dozens of plugins.
4. All custom Minecraft code MUST be Folia-safe.
5. Never assume a single global Minecraft main thread.
6. Never perform blocking database, HTTP, filesystem, Redis, or network operations on a region/entity tick thread.
7. Use async operations for I/O.
8. Use Folia-aware region, entity, global, and async schedulers where appropriate.
9. All economy transactions must be atomic and auditable.
10. Economy balances must never use floating-point numbers.
11. All persistent player identification must use UUIDs.
12. Backend Minecraft servers must never be directly exposed to the public internet.
13. Velocity is the public Minecraft entry point.
14. Core gameplay functionality should be implemented in the custom `AnarchyCore` plugin rather than spread across many unrelated plugins.
15. Features should be implemented as modular services with explicit interfaces.
16. Include automated tests for systems involving money, transactions, auctions, serialization, permissions, or data integrity.
17. Fail safely.
18. Log important administrative and economy actions.
19. Configuration should not require recompilation.
20. Avoid premature microservices while maintaining clean service boundaries that can later be extracted.

The priority order is:

**Correctness → stability → exploit resistance → performance → maintainability → features.**

---

# 2. PRODUCT VISION

Build a persistent public Minecraft survival server centered around:

* anarchy-style gameplay
* meaningful risk
* player conflict
* raiding
* exploration
* emergent alliances
* player-created wealth
* player-driven markets
* proximity voice communication
* persistent reputation
* long-term progression
* extremely high server performance

The server should feel dangerous.

Players should never assume that their base, inventory, wealth, location, alliances, or reputation are completely safe.

However, "anarchy" refers primarily to gameplay rules.

It does NOT mean players may intentionally attack server infrastructure or compromise other users.

---

# 3. CORE PRODUCT PITCH

> A persistent Minecraft world with no land claims, no grief protection, no free teleportation, and no staff replacing lost items — combined with a deep player-driven economy, proximity voice chat, auctions, contracts, bounties, businesses and statistics.

Players build the civilization.

Players can also destroy it.

---

# 4. CORE DESIGN PILLARS

## 4.1 Freedom

Players determine:

* where they live
* who they trust
* who they attack
* what they sell
* what items are valuable
* where settlements emerge
* what factions form
* what territory matters
* what businesses succeed

Avoid excessive artificial restrictions.

---

## 4.2 Consequences

Actions matter.

Death may result in:

* item loss
* XP loss
* bounty consequences
* economic loss
* location exposure
* tactical disadvantages

Bases can be:

* discovered
* infiltrated
* robbed
* destroyed

Players should think before revealing locations.

---

## 4.3 Player-Driven Economy

The economy should primarily move:

**player → player**

rather than:

**server → player**

There should not be an unlimited `/shop` purchasing every resource at fixed prices.

The economy should emerge naturally.

---

## 4.4 Persistent World

The world should feel permanent.

Avoid routine seasonal wipes.

A world reset should be considered an extraordinary event.

Players should be able to return months later and find the history of the server still present.

---

## 4.5 Performance

Server performance is itself a product feature.

Target:

* stable 20 TPS
* responsive combat
* responsive inventories
* minimal rubber-banding
* minimal chunk-loading stalls
* predictable database behavior
* graceful degradation under heavy load

---

## 4.6 Minimal Client Requirements

The server should remain primarily server-sided.

Target client requirement:

**Minecraft Java + Simple Voice Chat**

Players without Simple Voice Chat may still be allowed to connect unless voice chat is intentionally made mandatory later.

Additional client-side performance or visual mods may be allowed.

---

# 5. DEFINITION OF ANARCHY

The server is **gameplay anarchy**, not infrastructure anarchy.

## Allowed

Subject to future balance changes:

* griefing
* stealing
* raiding
* betrayal
* traps
* PvP
* scams involving in-game assets
* base hunting
* espionage
* factions
* alliances
* mercenary work
* monopolies
* price manipulation
* economic warfare

## Not Allowed

* server crashing
* network attacks
* DDoS
* packet exploits
* intentional corruption exploits
* duplication exploits
* unauthorized botnets
* authentication attacks
* compromised accounts
* doxxing
* real-world threats
* malicious code distribution
* exploiting software vulnerabilities to damage infrastructure
* attempts to gain administrative permissions
* attacks against other players outside the game

Cheating policy should be separately configurable.

---

# 6. CHEAT POLICY

Recommended launch policy:

## Allowed client enhancements

* shaders
* Sodium-style performance mods
* Iris
* inventory HUDs
* accessibility modifications
* resource packs
* cosmetic modifications

## Conditionally allowed

* minimaps
* waypoint systems

These should be intentionally decided before launch.

## Prohibited

* KillAura
* reach
* fly
* speed
* movement modification
* automatic combat
* packet manipulation
* inventory exploits
* crash clients
* duplication clients
* impossible mining automation
* unattended farming bots

Freecam and similar tools should receive an explicit policy decision before launch.

---

# 7. PLAYER EXPERIENCE

## 7.1 First Join

Player connects to:

`play.example.com`

Velocity forwards the player to the anarchy backend.

On first join:

1. Player record is created.
2. Economy account is created.
3. First-join timestamp is stored.
4. Player receives the starter introduction.
5. Player spawns within the designated spawn region.
6. Voice Chat installation instructions are shown.
7. No forced tutorial dungeon should delay actual gameplay.

Example introduction:

```
Welcome to ANARCHY.

There are no land claims.
There is no grief protection.
Your base can be destroyed.
Your items can be stolen.

Trust carefully.

Use /help to begin.
```

---

# 8. SPAWN DESIGN

Spawn should provide orientation without creating a protected civilization.

Recommended initial protection:

**approximately 64-128 blocks**

Protection should prevent permanent spawn destruction while allowing danger immediately beyond it.

Inside spawn protection:

* no block breaking
* no block placing
* no lava dumping
* no fire spread exploitation
* no portal traps
* no destructive entity exploits

Optional:

* disable PvP within the innermost spawn radius
* allow PvP immediately outside it

Avoid huge protected zones.

---

# 9. WORLD DESIGN

Initial recommended borders:

### Overworld

±50,000 blocks

Approximately:

100,000 × 100,000

### Nether

±6,250 blocks

### End

±50,000 blocks

Exact values should be configurable.

---

# 10. WORLD EXPANSION SYSTEM

Borders may expand based on milestones.

Examples:

* population
* exploration
* major updates
* community events
* economic growth

Expansion must NEVER require resetting previously generated areas.

Example:

```
Launch       ±50,000
Expansion I  ±100,000
Expansion II ±250,000
Expansion III ±500,000
```

Do not promise a specific expansion schedule publicly until established.

---

# 11. WORLD PREGENERATION

All initially accessible terrain should be pregenerated before public launch.

Primary reason:

Reduce runtime generation load.

World generation should happen:

* before launch
* during maintenance
* during planned expansions

Do not generate massive border expansions during peak periods.

Pregeneration process must support:

* progress tracking
* restartability
* graceful cancellation
* storage monitoring

---

# 12. CORE GAME LOOP

Primary loop:

```
EXPLORE
   ↓
GATHER
   ↓
SURVIVE
   ↓
TRADE
   ↓
BUILD
   ↓
ACCUMULATE WEALTH
   ↓
DISCOVER OTHER PLAYERS
   ↓
ALLY / RAID / TRADE / BETRAY
   ↓
EXPAND
   ↓
REPEAT
```

---

# 13. ECONOMY PHILOSOPHY

The economy is one of the defining systems.

It should create:

* specialization
* scarcity
* markets
* trade routes
* wealthy players
* merchants
* mercenaries
* industrial players
* resource suppliers
* competition
* economic conflict

Players should be able to become economically powerful without necessarily being the strongest PvP player.

---

# 14. CURRENCY

Initial currency:

`$`

Example:

`$12,482.50`

Currency should internally use integer minor units.

Example:

```
$1.00 = 100 cents
```

Never store balances as:

* float
* double

Use:

* BIGINT
* long

Example:

```
1248250 = $12,482.50
```

---

# 15. ECONOMY ACCOUNT MODEL

Each player has:

* account UUID
* Minecraft UUID
* balance
* lifetime earned
* lifetime spent
* created timestamp
* updated timestamp
* account status

Future account types:

* player
* company
* system
* escrow

---

# 16. MONEY CREATION

Currency must enter circulation through controlled systems.

Potential faucets:

* contracts
* bounties
* server events
* voting rewards
* rare treasure
* future jobs

Avoid massive passive daily payments.

Money creation should be measurable.

---

# 17. MONEY SINKS

Required to manage inflation.

Potential sinks:

* auction listing fees
* auction transaction fees
* business registration
* cosmetic purchases
* bounty fees
* contract posting fees
* name customization
* premium market listings
* transportation systems
* optional convenience systems

Never rely on arbitrary item deletion as the primary economic sink.

---

# 18. ECONOMY COMMANDS

MVP:

```
/balance
/bal

/pay <player> <amount>

/baltop

/money history
```

Administrative:

```
/eco get <player>

/eco set <player> <amount>

/eco add <player> <amount>

/eco remove <player> <amount>
```

Every administrative balance modification MUST be logged.

---

# 19. TRANSACTION LEDGER

Every transfer must create a ledger entry.

Transaction record:

* transaction UUID
* timestamp
* source account
* destination account
* amount
* transaction type
* reason
* related entity
* metadata
* actor
* idempotency key where applicable

Types may include:

```
PLAYER_TRANSFER
AUCTION_PURCHASE
AUCTION_FEE
BOUNTY_ESCROW
BOUNTY_REWARD
ADMIN_ADJUSTMENT
CONTRACT_PAYMENT
BUSINESS_PAYMENT
SYSTEM_REWARD
SYSTEM_SINK
```

Transactions should never silently disappear.

---

# 20. ATOMIC TRANSACTIONS

Economy operations must be atomic.

A payment must result in either:

A:

```
source - $500
destination + $500
ledger created
```

or:

B:

```
nothing happens
```

Never allow a partial transfer.

Use database transactions.

Lock account rows where appropriate.

Prevent double-spending.

---

# 21. AUCTION HOUSE

Command:

`/ah`

The auction house should provide a GUI.

MVP functionality:

* browse listings
* search
* item categories
* sort
* buy
* list item
* cancel own listing
* expiration
* transaction fees
* listing fees
* offline seller payout

Example:

```
/ah sell 5000
```

lists currently held item for `$5,000`.

---

# 22. AUCTION SECURITY

When an item is listed:

1. Validate item.
2. Serialize immutable item snapshot.
3. Remove item safely.
4. Create auction listing.
5. Store item data.
6. Confirm listing.

Purchasing:

1. lock listing
2. verify ACTIVE
3. verify buyer funds
4. deduct buyer funds
5. credit seller minus fees
6. transfer fee to system sink
7. mark listing SOLD
8. create delivery
9. log transaction
10. commit

Do not directly give the item before the database transaction succeeds.

---

# 23. DELIVERY SYSTEM

Purchased/expired items should not rely exclusively on player inventory space.

Implement:

`/claim`

or:

`/mail`

Items can exist in a persistent delivery queue.

This prevents inventory-full edge cases.

---

# 24. PLAYER TRADING

Command:

`/trade <player>`

Potential MVP or post-MVP.

Secure two-party GUI.

Each party can add:

* items
* money

Both must confirm.

Changing trade contents resets confirmation.

Transaction executes atomically.

---

# 25. BOUNTIES

Commands:

```
/bounty
/bounty <player>
/bounty add <player> <amount>
```

Players fund bounties with real economy balances.

Bounty money moves immediately into escrow.

On valid player kill:

* bounty is paid
* ledger transaction created
* kill recorded
* bounty closed

Anti-abuse rules should include:

* repeated same-player kill detection
* shared-IP signals for moderation
* optional cooldowns
* suspicious bounty redemption logs

Do not automatically punish based solely on IP matches.

---

# 26. CONTRACT SYSTEM

Future major economy feature.

Possible contracts:

* kill player
* deliver item
* acquire resources
* protect player
* construction task
* exploration objective

Initial supported machine-verifiable contracts should be narrow.

Do not build arbitrary natural-language contract enforcement in MVP.

---

# 27. COMPANIES

Post-MVP.

Players can create organizations.

Example:

```
/company create IronWorks
```

Company functionality:

* company account
* owner
* officers
* members
* permissions
* treasury
* transaction history
* public profile

Companies DO NOT grant land claims.

They are economic/social entities.

---

# 28. PLAYER SHOPS

Later feature.

Potential implementations:

* chest shops
* marketplace NPC
* GUI storefront
* company storefronts

Must integrate with AnarchyCore ledger.

Avoid separate disconnected economy systems.

---

# 29. PROXIMITY VOICE

Use Simple Voice Chat.

Desired gameplay:

* nearby players can speak naturally
* distance attenuation
* directional audio
* optional whispering
* optional voice groups

Voice communication should enhance:

* diplomacy
* ambushes
* negotiations
* social encounters
* betrayal
* exploration

Simple Voice Chat supports the relevant server/plugin approach, and vanilla clients can still connect if voice chat is not forced.

Recommended initial range:

Normal:

48 blocks

Whisper:

12 blocks

Actual values must be configurable.

---

# 30. PLAYER STATISTICS

Track:

* first join
* last join
* playtime
* kills
* deaths
* K/D
* player kills
* mob kills
* blocks mined
* blocks placed
* distance traveled
* balance
* lifetime earned
* lifetime spent
* bounty kills
* auction sales
* auction purchases

Do not synchronously update SQL for every individual block action.

Use buffered counters where appropriate.

---

# 31. LEADERBOARDS

Initial:

```
/top money
/top kills
/top deaths
/top playtime
/top bounty
```

Future website leaderboards should use the same underlying data.

---

# 32. CHAT

Keep chat relatively simple.

Example:

```
[Kyle] Anyone selling elytra?
```

Potential channels:

```
Global
Local
Trade
```

Possible commands:

```
/g
/l
/tradechat
```

Do not overload launch with excessive RPG formatting.

---

# 33. DEATH SYSTEM

Vanilla item dropping by default.

Track:

* killer UUID
* victim UUID
* weapon/item
* world
* coordinates
* timestamp
* bounty amount
* cause

Coordinates should NOT automatically be publicly exposed.

Example death:

```
Kyle was slain by Steve.
```

Potential special bounty message:

```
Steve eliminated Kyle and claimed a $25,000 bounty.
```

---

# 34. TELEPORTATION PHILOSOPHY

Avoid:

```
/home
/tpa
/back
/warp wilderness
```

These destroy geography.

Travel should matter.

Allowed:

* vanilla portals
* elytra
* horses
* boats
* minecarts
* future player-created infrastructure

Possible admin-only teleport commands remain available for moderation.

---

# 35. MAP POLICY

Do not launch with a public live Dynmap-style map unless intentionally desired.

Base secrecy is core gameplay.

Future map options:

* spawn-only map
* explored heatmap
* delayed map
* terrain-only map without players

---

# 36. NETWORK ARCHITECTURE

Target:

```
                        INTERNET
                            |
                            |
                     DDoS Protection
                       future layer
                            |
                            |
                       VELOCITY
                     Public :25565
                            |
              +-------------+-------------+
              |                           |
              |                           |
           LOBBY                       ANARCHY
           Paper                        Folia
                                      Backend
                                          |
                                          |
                                    ANARCHYCORE
                                          |
                       +------------------+------------------+
                       |                  |                  |
                    PostgreSQL          Redis           Metrics
                       |
                       |
                   Web/API
                    future
```

Velocity is the intended scalable proxy layer, and current PaperMC documentation supports Minecraft through 26.2.

---

# 37. VELOCITY

Responsibilities:

* public Minecraft connection
* authentication forwarding
* backend routing
* future lobby
* future queue
* maintenance routing
* network-wide player count

Use Velocity modern player forwarding.

Backend server must validate Velocity forwarding.

Modern forwarding is the preferred secure Velocity forwarding format.

---

# 38. BACKEND SECURITY

Public firewall MUST NOT expose Folia directly.

Desired:

```
Internet → Velocity → Folia
```

NOT:

```
Internet → Folia
```

Backend should:

* bind private interface
* enforce forwarding
* firewall public access
* validate proxy secret

---

# 39. FOLIA

Primary survival backend.

Folia uses regionized multithreading and is particularly suited to SMP-style workloads where players naturally spread across the world.

All custom plugin code MUST account for this architecture.

---

# 40. FOLIA PROGRAMMING RULES

Never assume:

```
Bukkit.getScheduler().runTask(...)
```

is universally correct.

Use appropriate scheduling contexts.

### Global operations

Use global scheduler.

Examples:

* global metadata
* announcements
* aggregate maintenance operations

### Region operations

Use region scheduler.

Examples:

* blocks
* chunk-local operations
* region state

### Entity operations

Use entity scheduler.

Examples:

* player operations
* mob operations
* entity teleport preparation

### Async operations

Use async scheduler/executor.

Examples:

* SQL
* Redis
* HTTP
* serialization where safe
* analytics

PaperMC explicitly distinguishes global, region, async and entity scheduling for Folia-compatible plugins.

---

# 41. THREAD OWNERSHIP

Never manipulate:

* player
* entity
* chunk
* block
* inventory

from arbitrary async threads.

Pattern:

```
REGION THREAD
    |
    | capture immutable data
    v
ASYNC DATABASE
    |
    | result
    v
ENTITY/REGION SCHEDULER
    |
    v
APPLY RESULT
```

---

# 42. ANARCHYCORE

Primary custom plugin.

Package recommendation:

`com.example.anarchycore`

Replace `example` with final organization identifier.

---

# 43. MODULE STRUCTURE

Recommended:

```
anarchy-platform/
│
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
│
├── anarchy-api/
│
├── anarchy-core/
│   └── src/main/java/
│
├── anarchy-proxy/
│
├── shared/
│
├── docker/
│
├── database/
│
├── docs/
│
└── scripts/
```

---

# 44. INTERNAL ANARCHYCORE PACKAGES

```
com.example.anarchy
│
├── AnarchyPlugin
│
├── bootstrap
│
├── config
│
├── database
│
├── redis
│
├── scheduler
│
├── player
│
├── economy
│
├── transaction
│
├── auction
│
├── delivery
│
├── bounty
│
├── trade
│
├── company
│
├── stats
│
├── combat
│
├── chat
│
├── spawn
│
├── command
│
├── permission
│
├── event
│
├── api
│
├── logging
│
└── util
```

---

# 45. SERVICE ARCHITECTURE

Core services should communicate through interfaces.

Example:

```java
public interface EconomyService {
    CompletableFuture<Balance> getBalance(UUID playerId);

    CompletableFuture<TransactionResult> transfer(
        UUID source,
        UUID destination,
        Money amount,
        TransactionContext context
    );
}
```

Avoid accessing repositories directly from commands.

Preferred:

```
COMMAND
   ↓
SERVICE
   ↓
DOMAIN
   ↓
REPOSITORY
   ↓
DATABASE
```

---

# 46. DOMAIN OBJECTS

Recommended:

```
Money
Account
Transaction
AuctionListing
Delivery
Bounty
PlayerProfile
Company
CompanyMember
StatisticSnapshot
```

Use immutable objects where practical.

---

# 47. DATABASE

Use PostgreSQL.

Why:

* strong transactions
* row locking
* mature indexing
* JSONB support
* excellent observability
* robust concurrency behavior

Do not use SQLite for production.

---

# 48. DATABASE MIGRATIONS

Use:

**Flyway**

Every schema modification must be versioned.

Example:

```
V1__initial_schema.sql
V2__auction_house.sql
V3__bounties.sql
```

Never manually change the production database without a migration except emergency recovery.

---

# 49. DATABASE SCHEMA

## players

```
players
-------
uuid UUID PRIMARY KEY
username VARCHAR(16)
first_join TIMESTAMPTZ
last_join TIMESTAMPTZ
last_seen TIMESTAMPTZ
playtime_seconds BIGINT
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

Index:

```
username
last_seen
```

---

# 50. ACCOUNTS

```
accounts
--------
id UUID PRIMARY KEY
owner_type VARCHAR
owner_uuid UUID
balance BIGINT NOT NULL
lifetime_earned BIGINT
lifetime_spent BIGINT
status VARCHAR
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

Constraint:

```
balance >= 0
```

unless overdrafts are intentionally supported later.

---

# 51. TRANSACTIONS

```
transactions
------------
id UUID PRIMARY KEY
source_account UUID
destination_account UUID
amount BIGINT
type VARCHAR
reason TEXT
related_entity UUID
metadata JSONB
actor_uuid UUID
created_at TIMESTAMPTZ
```

Indexes:

```
source_account
destination_account
created_at
type
related_entity
```

---

# 52. AUCTIONS

```
auction_listings
----------------
id UUID PRIMARY KEY
seller_uuid UUID
seller_account UUID
item_data BYTEA
item_summary JSONB
price BIGINT
listing_fee BIGINT
status VARCHAR
buyer_uuid UUID NULL
created_at TIMESTAMPTZ
expires_at TIMESTAMPTZ
sold_at TIMESTAMPTZ NULL
```

Statuses:

```
ACTIVE
SOLD
CANCELLED
EXPIRED
```

---

# 53. DELIVERIES

```
deliveries
----------
id UUID PRIMARY KEY
recipient_uuid UUID
type VARCHAR
payload BYTEA
metadata JSONB
status VARCHAR
created_at TIMESTAMPTZ
claimed_at TIMESTAMPTZ NULL
```

---

# 54. BOUNTIES

```
bounties
--------
id UUID PRIMARY KEY
target_uuid UUID
creator_uuid UUID
amount BIGINT
status VARCHAR
claimed_by UUID NULL
created_at TIMESTAMPTZ
claimed_at TIMESTAMPTZ NULL
```

---

# 55. KILLS

```
player_kills
------------
id UUID PRIMARY KEY
killer_uuid UUID
victim_uuid UUID
world VARCHAR
x INTEGER
y INTEGER
z INTEGER
weapon JSONB
cause VARCHAR
bounty_amount BIGINT
created_at TIMESTAMPTZ
```

Coordinates remain private unless explicitly needed by staff.

---

# 56. STATISTICS

Prefer aggregated counters.

```
player_stats
------------
player_uuid UUID PRIMARY KEY
kills BIGINT
deaths BIGINT
mob_kills BIGINT
blocks_broken BIGINT
blocks_placed BIGINT
distance_cm BIGINT
auction_sales BIGINT
auction_purchases BIGINT
bounties_claimed BIGINT
updated_at TIMESTAMPTZ
```

---

# 57. DATABASE CONNECTION POOL

Use HikariCP unless the runtime provides a better explicitly chosen implementation.

Configuration:

```
minimumIdle
maximumPoolSize
connectionTimeout
idleTimeout
maxLifetime
```

Values must be configurable.

Never open a new database connection for every command.

---

# 58. REDIS

Redis responsibilities:

* cache
* cross-server messages
* online presence
* short-lived locks when appropriate
* future network events
* future web synchronization

Do NOT make Redis the authoritative economy database.

PostgreSQL remains authoritative.

---

# 59. REDIS KEYS

Example convention:

```
anarchy:player:online:{uuid}
anarchy:player:server:{uuid}
anarchy:economy:balance:{uuid}
anarchy:leaderboard:money
anarchy:network:players
```

Use TTLs for ephemeral state.

---

# 60. CACHE PHILOSOPHY

Caches may become stale.

Therefore:

**Database = truth**

Cache = optimization.

Do not design money transfer correctness around cached balances alone.

---

# 61. ITEM SERIALIZATION

Auction/delivery items must preserve:

* material
* quantity
* enchantments
* custom names
* lore
* components
* durability
* custom model data
* future-compatible metadata

Prefer official supported serialization mechanisms where possible.

Serialized items must include schema/version metadata.

---

# 62. CONFIGURATION

Primary config:

```
plugins/AnarchyCore/config.yml
```

Split large systems:

```
config.yml
economy.yml
auction.yml
bounties.yml
chat.yml
spawn.yml
database.yml
redis.yml
messages.yml
```

---

# 63. CONFIG EXAMPLE

```yaml
server:
  id: anarchy-01

economy:
  starting-balance: 0
  currency-symbol: "$"
  decimal-places: 2

auction:
  enabled: true
  listing-fee-percent: 1.0
  sale-fee-percent: 5.0
  max-listings-default: 10
  duration-hours: 48

bounties:
  enabled: true
  minimum: 10000

spawn:
  protection-radius: 96

voice:
  expected: true
```

Store monetary values internally as integer minor units even when configuration presents readable decimal values.

---

# 64. PERMISSIONS

Use LuckPerms as the permissions provider.

LuckPerms remains a dedicated permissions platform for Minecraft.

Groups:

```
default
supporter
moderator
admin
owner
```

Do not use Minecraft operator status as the normal permissions strategy.

Production staff should have only permissions required for their role.

---

# 65. COMMAND ARCHITECTURE

Commands should:

1. validate input
2. validate permission
3. gather safe contextual data
4. execute async service operation if needed
5. return to entity scheduler
6. update player
7. log result

Commands should NOT contain core business logic.

---

# 66. COMMANDS — LAUNCH

Player:

```
/help
/balance
/pay
/baltop

/ah
/ah sell
/ah search

/claim

/bounty
/bounty add

/stats
/stats <player>

/voiceinfo

/playtime
```

Admin:

```
/anarchy reload
/anarchy status

/eco

/auction admin

/playerdata

/serverhealth
```

---

# 67. API

Create internal public API module:

`anarchy-api`

Other trusted plugins can consume it.

Interfaces:

```
EconomyAPI
PlayerAPI
AuctionAPI
BountyAPI
StatisticsAPI
```

Do not expose implementation classes.

---

# 68. EVENT API

Custom events:

```
EconomyTransferEvent
AuctionListedEvent
AuctionPurchasedEvent
BountyCreatedEvent
BountyClaimedEvent
PlayerFirstJoinEvent
```

Carefully document thread context for every custom event.

---

# 69. LOGGING

Structured logs for:

* startup
* shutdown
* DB connectivity
* Redis connectivity
* transactions
* admin economy commands
* auction failures
* duplication detection
* unusual transaction behavior
* exceptions
* performance warnings

Never log:

* passwords
* database credentials
* Redis credentials
* proxy secrets

---

# 70. AUDIT LOG

Important actions should go into persistent audit storage.

Fields:

```
id
actor
action
target
details
server
timestamp
```

Examples:

```
ADMIN_BALANCE_SET
AUCTION_FORCE_CANCEL
PLAYER_DATA_INSPECTION
CONFIG_RELOAD
BAN
UNBAN
```

---

# 71. METRICS

Expose Prometheus-compatible metrics when practical.

Track:

### Minecraft

* TPS
* MSPT
* player count
* loaded chunks
* entities
* tick durations

### JVM

* heap
* GC
* threads
* CPU

### Database

* query duration
* connection pool usage
* transaction failures

### Economy

* total currency
* hourly currency created
* hourly currency destroyed
* transfer count
* auction volume
* median sale value

### Custom plugin

* command latency
* async queue depth
* cache hit rate
* errors

---

# 72. PERFORMANCE TARGETS

### Normal operation

TPS:

`20`

### MSPT

Desired average:

`< 40ms`

Target:

`< 25ms` where realistic.

### Economy command latency

Cached read:

`<100ms`

Database-backed transaction:

`<300ms typical`

Never block ticks while waiting.

---

# 73. LOAD TESTING

Before launch simulate:

* 25 players
* 50 players
* 100 players
* 200 players where hardware allows

Tests should include:

* chunk movement
* elytra
* entity farms
* redstone
* auctions
* payments
* deaths
* combat
* voice
* database load

---

# 74. PERFORMANCE PHILOSOPHY

Do not optimize by blindly disabling Minecraft mechanics.

Instead determine actual causes through profiling.

Use tools such as spark-compatible profiling.

Optimize:

* pathological farms
* entity density
* chunk loading
* view distance
* simulation distance
* plugin event handlers
* database interactions

Preserve vanilla gameplay wherever possible.

---

# 75. FARM POLICY

An economy server will incentivize extreme industrial farms.

Monitor:

* iron farms
* gold farms
* raid farms
* mob farms
* villager halls
* redstone clocks
* minecart systems

Do not arbitrarily ban efficient players.

Instead configure sensible technical limits when specific constructs threaten stability.

---

# 76. BACKUPS

Backups are mandatory.

Strategy:

### Database

Frequent PostgreSQL backups.

### World

Incremental filesystem snapshots where supported.

### Configurations

Git repository.

### Plugin source

GitHub/private Git repository.

### Offsite

At least one remote backup.

Never keep the only backup on the game server.

---

# 77. BACKUP TARGET

Suggested:

Database:

every 1-6 hours depending on production stage.

World:

daily minimum.

Critical config:

on change through Git.

Retention example:

```
24 hourly
14 daily
8 weekly
12 monthly
```

Adjust based on storage.

---

# 78. RESTORE TESTING

A backup is not considered valid until restoration is tested.

Create documented restore process for:

* database
* world
* server configuration
* plugin version

Run periodic restore tests.

---

# 79. DEPLOYMENT

Recommended host OS:

Ubuntu Server LTS or equivalent stable Linux distribution.

Core:

```
Linux
  |
Docker
  |
Pterodactyl Wings
  |
  +-- Velocity
  +-- Folia
  +-- Lobby
```

PostgreSQL and Redis may initially run on the same host but should be isolated logically and designed for migration.

---

# 80. DOCKER NETWORKING

Suggested private network:

```
minecraft_internal
```

Services:

```
velocity
anarchy
lobby
postgres
redis
```

Public:

```
Velocity TCP 25565
Simple Voice UDP configured port
```

Panel/web ports should use proper reverse proxy and TLS.

Postgres and Redis should NOT be publicly exposed.

---

# 81. REPOSITORY

Recommended repository:

```
anarchy-server
```

Branches:

```
main
develop
feature/*
fix/*
```

`main` should represent production-ready code.

---

# 82. CI

GitHub Actions or equivalent.

Pipeline:

```
checkout
 ↓
setup Java
 ↓
Gradle build
 ↓
unit tests
 ↓
static checks
 ↓
plugin artifact
```

Future:

```
integration tests
 ↓
staging deploy
 ↓
production deploy
```

---

# 83. TEST ENVIRONMENTS

Three environments:

### Local

Developer machine.

### Staging

Private test Minecraft network.

### Production

Public network.

Never develop experimental economy logic directly against production.

---

# 84. TEST STRATEGY

Mandatory unit tests:

### Money

* parsing
* formatting
* negative values
* overflow

### Economy

* transfer success
* insufficient funds
* simultaneous transfer
* duplicate request
* invalid account

### Auctions

* purchase
* double purchase
* cancellation
* expiration
* full inventory
* delivery

### Bounty

* creation
* escrow
* claim
* duplicate claim

### Serialization

* complex item round trip

---

# 85. EXPLOIT TESTING

Attempt:

* double clicking purchase
* duplicated command packets
* disconnect during auction purchase
* disconnect during trade
* server crash during transfer
* full inventory
* database timeout
* Redis unavailable
* seller offline
* buyer offline
* item modification
* negative amount
* integer overflow
* NaN-style parsing
* extremely large input
* alternate character inputs

Economy correctness must survive hostile users.

---

# 86. GRACEFUL FAILURE

If PostgreSQL becomes unavailable:

* prevent new financial mutations
* preserve Minecraft world functionality where safe
* notify administrators
* retry connections
* avoid guessing player balances

Example:

```
Economy services are temporarily unavailable.
Your balance has not been changed.
```

Do not allow money operations against uncertain state.

---

# 87. REDIS FAILURE

If Redis fails:

* player survival gameplay continues
* economy uses database
* network caches degrade
* cross-server features may temporarily stop

Redis failure must never erase wealth.

---

# 88. WEBSITE — FUTURE

Public website:

```
example.com
```

Potential pages:

```
/
play
rules
status
players
leaderboards
economy
market
bounties
stats
store
```

---

# 89. PUBLIC API — FUTURE

API:

```
api.example.com
```

Potential endpoints:

```
GET /server/status

GET /players/{uuid}

GET /leaderboards/money

GET /leaderboards/kills

GET /market

GET /market/{listing}

GET /bounties
```

Never expose private coordinates or sensitive staff information.

---

# 90. WEB STACK — FUTURE

Suggested:

```
Next.js / React
TypeScript
PostgreSQL read services
Redis cache
```

The web platform should not directly modify player balances.

Financial mutations should pass through trusted service boundaries.

---

# 91. ADMIN DASHBOARD — FUTURE

Staff dashboard:

* player lookup
* transaction history
* economy graph
* auction lookup
* bounty lookup
* server performance
* audit log
* moderation notes

High-risk actions require elevated permissions.

---

# 92. DISCORD — FUTURE

Potential integration:

* server status
* player count
* major bounties
* economy events
* announcements

Do not expose base coordinates.

---

# 93. MONETIZATION

Monetization must avoid pay-to-win where possible.

Possible:

* cosmetics
* chat colors
* name cosmetics
* Discord roles
* supporter badges
* cosmetic particles
* cosmetic titles

Potentially:

* queue priority

Be careful with gameplay advantages.

Minecraft commercial rules must be reviewed before monetization is finalized.

---

# 94. LAUNCH PLUGIN STACK

Keep small.

Required/likely:

### Proxy

Velocity

### Survival

Folia

### Permissions

LuckPerms

### Voice

Simple Voice Chat

### Pregeneration

Chunky or compatible alternative

### Profiling

spark or compatible profiler

### Compatibility

Only install ProtocolLib/PacketEvents-style libraries when something actually requires them.

### Custom

AnarchyCore

Avoid installing five plugins for functionality that belongs in AnarchyCore.

---

# 95. WHAT ANARCHYCORE SHOULD NOT REIMPLEMENT

Do not unnecessarily recreate:

* permission backend
* voice transport
* Minecraft proxy
* terrain generator
* JVM profiler

Build differentiating gameplay.

---

# 96. ANARCHYCORE MVP

Version:

`0.1.0`

Must contain:

### Platform

* startup
* shutdown
* configuration
* database pool
* migrations
* Redis
* scheduler abstraction
* metrics foundation

### Players

* player profiles
* first join
* join/leave tracking
* playtime

### Economy

* account
* balance
* payments
* transaction ledger
* admin economy commands

### Auctions

* listings
* sale
* expiration
* claim delivery

### Bounties

* create
* list
* claim
* escrow

### Statistics

* kills
* deaths
* playtime

### Utilities

* standardized messages
* permissions
* logging

---

# 97. MVP ACCEPTANCE CRITERIA

Server can be considered Alpha-ready when:

* Velocity works.
* Folia backend works.
* Modern forwarding works.
* PostgreSQL migrations execute automatically.
* Redis connects.
* Player accounts are automatically created.
* `/bal` works.
* `/pay` works atomically.
* Transaction history persists.
* `/ah sell` works.
* `/ah` purchases cannot double-sell.
* Offline seller receives money.
* Buyer receives item through safe delivery.
* `/bounty add` moves money to escrow.
* bounty payout occurs correctly.
* kill/death stats work.
* server survives PostgreSQL interruption without corrupting money.
* all custom Minecraft access is Folia-safe.
* backups work.
* staging environment exists.
* profiler shows no obvious AnarchyCore tick blocking.

---

# 98. DEVELOPMENT PHASE 0 — REPOSITORY

Cursor should first generate:

```
anarchy-server/
├── .github/
├── gradle/
├── anarchy-api/
├── anarchy-core/
├── anarchy-proxy/
├── database/
├── docker/
├── docs/
├── scripts/
├── .editorconfig
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── docker-compose.dev.yml
```

Do not implement gameplay before the build works.

---

# 99. DEVELOPMENT PHASE 1 — PLATFORM FOUNDATION

Implement:

1. Gradle multi-module project.
2. Java toolchain.
3. Folia API dependencies.
4. Velocity API dependencies.
5. configuration loader.
6. dependency injection/bootstrap.
7. structured logging.
8. scheduler wrapper.
9. database connection.
10. Flyway migrations.
11. Redis client.
12. health checks.

Deliverable:

Plugin starts and cleanly connects/disconnects infrastructure.

---

# 100. DEVELOPMENT PHASE 2 — PLAYER SERVICE

Implement:

```
PlayerProfile
PlayerRepository
PlayerService
PlayerJoinListener
PlayerQuitListener
```

Behavior:

First join:

```
UPSERT player
create economy account
```

Existing join:

```
update username
last_join
last_seen
```

Do DB asynchronously.

---

# 101. DEVELOPMENT PHASE 3 — ECONOMY

Build:

```
Money
Account
Transaction
EconomyRepository
TransactionRepository
EconomyService
EconomyCommand
PayCommand
BalanceCommand
```

Use explicit database transactions.

Then tests.

Do not proceed to auction house until economy tests pass.

---

# 102. DEVELOPMENT PHASE 4 — AUCTION HOUSE

Build:

```
AuctionListing
AuctionRepository
AuctionService
AuctionGUI
AuctionCommands
DeliveryService
```

Critical concurrency test:

Two buyers attempt to purchase same auction simultaneously.

Expected:

Exactly one succeeds.

---

# 103. DEVELOPMENT PHASE 5 — BOUNTIES

Implement:

```
Bounty
BountyRepository
BountyService
BountyCommands
CombatListener
```

Money enters escrow on creation.

Never create bounty before escrow succeeds.

---

# 104. DEVELOPMENT PHASE 6 — STATISTICS

Implement buffered statistics.

Do not issue:

```
UPDATE player_stats
```

for every block broken.

Instead:

```
memory counters
   ↓
periodic async batch
   ↓
PostgreSQL
```

Flush:

* periodically
* on disconnect
* on shutdown

---

# 105. DEVELOPMENT PHASE 7 — DEPLOYMENT

Create:

```
docker/
velocity/
folia/
postgres/
redis/
```

Provide:

* example environment variables
* healthchecks
* volume structure
* backup scripts
* firewall documentation

---

# 106. DEVELOPMENT PHASE 8 — STAGING

Staging test:

```
Velocity
   ↓
Folia
   ↓
Postgres
Redis
```

Run multiple simulated players.

Test:

* combat
* deaths
* voice
* economy
* auction race condition
* restart
* crash
* DB outage

---

# 107. DEVELOPMENT PHASE 9 — WORLD

Create production seed.

Configure:

* border
* spawn
* Nether
* End
* pregeneration

Pregenerate all initial accessible terrain.

Measure storage.

---

# 108. DEVELOPMENT PHASE 10 — CLOSED ALPHA

Suggested:

10-25 players.

Observe:

* economy
* farms
* PvP
* lag
* voice
* item scarcity
* travel
* base discovery
* auction prices

Do not artificially rebalance based on one unusual day.

Collect data.

---

# 109. DEVELOPMENT PHASE 11 — BETA

Target:

50-100 concurrent-capable environment.

Add:

* public website
* polished onboarding
* moderation tools
* alerting
* DDoS strategy
* production backups
* status page

---

# 110. DEVELOPMENT PHASE 12 — PUBLIC LAUNCH

Launch requirements:

* documented rules
* backups verified
* monitoring active
* database tested
* rollback procedure
* moderation permissions
* server status page
* issue reporting
* network firewall
* voice UDP verified
* world pregenerated

---

# 111. SCALING STRATEGY

Important:

One persistent Minecraft survival world cannot simply be distributed across unrelated Minecraft backend servers without fundamentally changing world architecture.

Primary world scaling is therefore:

**vertical + region parallelism**

through Folia.

Network scaling is:

**horizontal**

through Velocity.

---

# 112. SCALE STAGE 1

Target:

0-100 concurrent.

Possible single physical machine:

```
Velocity
Folia
PostgreSQL
Redis
Web
```

Strong modern CPU.

NVMe.

64 GB RAM recommended starting point for serious hosting.

---

# 113. SCALE STAGE 2

Target:

100-300 concurrent depending on workload.

Separate:

```
Machine A:
Velocity

Machine B:
Folia

Machine C:
PostgreSQL
Redis
Web
```

Actual thresholds must be metrics-driven.

---

# 114. SCALE STAGE 3

Large network:

```
Edge
 |
Velocity cluster
 |
+-------+---------+---------+
|       |         |         |
Lobby  Anarchy   Events    Other modes
         |
     dedicated host
         |
      Postgres
         |
       Redis
```

Anarchy survival remains one authoritative world unless a deliberate multi-world architecture is introduced.

---

# 115. HARDWARE PHILOSOPHY

Folia benefits from many strong CPU cores, and PaperMC's Folia guidance specifically notes large SMP-style populations and recommends substantial physical core counts for appropriate workloads.

Priorities:

1. strong modern CPU cores
2. sufficient physical cores
3. fast low-latency NVMe
4. enough RAM
5. good network
6. DDoS protection

Do not choose old server hardware merely because it has many weak cores.

---

# 116. RAM

Do not allocate every available GB to Minecraft.

Leave room for:

* OS
* filesystem cache
* PostgreSQL
* Redis
* Velocity
* monitoring
* Docker overhead

Example 64 GB server:

```
Folia       24 GB
Velocity     2 GB
Lobby        3 GB
Postgres     4 GB+
Redis        1 GB
OS/cache    remainder
```

Tune from actual measurements.

---

# 117. STORAGE

Use high-quality NVMe.

World storage should be monitored.

Track:

* total size
* region growth
* daily growth
* backup size
* I/O latency

Alert before disk usage becomes critical.

---

# 118. ALERTING

Create alerts for:

* server offline
* TPS degradation
* high MSPT
* disk >80%
* database unreachable
* Redis unreachable
* backup failure
* abnormal heap usage
* repeated plugin exceptions

Future notifications may use:

* Discord
* email
* Grafana alerting

---

# 119. SECURITY

Secrets belong in environment variables or secret storage.

Never commit:

```
DB_PASSWORD
REDIS_PASSWORD
VELOCITY_SECRET
API_KEYS
ADMIN_TOKENS
```

Add `.env` to `.gitignore`.

Provide `.env.example`.

---

# 120. DATABASE USER

Minecraft plugin should not connect as PostgreSQL superuser.

Create scoped account:

```
anarchy_app
```

Grant only necessary database privileges.

Backups use separate credentials.

---

# 121. FIREWALL

Public inbound:

```
22 SSH
25565/TCP Velocity
Voice UDP port
80/443 web where needed
```

SSH should ideally be restricted.

Do not expose:

```
PostgreSQL 5432
Redis 6379
Folia backend
Pterodactyl Wings internals
```

unless protected by appropriate private networking/firewall rules.

---

# 122. ADMIN SECURITY

Require:

* unique accounts
* strong passwords
* MFA where supported
* least privilege
* audit logging

Do not share one universal owner credential among staff.

---

# 123. MODERATION

Moderators should have access to:

* mute
* kick
* ban
* player lookup
* reports

Moderators should NOT automatically have:

* economy editing
* database access
* filesystem access
* console
* world editing

---

# 124. GAME MASTER PRINCIPLE

Staff should not interfere in legitimate survival conflicts.

Do not restore:

* raided bases
* stolen items
* lost inventories
* failed trades

unless loss resulted from:

* proven server bug
* administrative mistake
* severe exploit incident

Policies must be consistent.

---

# 125. ECONOMIC OBSERVABILITY

Create internal economic dashboard data:

```
Total money supply
Money supply 24h change
Money sources
Money sinks
Auction volume
Median sale price
Top traded materials
Wealth distribution
Top balances
```

This allows inflation to be managed with data.

---

# 126. ECONOMIC INTEGRITY

Never silently delete or create money.

Every system balance mutation must correspond to a transaction.

Useful accounting invariant:

```
Sum(account balances)
+
escrow
=
expected monetary supply
```

with system mint/burn transactions explaining changes.

Create reconciliation tooling.

---

# 127. RECONCILIATION

Admin command:

```
/eco reconcile
```

or offline maintenance task.

Checks:

* negative balances
* orphan transactions
* broken auctions
* invalid escrow
* duplicated listing claims
* ledger/account mismatch

Never automatically repair severe inconsistencies without logging.

---

# 128. PLAYER PRIVACY

Public endpoints should not disclose:

* IP address
* email
* private moderation notes
* base coordinates
* login metadata
* authentication information

Coordinates stored for technical/admin reasons remain private.

---

# 129. FUTURE SYSTEMS

After successful launch consider:

### Economy

* companies
* contracts
* shop stalls
* stockpiles
* banking
* loans

Loans require careful anti-abuse design.

### PvP

* bounty hunters
* kill streaks
* assassination contracts

### Social

* organizations
* reputation
* alliances

### World

* ruins
* special events
* rare world drops

### Website

* profiles
* economy graphs
* market browsing
* leaderboards

---

# 130. FEATURES TO AVOID EARLY

Do NOT initially build:

* cryptocurrencies
* blockchain
* complex stock exchange
* land claims
* 100 custom items
* custom RPG classes
* custom skill trees
* massive quest chains
* procedural dungeons
* separate currency types
* AI NPC economy
* player loans
* cross-world survival sharding

First prove:

**Anarchy + survival + economy + voice + performance is fun.**

---

# 131. PROJECT ROADMAP

## V0.1 — Infrastructure

* repository
* Gradle
* Folia plugin
* Velocity plugin
* PostgreSQL
* Redis
* migrations
* health
* logging

## V0.2 — Economy

* player accounts
* balances
* pay
* ledger
* admin commands

## V0.3 — Market

* auction house
* delivery
* history
* fees

## V0.4 — PvP Economy

* kills
* deaths
* bounties
* bounty payouts

## V0.5 — Stats

* player statistics
* leaderboards
* playtime

## V0.6 — Operations

* metrics
* alerts
* backups
* administration

## V0.7 — Alpha

* real players
* tuning
* bug fixing

## V0.8 — Web

* status
* profiles
* leaderboard
* market

## V0.9 — Beta

* polish
* scaling
* security
* load tests

## V1.0 — Public

Production launch.

---

# 132. FIRST CURSOR IMPLEMENTATION TASK

Cursor should begin with this exact objective:

> Create the initial production-quality multi-module Java/Gradle repository for the Anarchy Economy Minecraft network. Do not implement gameplay features yet. Establish the architecture that all future features will use.

Required modules:

```
anarchy-api
anarchy-core
anarchy-proxy
```

Requirements:

* Java 21+
* Gradle Kotlin DSL
* Folia API dependency for `anarchy-core`
* Velocity API for `anarchy-proxy`
* shared build conventions
* `.gitignore`
* `.editorconfig`
* README
* plugin metadata
* configuration system
* startup/shutdown lifecycle
* logging
* scheduler abstractions
* PostgreSQL connectivity
* HikariCP
* Flyway
* Redis client
* Docker Compose development environment
* JUnit
* Testcontainers where appropriate

Docker development services:

```
postgres
redis
```

Environment variables:

```
ANARCHY_DB_HOST
ANARCHY_DB_PORT
ANARCHY_DB_DATABASE
ANARCHY_DB_USERNAME
ANARCHY_DB_PASSWORD

ANARCHY_REDIS_HOST
ANARCHY_REDIS_PORT
ANARCHY_REDIS_PASSWORD

ANARCHY_SERVER_ID
```

Deliverables:

1. Entire project compiles.
2. Tests execute.
3. PostgreSQL launches from Docker Compose.
4. Redis launches from Docker Compose.
5. AnarchyCore initializes configuration.
6. AnarchyCore opens DB pool asynchronously.
7. Flyway runs migrations.
8. Redis connection initializes.
9. Health status can be queried.
10. Shutdown cleanly closes resources.
11. No blocking external I/O occurs on Minecraft region/entity tick threads.
12. README contains local development instructions.

Do not implement auction houses, bounties, companies or unrelated features during this task.

---

# 133. SECOND CURSOR IMPLEMENTATION TASK

After Phase 1 passes:

> Implement the player identity and persistence layer.

Create:

```
PlayerProfile
PlayerRepository
PostgresPlayerRepository
PlayerService
PlayerSessionService
```

Listeners:

```
PlayerJoinListener
PlayerQuitListener
```

Requirements:

* UUID is authoritative.
* Username updates automatically.
* first join persists.
* last join persists.
* last seen persists.
* DB calls asynchronous.
* disconnect cannot generate unhandled exceptions.
* database outage handled gracefully.
* unit/integration tests included.

---

# 134. THIRD CURSOR IMPLEMENTATION TASK

After Player Service passes:

> Implement a production-grade atomic economy system.

Build:

```
Money
Account
AccountType
Transaction
TransactionType
EconomyRepository
TransactionRepository
EconomyService
PostgresEconomyService

BalanceCommand
PayCommand
BalanceTopCommand
EconomyAdminCommand
```

Requirements:

* BIGINT minor units.
* no floating point.
* UUID accounts.
* PostgreSQL transaction.
* row locks where needed.
* no negative transfers.
* overflow protection.
* insufficient funds protection.
* self-payment policy defined.
* every balance change logged.
* administrative changes logged.
* concurrent payment tests.
* idempotency infrastructure.
* Folia-safe player messaging.

Do not begin Auction House until tests pass.

---

# 135. ENGINEERING DEFINITION OF DONE

A feature is NOT complete merely because it works once.

A feature is complete when:

* implementation exists
* configuration exists
* permissions exist
* invalid input handled
* failure modes handled
* logs exist
* tests exist
* concurrency considered
* Folia thread safety verified
* restart persistence verified
* documentation updated
* no known duplication exploit exists

---

# 136. PRODUCT SUCCESS CRITERIA

Technical success:

* stable TPS
* low latency
* no economy duplication
* minimal server crashes
* safe backups
* reliable restart
* hundreds of players technically achievable with scaling

Gameplay success:

* players trade regularly
* wealth matters
* travel matters
* bases matter
* raids matter
* proximity voice creates encounters
* player organizations emerge naturally
* market prices develop organically

Community success:

Players create stories that the developers did not explicitly script.

That is the central goal.

---

# 137. FINAL PRODUCT PRINCIPLE

Do not attempt to manufacture every interaction.

Provide systems that allow interactions to emerge.

Minecraft already provides:

* survival
* building
* destruction
* exploration
* combat
* logistics

This project adds:

* economic infrastructure
* persistent identity
* market infrastructure
* risk incentives
* player statistics
* social voice
* scalability
* operational reliability

The server should not feel like Minecraft buried beneath plugins.

It should feel like:

> **Minecraft where civilization, wealth, politics, trade and conflict emerge because the players created them.**
