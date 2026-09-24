# TradeEverything

TradeEverything is a Fabric mod for Minecraft 26.2. In newly generated vanilla Swamp Huts, only the structure-generated Witch is replaced with one canonical TradeEverything Villager merchant. The hut and its Cat remain vanilla, and already-generated huts are not changed.

The merchant provides a searchable, server-authoritative Buy/Sell screen. It shows exactly seven catalog rows at a time, supports scrolling, and matches trimmed, case-insensitive partial localized names and registry IDs. The right detail panel uses one shared layout for text and controls, including bounded long text.

## Requirements and installation

Minecraft 26.2, Java 25, Fabric Loader 0.19.3 or newer, a compatible Fabric API 0.157.0+26.2 or newer, and TradeEverything 0.8.b-dev are required on both client and server. Put Fabric API and `tradeeverything-0.8.b-dev.jar` in both `mods` directories.

## Trading

- Buy payments accept Emeralds and Emerald Blocks. One Emerald Block is worth nine Emeralds; loose Emeralds are used first and any change is returned as Emeralds.
- Enchanted books are one logical catalog entry per vanilla enchantment. Select a level from the synced valid range; the server creates and validates the stored-enchantment component.
- Potions are logical brewing-reachable families, not arbitrary effect combinations. Select one legal option plus Potion, Splash Potion, or Lingering Potion. The server resolves the real potion and constructs its potion contents.
- Search covers ordinary-item names/IDs, enchantment names/IDs, potion-family terms, legal potion IDs, and potion-container IDs. When the search field is focused, `E` types `e`; `Esc` still closes the screen normally.

All prices, variant selections, inventory capacity checks, and payment/inventory changes are validated by the server. Client requests never supply an authoritative item stack, potion contents, NBT, or components.

## Configuration

The configuration file is `config/config`. Item rules may use the modern independent Buy/Sell form:

```json
{
  "items": {
    "minecraft:diamond": {
      "enabled": true,
      "buy": {
        "emeralds": 24,
        "output": 1
      },
      "sell": {
        "items": 4,
        "emeralds": 3
      }
    },
    "examplemod:ruby": {
      "enabled": true,
      "buy": {
        "emeralds": 8,
        "output": 1
      },
      "sell": {
        "items": 1,
        "emeralds": 4
      }
    }
  }
}
```

`buy.emeralds` is the cost per purchase unit and `buy.output` is the server-created output count. `sell.items` and `sell.emeralds` form a bundle ratio: the diamond rule above trades 4 diamonds for 3 Emeralds, so 4 and 8 are valid quantities while 5 is rejected. If no explicit `sell` block exists, the existing derived `SellPricing` fallback remains in use.

The legacy form remains supported:

```json
{
  "enabled": true,
  "emeralds": 24,
  "output": 1
}
```

Legacy `emeralds` and `output` are Buy settings only. A modern `buy` block overrides legacy Buy fields when both are present. Loading an old configuration does not rewrite the user's file.

Vanilla eligible items remain automatic. Non-`minecraft` items are never auto-enumerated: they require an enabled explicit rule and a live registered item ID. Missing configured IDs are warned about and skipped. Generic configured mod items use their normal default stack; the mod does not invent arbitrary custom component state. Component-sensitive Sell matching is retained.

## Progress

TradeEverything adds four hidden-to-discovery progress goals, awarded only after completed authoritative merchant transactions. The all-items objective has a fixed vanilla canonical universe of 1411 units: ordinary eligible vanilla items contribute individually, all enchanted-book levels share one unit, all potion families/options/containers share one unit, and non-`minecraft` items contribute zero units. Filled Shulker Box contents are recorded; the retained box shell is not.

## Commands

Operator commands require permission level 2:

```text
/tre summon [<x> <y> <z>]
/tre verify
/tre reload
```

`summon` is an administrative/debug way to create the canonical merchant. It does not test Swamp Hut replacement.

## Development

The validated minimum baseline is Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Fabric Loom 1.17.2, Gradle 9.7.1, and Java 25. Run `./gradlew clean build` to produce `build/libs/tradeeverything-0.8.b-dev.jar`.

## License

Copyright 2026 COSHIAN. Licensed under the [Apache License 2.0](LICENSE).
