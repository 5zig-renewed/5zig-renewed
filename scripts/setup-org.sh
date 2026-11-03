#!/usr/bin/env bash
# Creates the organisation repos and pushes the byte-for-byte mirrors of 5zig-reborn.
# Requires: gh authenticated as the org owner, and ~/work/5zig-mirrors/*.git (git --mirror clones).
set -euo pipefail

ORG="${ORG:-5zig-renewed}"
MIRRORS="${MIRRORS:-$HOME/work/5zig-mirrors}"
PORT_REPO="${PORT_REPO:-$HOME/work/5zig-renewed}"

say() { printf '\n== %s\n' "$*"; }

say "organisation $ORG"
gh api "orgs/$ORG" --jq '.login + " / " + (.name // "")' || {
    echo "Org $ORG does not exist yet — create it at https://github.com/organizations/plan (Free)."; exit 1; }

say "mirrors"
for d in "$MIRRORS"/*.git; do
    name="$(basename "$d" .git)"
    if gh repo view "$ORG/$name" >/dev/null 2>&1; then
        echo "  $name: exists, updating"
    else
        gh repo create "$ORG/$name" --public \
            --description "Mirror of 5zig-reborn/$name (read-only upsteam snapshot)" >/dev/null
        echo "  $name: created"
    fi
    git -C "$d" push --mirror "https://github.com/$ORG/$name.git" 2>&1 | tail -1
    # restore upstream default branch name
    def="$(git -C "$d" symbolic-ref --short HEAD 2>/dev/null || echo master)"
    gh api -X PATCH "repos/$ORG/$name" -f default_branch="$def" >/dev/null || true
    gh repo edit "$ORG/$name" --add-topic 5zig --add-topic mirror >/dev/null 2>&1 || true
done

say "port repository"
if gh repo view "$ORG/5zig-renewed" >/dev/null 2>&1; then
    echo "  5zig-renewed: exists"
else
    gh repo create "$ORG/5zig-renewed" --public \
        --description "The 5zig Mod renewed for Minecraft 1.16-26.3 on Fabric, NeoForge and Forge" >/dev/null
    echo "  5zig-renewed: created"
fi
cd "$PORT_REPO"
git init -q 2>/dev/null || true
git add -A
git -c user.email=noreply@example.com -c user.name=maintainer commit -qm "Initial import: 5zig core + 1.20.1 Fabric port skeleton" 2>/dev/null || true
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$ORG/5zig-renewed.git"
git branch -M main
git push -u origin main 2>&1 | tail -2

say "done"
gh repo list "$ORG" --limit 50
