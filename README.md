# TradeEverything

TradeEverything is a Fabric mod for Minecraft 26.2. In newly generated vanilla Swamp Huts, the structure-generated Witch is replaced with one canonical TradeEverything Villager merchant. The hut itself and its Cat remain vanilla; already-generated huts are deliberately unchanged.

The merchant provides a searchable, server-authoritative Buy/Sell screen for enabled, Survival-obtainable vanilla items. The list shows nine rows at once, supports scrolling and localized-name/registry-ID search, and includes one maximum-level enchanted-book listing for every vanilla enchantment. Search an enchantment by localized name or ID, for example `Mending`, `修繕`, or `minecraft:mending`.

## Requirements and installation

Minecraft 26.2, Java 25, Fabric Loader 0.19.3 or newer, Fabric API 0.157.0+26.2 or newer compatible version, and TradeEverything 0.7.a-dev are required on both client and server. Put Fabric API and `tradeeverything-0.7.a-dev.jar` in both `mods` directories.

## Commands

Operator commands require permission level 2:

```text
/tre summon [<x> <y> <z>]
/tre verify
/tre reload
```

`summon` is a debug/administrative way to create the same canonical merchant. It does not test Swamp Hut replacement.

## Progress

TradeEverything adds four hidden-to-discovery progress goals. They are awarded only after completed authoritative merchant transactions. The all-items objective records current enabled catalog item IDs in persistent world data, including contents sold from a filled Shulker Box; the retained box shell is not counted.

## Development

The validated minimum baseline is Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Fabric Loom 1.17.2, Gradle 9.7.1, and Java 25. Run `./gradlew clean build` to produce `build/libs/tradeeverything-0.7.a-dev.jar`.

## License

Copyright 2026 COSHIAN. Licensed under the [Apache License 2.0](LICENSE).
