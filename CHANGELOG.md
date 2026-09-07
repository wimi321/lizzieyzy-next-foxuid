# Changelog

All notable maintenance updates to this fork are documented here.

## Unreleased

- Allow fresh point evaluation after returning to an accepted ReadBoard position without another helper frame; validate position semantics and engine synchronization, and reject malformed or stale frame publication (#444).
- Preserve local moves after stopping ReadBoard synchronization and retire obsolete placement confirmations and queued output (#432).
- Preserved diagnostic log record boundaries and stack-trace whitespace during export without weakening privacy redaction (#431).
- Made diagnostic export estimates describe approximate uncompressed content, pruned proven irrelevant archives before reading using native Windows file identities where needed, and displayed publication success independently of folder opening (#430).
- Wait for confirmed engine positions before starting ordinary analysis after moves and history navigation; resume ReadBoard analysis once at the final synchronized position while preserving valid node caches (#429).
- Preserved complete custom match rules, distinguished official rule presets, clarified unverified participation, and enabled match-rule inspection from independent-board shortcuts (#422).
- Run custom local KataGo `benchmark` commands as slot-owned, cancellable tasks with streamed output and exit-code results (#423).
- Prevented KataGo rules dialogs from interrupting engine games while their participants remain occupied (#421).
- Added CI to build the shaded jar on push and pull request.
- Added Dependabot configuration for Maven dependencies and GitHub Actions.
- Added installation, troubleshooting, maintenance, and release-process docs.
- Added Japanese and Korean install guides.
- Unified the project wording around Fox nickname search and novice-friendly account wording in docs and UI strings.
- Polished the repository landing page, community health files, and multilingual README structure.
- Added internal release metadata generation and a validator that keeps public releases limited to the main novice-friendly assets.

## 2026-04-17 - Board Sync Entry Recovery

- Restored the Windows board sync entry so users can still open the feature even when the legacy native `readboard` folder is not bundled.
- Added a guided download prompt that opens the maintained `readboard` releases page when the legacy helper is missing.
- Added regression tests for the missing-helper prompt flow and the new localization keys.
- Historical tag at that time: `2.5.3-next-2026-04-17.1`

## 2026-03-16 - First Maintained Release

- Restored Fox kifu sync for the maintained fork.
- Switched the user-facing flow to Fox ID input instead of username lookup.
- Published practical multi-platform release assets, including Windows, macOS, and Linux packages.
- Added macOS Intel dmg packaging alongside Apple Silicon packaging.
- Bundled KataGo and default weights in the main all-in-one packages.
- Historical tag at that time: `2.5.3-next-foxuid-2026-03-16.2`
