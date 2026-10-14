# xPlayTime

Low-overhead Velocity proxy plugin that tracks player playtime in MariaDB/MySQL.

## Features

- In-memory session accrual (join/leave) with debounced batched flushes
- HikariCP connection pool + prepared/batched upserts
- Period queries: daily / weekly / monthly / yearly / total
- Website rollup table refreshed every ~30 minutes via one set-based SQL statement
- Public API for other plugins

## Requirements

- Velocity 4.2+ (Java 25+)
- MariaDB 10.5+ or MySQL 8+

## Build

```bash
cd xPlayTime
./gradlew build
```

Jar output: `build/libs/xPlayTime-1.0.0.jar`

## Install

1. Drop the jar into Velocity `plugins/`
2. Start once to generate `plugins/xplaytime/mysql.yml`
3. Create a database/user and fill in credentials
4. Restart (or `/playtime reload`)

### Sample MariaDB setup

```sql
CREATE DATABASE xplaytime CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xplaytime'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON xplaytime.* TO 'xplaytime'@'%';
FLUSH PRIVILEGES;
```

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/playtime` | `xplaytime.use` | Own overall playtime |
| `/playtime get <username>` | `xplaytime.get` | Lookup another player |
| `/playtime clear <username>` | `xplaytime.clear` | Wipe stored playtime |
| `/playtime daily\|weekly\|monthly\|yearly\|total` | `xplaytime.use` | Own period playtime |
| `/playtime reload` | `xplaytime.admin` | Reload `mysql.yml` |
| `/playtime rollup` | `xplaytime.admin` | Force website rollup refresh |

Default: give `xplaytime.use` to everyone; admin nodes as needed.

## Tables

- `xplaytime_players` — totals + last username
- `xplaytime_daily` — per-day buckets (period queries)
- `xplaytime_website_rollup` — denormalized daily→overall aggregates for websites

## Public API

```kotlin
import dev.xplaytime.api.PlayTimeApiProvider
import dev.xplaytime.api.PlayTimePeriod

val provider = server.pluginManager
    .getPlugin("xplaytime")
    .flatMap { it.instance }
    .orElse(null) as? PlayTimeApiProvider

val api = provider?.api
api?.getPlayTime(player.uniqueId, PlayTimePeriod.WEEKLY)
    ?.thenAccept { duration -> /* ... */ }
```

Compile against this project (or copy the `api` package) with `compileOnly`. Do not shade the API into dependents if both load on the same proxy — depend on the plugin instance instead.

## Performance notes

- Hot path never opens JDBC (join/leave only touch concurrent maps)
- Flushes are batched upserts with `rewriteBatchedStatements`
- Empty flush ticks are skipped when nothing is dirty
- Rollup is a single `INSERT … SELECT … ON DUPLICATE KEY UPDATE`, not per-player queries
