#!/usr/bin/env bash
# Writes the release notes: the bullets of the version's section in CHANGELOG.md, what the mod needs, links,
# publisher, contact and the Mojang notice from NOTICE. The same for GitHub and Modrinth.
# Usage in the repository: bash .github/notizen.sh <version>
set -euo pipefail
version=$1
repo=https://github.com/VonNekyia/colorful_leaves

punkte=$(awk -v kopf="## $version" '$0 == kopf {an = 1; next} /^## / {an = 0} an && NF' CHANGELOG.md)
[ -n "$punkte" ] || { echo "::error::CHANGELOG.md has no section \"## $version\" with bullets" >&2; exit 1; }

herausgeber=$(grep '^Publisher and responsible: ' NOTICE | head -1)
hinweis=$(sed -n '/^NOT AN OFFICIAL/,/^$/p' NOTICE | paste -sd ' ' | sed 's/ *$//')
[ -n "$herausgeber" ] && [ -n "$hinweis" ] \
  || { echo "::error::NOTICE names no publisher, contact or Mojang notice" >&2; exit 1; }

cat <<EOF
**What's new in $version**

$punkte

For Minecraft 26.3 with Fabric Loader and Fabric API. Leaves take colours only on servers that send them.

[All releases on GitHub]($repo/releases)

$herausgeber

$hinweis
EOF
