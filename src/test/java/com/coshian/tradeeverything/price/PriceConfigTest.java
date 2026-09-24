package com.coshian.tradeeverything.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.coshian.tradeeverything.advancement.AllItemsProgression;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PriceConfigTest {
	@TempDir Path temporaryDirectory;
	private static final Map<String, Integer> ITEMS = Map.ofEntries(
		Map.entry("diamond", 64), Map.entry("elytra", 1), Map.entry("cobblestone", 64), Map.entry("oak_log", 64),
		Map.entry("diamond_sword", 1), Map.entry("ender_pearl", 16), Map.entry("stone", 64),
		Map.entry("netherite_ingot", 64), Map.entry("dragon_egg", 64), Map.entry("nether_star", 64),
		Map.entry("enchanted_golden_apple", 64), Map.entry("barrier", 64));
	private static final PriceConfig.ItemLookup LOOKUP = id -> id.getNamespace().equals("minecraft") ? ITEMS.get(id.getPath()) : null;

	@Test void missingConfigurationCreatesExactFilenameWithValidDefaults() throws Exception {
		Path config = temporaryDirectory.resolve(PriceConfig.FILE_NAME);
		PriceConfig.LoadResult result = PriceConfig.parse(config, true, LOOKUP);
		assertEquals("config", config.getFileName().toString());
		assertTrue(result.created());
		assertTrue(Files.isRegularFile(config));
		assertTrue(result.status().healthy());
		assertEquals(24, price(result, "diamond").emeraldValue());
	}

	@Test void bundledDefaultsUseModernBuyRulesWithoutExplicitSellRules() throws Exception {
		Path config = temporaryDirectory.resolve(PriceConfig.FILE_NAME);
		PriceConfig.LoadResult result = PriceConfig.parse(config, true, LOOKUP);
		var root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
		assertTrue(root.has("language") && root.has("protect_npcs") && root.has("catalog_version"));
		assertFalse(root.has("structure_spacing") || root.has("structure_separation"));
		var expected = Map.of("diamond", 24, "netherite_ingot", 144, "elytra", 288, "dragon_egg", 576, "nether_star", 288, "enchanted_golden_apple", 128);
		for (var entry : expected.entrySet()) {
			var rule = root.getAsJsonObject("items").getAsJsonObject("minecraft:" + entry.getKey());
			assertTrue(rule.has("buy"));
			assertFalse(rule.has("emeralds") || rule.has("output") || rule.has("sell"));
			assertEquals(entry.getValue().intValue(), rule.getAsJsonObject("buy").get("emeralds").getAsInt());
			assertEquals(1, rule.getAsJsonObject("buy").get("output").getAsInt());
			assertEquals(new PriceConfig.Price(entry.getValue(), 1), price(result, entry.getKey()));
			assertEquals(SellPricing.sellOfferFor(entry.getValue()), PriceConfig.sellOffer(result.snapshot(), id(entry.getKey()), entry.getValue()));
		}
	}

	@Test void validConfigurationMergesIndependentOptionalItemFields() throws Exception {
		Path config = write("""
			{
			  "catalog_version": 91,
			  "items": {
			    "minecraft:diamond": {"enabled": false},
			    "minecraft:elytra": {"emeralds": 200},
			    "minecraft:cobblestone": {"output": 16},
			    "minecraft:oak_log": {"enabled": true, "emeralds": 3, "output": 4}
			  }
			}
			""");
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, LOOKUP);
		assertTrue(result.status().healthy());
		assertFalse(PriceConfig.isEnabled(result.snapshot(), id("diamond")));
		assertEquals(new PriceConfig.Price(24, 1), price(result, "diamond"), "omitted fields retain bundled values");
		assertEquals(new PriceConfig.Price(200, 1), price(result, "elytra"));
		assertEquals(new PriceConfig.Price(2, 16), price(result, "cobblestone"));
		assertEquals(new PriceConfig.Price(3, 4), price(result, "oak_log"));
		assertEquals(91, result.snapshot().catalogVersion());
	}

	@Test void unknownIdsAndInvalidNumbersAreIgnoredPerField() throws Exception {
		Path config = write("""
			{
			  "items": {
			    "minecraft:this_item_does_not_exist": {"enabled": true},
			    "minecraft:diamond": {"enabled": false, "emeralds": 0, "output": -1},
			    "minecraft:elytra": {"emeralds": -1, "output": 0},
			    "minecraft:cobblestone": {"emeralds": "not-a-number", "output": 65},
			    "minecraft:oak_log": {"emeralds": 1.5}
			  }
			}
			""");
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, LOOKUP);
		assertFalse(result.status().healthy());
		assertEquals(8, result.status().rejected());
		assertFalse(PriceConfig.isEnabled(result.snapshot(), id("diamond")), "valid enabled field still applies");
		assertEquals(new PriceConfig.Price(24, 1), price(result, "diamond"));
		assertEquals(new PriceConfig.Price(288, 1), price(result, "elytra"));
		assertEquals(new PriceConfig.Price(2, 1), price(result, "cobblestone"));
	}

	@Test void malformedConfigurationIsPreservedAndFallsBackToBundledDefaults() throws Exception {
		String malformed = "{\"items\": {\"minecraft:diamond\":";
		Path config = write(malformed);
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, LOOKUP);
		assertFalse(result.status().healthy());
		assertEquals(malformed, Files.readString(config));
		assertEquals(new PriceConfig.Price(24, 1), price(result, "diamond"));
		assertTrue(PriceConfig.isEnabled(result.snapshot(), id("diamond")));
	}

	@Test void stackAwareFallbacksRemainUnchanged() throws Exception {
		PriceConfig.LoadResult result = PriceConfig.parse(temporaryDirectory.resolve("absent"), false, LOOKUP);
		assertEquals(new PriceConfig.Price(12, 1), price(result, "diamond_sword"));
		assertEquals(new PriceConfig.Price(6, 1), price(result, "ender_pearl"));
		assertEquals(new PriceConfig.Price(2, 1), price(result, "stone"));
	}

	@Test void modernBuySellRulesAreIndependentAndOverrideLegacyBuyFields() throws Exception {
		Path config = write("""
			{"items":{"minecraft:diamond":{"emeralds":99,"output":9,"buy":{"emeralds":24,"output":1},"sell":{"items":4,"emeralds":3}}}}
			""");
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, LOOKUP);
		PriceConfig.ItemRule rule = result.snapshot().items().get(id("diamond"));
		assertEquals(new PriceConfig.BuyRule(24, 1), rule.buy(), "Modern buy is authoritative when legacy fields coexist");
		assertEquals(new PriceConfig.SellRule(4, 3), rule.sell());
		assertEquals(new SellOffer(4, 3), PriceConfig.sellOffer(result.snapshot(), id("diamond"), 24));
	}

	@Test void legacyRulesRemainBuyOnlyAndConfiguredModIdsNeedLiveLookup() throws Exception {
		Path config = write("""
			{"items":{"minecraft:diamond":{"emeralds":24,"output":1},"examplemod:ruby":{"enabled":true,"buy":{"emeralds":8,"output":1},"sell":{"items":2,"emeralds":1}},"missingmod:ghost":{"enabled":true,"buy":{"emeralds":8,"output":1}}}}
			""");
		PriceConfig.ItemLookup lookup = id -> id.toString().equals("examplemod:ruby") ? 64 : LOOKUP.maxStack(id);
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, lookup);
		assertEquals(new PriceConfig.BuyRule(24, 1), result.snapshot().items().get(id("diamond")).buy());
		assertEquals(null, result.snapshot().items().get(id("diamond")).sell(), "Legacy fields never imply explicit Sell");
		assertEquals(new PriceConfig.BuyRule(8, 1), result.snapshot().items().get(Identifier.parse("examplemod:ruby")).buy());
		assertFalse(result.snapshot().items().containsKey(Identifier.parse("missingmod:ghost")), "Absent registry IDs are skipped safely");
	}

	@Test void malformedModernRulesAreRejectedWithoutConfigRewrite() throws Exception {
		String contents = "{\"items\":{\"minecraft:diamond\":{\"buy\":{\"emeralds\":0,\"output\":1},\"sell\":{\"items\":0,\"emeralds\":3}}}}";
		Path config = write(contents);
		PriceConfig.LoadResult result = PriceConfig.parse(config, false, LOOKUP);
		assertFalse(result.status().healthy());
		assertEquals(contents, Files.readString(config));
		assertEquals(new PriceConfig.Price(24, 1), price(result, "diamond"));
		assertEquals(null, result.snapshot().items().get(id("diamond")).sell());
	}

	@Test void sellOnlyRuleKeepsSafeAutomaticBuyFallback() throws Exception {
		PriceConfig.LoadResult result = PriceConfig.parse(write("{\"items\":{\"minecraft:stone\":{\"sell\":{\"items\":4,\"emeralds\":3}}}}"), false, LOOKUP);
		PriceConfig.ItemRule rule = result.snapshot().items().get(id("stone"));
		assertEquals(new PriceConfig.Price(2, 1), price(result, "stone"));
		assertEquals(new SellOffer(4, 3), PriceConfig.sellOffer(result.snapshot(), id("stone"), 2));
		assertEquals(new PriceConfig.SellRule(4, 3), rule.sell());
	}

	@Test void manyExplicitModRulesStayOutsideAllItemsProgression() throws Exception {
		PriceConfig.ItemLookup lookup = id -> id.getNamespace().equals("examplemod") || id.getNamespace().equals("anothermod") || id.getNamespace().equals("thirdmod") ? 64 : LOOKUP.maxStack(id);
		PriceConfig.LoadResult result = PriceConfig.parse(write("{\"items\":{\"examplemod:ruby\":{\"buy\":{\"emeralds\":8,\"output\":1}},\"anothermod:sapphire\":{\"buy\":{\"emeralds\":9,\"output\":1}},\"thirdmod:part\":{\"sell\":{\"items\":2,\"emeralds\":1}}}}"), false, lookup);
		var modded = result.snapshot().items().keySet().stream().filter(id -> !id.getNamespace().equals("minecraft")).toList();
		assertEquals(3, modded.size());
		assertTrue(modded.stream().noneMatch(id -> AllItemsProgression.key(id).isPresent()));
	}

	private Path write(String contents) throws Exception {
		Path config = temporaryDirectory.resolve(PriceConfig.FILE_NAME);
		Files.writeString(config, contents);
		return config;
	}
	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("minecraft", path); }
	private static PriceConfig.Price price(PriceConfig.LoadResult result, String path) { return PriceConfig.resolve(result.snapshot(), id(path), ITEMS.get(path)); }
}
