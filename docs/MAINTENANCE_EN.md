# Maintenance Notes

This fork is meant to be maintained, not just patched once and left behind.

## Current Priorities

The project currently prioritizes:

- Keeping Fox kifu sync working through **Fox ID** input
- Shipping clear, practical multi-platform release packages
- Preserving the strongest LizzieYzy features while keeping the basic user flow reliable
- Reducing setup friction for first-time users

## Supported Direction

The main maintained user flow is:

1. Install the app
2. Launch it successfully
3. Use the Fox sync entry
4. Enter a numeric Fox ID
5. Fetch the latest public Fox games

## Terminology

To avoid confusion, the project now standardizes on:

- `Fox ID` instead of ambiguous UID wording
- `with-katago`
- `without.engine`

## Release Philosophy

Releases should be easy to understand before users download anything.

That means:

- clear per-platform asset names
- clear separation between bundled-engine and no-engine packages
- no extra package types that confuse ordinary users

## Highest-Priority Fixes

If maintenance time is limited, fix these first:

1. App cannot launch
2. Fox sync cannot fetch public games
3. Release assets do not match README guidance
4. Bundled KataGo is broken in all-in-one packages
5. UI or docs still mislead users about Fox ID input

## Related Docs

- [Installation Guide](INSTALL_EN.md)
- [Troubleshooting](TROUBLESHOOTING_EN.md)
- [Package Overview](PACKAGES_EN.md)
