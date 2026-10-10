# Changelog

What changed in ColorfulLeaves, one section per version, newest first. The
release notes on GitHub and Modrinth come from here (`.github/notizen.sh`).

To release: raise `version` in `gradle.properties`, add a section `## X.Y.Z`
below, merge, then push the tag `vX.Y.Z`. The Release workflow builds the jar
and drafts a GitHub release; publishing it uploads the version to Modrinth.

Pull requests go against main only and need its latest state: the check
"Basis aktuell" (`.github/workflows/basis.yml`) fails otherwise. A pull
request built on another stays a draft until that one is merged.

## 1.0.0

- **Leaf colours from the server:** the server says which leaves get which colour, per block; without the mod, leaves look as usual.
- **More leaves:** azalea, cherry, pale oak and poplar leaves take colours too.
- **Apache-2.0,** with LICENSE and NOTICE inside the jar.
