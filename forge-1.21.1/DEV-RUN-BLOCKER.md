# Why `runClient` cannot load the mod (ForgeGradle 7)

The **release jar is fine** (18.8 MB, SRG-remapped, mixin refmap, 35/35 injection targets resolve
against the patched Forge client). Only the *dev run* is broken.

## Symptom

```
[FATAL] The following classes are missing, but are reported in the mods.toml: [the5zigmod]
net.minecraftforge.fml.ModLoadingException: The Mod File .../build/resources/main has mods that were
not found
```

FML scans `build/resources/main` — which contains `META-INF/mods.toml` but no classes — concludes the
mod id exists without a `@Mod` class, and aborts.

## What was tried

| Attempt | Result |
|---|---|
| `minecraft { mods { the5zigmod { sourceSet sourceSets.main } } }` | No such DSL. Introspecting the extension shows only `dependency`, `mappings`, `hasAccessTransformersPlugin`. |
| `-Dfml.modFolders=the5zigmod=<classes>:<resources>` | Accepted by the launcher (verified in `--info` JVM args) but ignored; same error. FG7 runs on the **Slime Launcher**, which builds its mod model from its own configuration. |
| `;` separator instead of `:` | Same error (FML splits on `File.pathSeparator`). |

## Routes that should work

1. **Packaged-jar test** — install Forge 1.21.1 into a real client and drop the built jar into `mods/`.
   This is the same test used for the Fabric jars and is the honest one anyway.
2. **FG6** for a verification-only harness (`net.minecraftforge.gradle` 6.0.54 does expose the
   `mods { }` block and configures 1.21.1 fine), accepting that FG6 emits no mixin refmap, so it is
   dev-only and must not produce release artifacts.

Forge 1.20.1 (`forge/`) is unaffected: FG6 + the working `mods` block, client-verified.
