# Database

PostgreSQL is the authoritative store for all persistent state
(GDD sections 47-60). Redis is cache/messaging only.

## Migrations

Canonical Flyway migrations live inside the plugin so they ship with it and
run automatically at startup:

```
glyph-core/src/main/resources/db/migration/
└── V1__initial_schema.sql   (players, accounts, transactions)
```

Rules (GDD section 48):

- Every schema change is a new `V<n>__description.sql` file. Never edit an
  applied migration.
- Never change the production schema manually outside an emergency.

## Production user

The plugin connects as a scoped user (`glyph_app`), never a superuser
(GDD section 120). Grant it only DML + DDL on the `glyph` database. Backup
jobs use separate credentials.
