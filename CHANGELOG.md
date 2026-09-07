# Changelog

## 0.7.a-dev

- Replaced only the persisted structure-generated Witch add in Minecraft 26.2 Swamp Huts with the canonical TradeEverything merchant while preserving the vanilla Cat path.
- Removed the custom jigsaw Trading Post, its resources, template generator, terrain placement path, and `/tre place` command.
- Added registry-driven maximum-level vanilla enchanted-book Buy variants with typed stored-enchantment components and server-validated variant IDs.
- Expanded the searchable screen to nine visible rows and centralized the right-detail panel rows to avoid text/control overlap.
- Added persistent, server-authoritative TradeEverything transaction advancements.

## 0.6.a-dev

- Filled Shulker Boxes now sell validated contents while retaining an empty shell; empty boxes still sell normally.
- Changed accelerated quantity controls to 64-item Shift and 32-item Shift+Control boundaries.
- Reduced the proven Fabric API baseline and replaced the Loom snapshot with the matching stable release.

## 0.5.b-dev

- Fixed GitHub Actions artifact uploads so the mod JAR path follows the active project version automatically.
- Added Dependabot monitoring for Gradle and GitHub Actions dependencies.

## 0.5.a-dev

- Added unified Emerald and Emerald Block purchase currency with exact automatic change.
- Added Shift quantity adjustment and atomic filled-Shulker selling with validated contents.
- Moved Buy affordability feedback to the action button text color.

## [Unreleased]

- Set the active development version to `0.4.b-dev`.
- Made Buy and Sell inventory mutations atomic through copy, simulation, and commit.
- Fixed BUY/SELL mode cache invalidation and reduced Sell refresh work to one inventory aggregation plus one catalog intersection.
- Updated visible author branding to COSHIAN while retaining the working repository URL.
- Set the active development version to `0.2.a-dev`.
- Fixed focused catalog-search keyboard routing and added localized search placeholders.
- Renamed the administrative command root from `/tradeeverything` to `/tre`.
- Added `/tre summon [<x> <y> <z>]` for standalone canonical TradeEverything merchants.
- Replaced numbered category/page clerks with one canonical merchant per Trading Post.
- Added a client-side searchable catalog screen with localized-name and registry-ID filtering.
- Added bounded catalog synchronization and server-authoritative purchase payloads with session, distance, payment, capacity, and stale-version validation.
- Added safe legacy page-clerk migration and reduced new structure templates to one merchant marker.
- Restricted the catalog to audited Survival-obtainable vanilla items with centralized JSON-compatible enable/price/quantity metadata.
- Added extensionless `config/config` loading with automatic defaults, field-level validation, partial overrides, and malformed-file fallback.
- Made TradeEverything villagers stationary with persistent anchors, vanilla `NoAI`, and collision correction.
- Made jigsaw placement footprint-aware, rejecting unsuitable terrain and adding a reproducible vanilla-block foundation.
- Replaced custom entities and structure registries with vanilla villagers and a vanilla jigsaw/template structure.
- Added safe reload, persistence/idempotence checks, and client/common compatibility tests.

## 0.1.0 - 2026-08-28

- Initial production release target for Minecraft 26.2.
