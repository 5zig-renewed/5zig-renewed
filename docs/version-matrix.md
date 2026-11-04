# Version matrix

Generated from the real build directories and the GitHub release list, not hand-maintained.

Fabric is the primary loader for all 40 versions; NeoForge covers 1.20.2+ and Forge stops at 1.20.1
(per the 2026-11-04 loader decision). **built** = a jar exists. **verified** = that jar was booted in a
real headless client and the 5zig HUD rendered.

¹ Fabric API for the `1.16` line declares `minecraft: ~1.16.2-alpha.20.28.a`, so Fabric refuses to load
the API on 1.16.0/1.16.1 and our mod hard-depends on it. The jars build but cannot run.

| # | MC | Java | Fabric | NeoForge | Forge |
|---|---|---|---|---|---|
| 1 | 1.20.1 | 17 | verified | built | built |
| 2 | 1.21.1 | 21 | verified | verified | — |
| 3 | 1.21.11 | 21 | verified | built | — |
| 4 | 1.21.4 | 21 | verified | verified | — |
| 5 | 1.21.8 | 21 | verified | built | — |
| 6 | 26.1.2 | 25 | verified | verified | — |
| 7 | 1.21 | 21 | verified | verified | — |
| 8 | 1.21.10 | 21 | verified | verified | — |
| 9 | 1.21.5 | 21 | verified | verified | — |
| 10 | 26.2 | 25 | verified | — | — |
| 11 | 1.20.4 | 17 | verified | built | — |
| 12 | 1.21.7 | 21 | verified | — | — |
| 13 | 1.21.6 | 21 | verified | — | — |
| 14 | 1.21.9 | 21 | verified | — | — |
| 15 | 1.21.3 | 21 | verified | — | — |
| 16 | 1.19.2 | 17 | verified | — | — |
| 17 | 26.1 | 25 | verified | — | — |
| 18 | 1.21.2 | 21 | verified | — | — |
| 19 | 1.20.2 | 17 | verified | built | — |
| 20 | 26.1.1 | 25 | verified | — | — |
| 21 | 1.20.6 | 21 | verified | built | — |
| 22 | 1.20 | 17 | verified | — | — |
| 23 | 1.20.3 | 17 | verified | — | — |
| 24 | 26.3 | 25 | verified | — | — |
| 25 | 1.19.4 | 17 | verified | — | — |
| 26 | 1.18.2 | 17 | verified | — | — |
| 27 | 1.20.5 | 21 | verified | — | — |
| 28 | 1.19.3 | 17 | verified | — | — |
| 29 | 1.19 | 17 | verified | — | — |
| 30 | 1.16.5 | 8 | verified | — | — |
| 31 | 1.19.1 | 17 | verified | — | — |
| 32 | 1.18.1 | 17 | verified | — | — |
| 33 | 1.17.1 | 16 | verified | — | — |
| 34 | 1.18 | 17 | verified | — | — |
| 35 | 1.17 | 16 | verified | — | — |
| 36 | 1.16.4 | 8 | verified | — | — |
| 37 | 1.16.3 | 8 | verified | — | — |
| 38 | 1.16.2 | 8 | verified | — | — |
| 39 | 1.16.1 | 8 | unloadable¹ | — | — |
| 40 | 1.16 | 8 | unloadable¹ | — | — |
