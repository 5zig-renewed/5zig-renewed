#!/usr/bin/env bash
# Fills organisation + repository metadata and restores upstream default branches.
set -uo pipefail
ORG=5zig-renewed

echo "== default branches"
for name in 5zig-fabric 5zig-reborn.github.io DiscordIPC ForgeGradle Mixin MixinGradle The-5zig-Mod deployments documentation multimc-bundler plugin-api server-api translations version-support; do
  def=$(curl -s "https://api.github.com/repos/5zig-reborn/$name" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("default_branch","master"))')
  gh api -X PATCH "repos/$ORG/$name" -f default_branch="$def" >/dev/null 2>&1
  printf '  %-24s -> %s\n' "$name" "$def"
done

echo
echo "== org metadata"
gh api -X PATCH "orgs/$ORG" \
  -f name="5zig Renewed" \
  -f description="The 5zig Mod, renewed for Minecraft 1.16-26.3 on Fabric, NeoForge and Forge. GPL-3.0 continuation of 5zig Reborn." \
  -f blog="https://github.com/$ORG/5zig-renewed" \
  -f location="Europe" \
  -f email="" >/dev/null && gh api "orgs/$ORG" --jq '{login,name,description,blog,location}'

echo
echo "== mirror metadata"
for name in 5zig-fabric 5zig-reborn.github.io DiscordIPC ForgeGradle Mixin MixinGradle The-5zig-Mod deployments documentation multimc-bundler plugin-api server-api translations version-support; do
  gh repo edit "$ORG/$name" \
    --description "Mirror of 5zig-reborn/$name - upstream snapshot. Active development: $ORG/5zig-renewed" \
    --add-topic 5zig --add-topic mirror --add-topic upstream >/dev/null 2>&1
done

echo "== port repo metadata"
gh repo edit "$ORG/5zig-renewed" \
  --description "The 5zig Mod, renewed for Minecraft 1.16-26.3 on Fabric, Neoforge and Forge (GPL-3.0)" \
  --add-topic minecraft --add-topic minecraft-mod --add-topic fabric --add-topic neoforge \
  --add-topic forge --add-topic hud --add-topic client-mod --add-topic 5zig \
  --enable-issues --enable-wiki=false >/dev/null 2>&1
gh repo view "$ORG/5zig-renewed" --json name,description,repositoryTopics,homepageUrl,defaultBranchRef --jq '{name,description,topics:[.repositoryTopics[].name],default:.defaultBranchRef.name}'

echo
echo "== org repo list"
gh repo list "$ORG" --limit 50 --json name,defaultBranchRef,description \
  --jq '.[] | "\(.name)\t\(.defaultBranchRef.name)\t\(.description)"' | sort
