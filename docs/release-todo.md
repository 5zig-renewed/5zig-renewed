# Release rebuild queue

Published assets must be rebuilt and re-uploaded whenever a fix lands that changes the shipped jar.
A green working tree does not repair a download link — every tag listed here currently serves bytes from
before the fix named in the row.

Verified stance: after re-uploading a tag, compare `gh release view <tag> --json assets --jq '.assets[0].size'`
against the local jar size. Sizes must match exactly; a rebuilt jar differs by the platform class changes.

## Done

| range | reason | status |
|---|---|---|
| 1.21.9 / 1.21.10 / 1.21.11 (Fabric + NeoForge) | D7 blur crash, D8 `ResourceLocation` final | re-uploaded, sizes verified |

## Queued — D9 (background drawn twice, `Screen#render` owns it from 1.20.2)

Fatal from 1.21.2 (blur), cosmetic double-dim before that, so every asset in this range predates the fix:

| version | Fabric | NeoForge |
|---|---|---|
| 1.20.2 | todo | todo |
| 1.20.3 | todo | — |
| 1.20.4 | todo | todo |
| 1.20.5 | todo | — |
| 1.20.6 | todo | todo |
| 1.21 | todo | todo |
| 1.21.1 | todo | todo |
| 1.21.2 | todo | todo |
| 1.21.3 | todo | todo |
| 1.21.4 | todo | todo |
| 1.21.5 | todo | todo |
| 1.21.6 | todo | todo |
| 1.21.7 | todo | todo |
| 1.21.8 | todo | todo |

## Queued — D10 (platform resource-location wrapper cast)

Blocks the same tags for **1.21 … 1.21.5** on top of D9; do not rebuild those six until the D10 fix is in
the tree, or the republished asset still crashes on the first texture draw.

## Toolchain per era (do not guess — a wrong pair fails at plugin-apply, not compile)

| versions | Gradle | JDK |
|---|---|---|
| 1.20.2 – 1.20.4 (Fabric) | 8.10.2 | 17 |
| 1.20.5 – 1.21.4 (Fabric) | 8.10.2 | 21 |
| 1.21.5 – 1.21.8 (Fabric) | 8.14.3 | 21 |
| NeoForge 1.20.x | 8.14.3 | 21 |
| NeoForge 1.21.x | 8.14.3 | 21 |
| 26.x | 9.8.2 | 25 |
