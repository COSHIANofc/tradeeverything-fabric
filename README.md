# TradeEverything

TradeEverything is a Fabric mod for Minecraft 26.2. In newly generated vanilla Swamp Huts, the Witch is replaced by a TradeEverything merchant. The hut and its Cat remain unchanged, and existing huts are not modified.

## Install

Install Fabric Loader 0.19.3 or newer, a compatible Fabric API 0.157.0+26.2 or newer, and TradeEverything 0.8.b-dev on both the client and server. Java 25 and Minecraft 26.2 are required. Put Fabric API and `tradeeverything-0.8.b-dev.jar` in each `mods` directory.

## Trading

The merchant offers Buy and Sell modes in one searchable catalog. Seven results are visible at once; use the mouse wheel to scroll. Search accepts item names and registry IDs, ignores leading/trailing spaces and case, and supports partial matches.

- Use the quantity controls to choose the trade amount; modifier keys keep their accelerated adjustments.
- Buy payments accept Emeralds and Emerald Blocks. One Emerald Block equals nine Emeralds. Loose Emeralds are used first and any change is returned as Emeralds.
- Each vanilla enchantment has one book entry. Choose any available level, including the single valid level for enchantments such as Mending.
- Potions are grouped by effect family. Choose an available potion variant and then Potion, Splash Potion, or Lingering Potion.

The server verifies prices, selected variants, payment, and inventory space before completing a trade.

## Configuration

The configuration file is `config/config`. Buy and Sell rules can be set independently:

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

`buy.emeralds` is the Emerald cost and `buy.output` is the number of items received per purchase. `sell.items` and `sell.emeralds` form a bundle: `items: 4` and `emeralds: 3` trades every four items for three Emeralds. Therefore, 4 and 8 items are valid quantities; 5 is not. If a rule has no `sell` block, the usual Sell price is used.

Older rules still work:

```json
{
  "enabled": true,
  "emeralds": 24,
  "output": 1
}
```

In this form, `emeralds` and `output` apply to Buy only. If both old fields and a modern `buy` block exist, the modern `buy` block takes priority. Existing configuration files are read as-is and are not automatically rewritten.

Eligible vanilla items are available automatically. Mod items require an enabled, explicit rule and must be installed. Unknown configured IDs are skipped safely. Configured mod items use their normal item form; custom component states are not created by configuration.

## Progress

Progress is awarded after successful merchant trades. For the all-items goal, eligible vanilla items count individually; all enchanted books count as one category; all potion variants and containers count as one category; and mod items do not change the requirement. The current requirement is 1411 units. Filled Shulker Box contents count when sold, while the retained empty box does not.

## Commands

Operator permission level 2 is required:

```text
/tre summon [<x> <y> <z>]
/tre verify
/tre reload
```

`/tre summon` creates a merchant for administration or testing. It does not change an existing Swamp Hut.

## License

Copyright 2026 COSHIAN. Licensed under the [Apache License 2.0](LICENSE).
