# Changelog

What changed in ColorfulLeaves, one section per version, newest first. The
release notes on GitHub and Modrinth come from here (`.github/notizen.sh`).

To release: raise `version` in `gradle.properties`, add a section `## X.Y.Z`
below, merge, then push the tag `vX.Y.Z`. The Release workflow builds the jar
and drafts a GitHub release; publishing it uploads the version to Modrinth.

Pull requests go against main only and need its latest state: the check
"Basis aktuell" (`.github/workflows/basis.yml`) fails otherwise. A pull
request built on another stays a draft until that one is merged.

## 1.1.0

- **Highlights:** coloured leaves shine a little, one highlight per leaf. A mask per kind of leaves, painted in Aseprite (`art/masks`), tells the single leaves of a texture apart.
- **Secondary colour:** with a secondary colour from the server, the leaves of a block spread between the two colours, leaf by leaf.
- **Blossoms:** with a blossom colour from the server, leaves bloom. Every kind has blossoms in its mask; cherry and flowering azalea take the colour on their own.
- **Version 2 of the leaf colours** on the new channel `colorfulleaves:leaves`, with secondary colours and blossoms; version 1 on `colorfulleaves:chunk` stays as it is.
- **Resource packs** whose leaf models bring textures of their own keep those parts as the pack draws them; only the leaves' own texture is coloured.

## 1.0.0

- **Leaf colours from the server:** the server says which leaves get which colour, per block; without the mod, leaves look as usual.
- **More leaves:** azalea, cherry, pale oak and poplar leaves take colours too.
- **Apache-2.0,** with LICENSE and NOTICE inside the jar.
