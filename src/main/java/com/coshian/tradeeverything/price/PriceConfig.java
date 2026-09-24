package com.coshian.tradeeverything.price;

import com.coshian.tradeeverything.TradeEverything;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Compatibility parser which normalizes legacy Buy fields and modern independent Buy/Sell rules once at load. */
public final class PriceConfig {
	public static final int MAX_EMERALD_VALUE = 584;
	public static final int MAX_SELL_BUNDLE = 576;
	public static final String FILE_NAME = "config";
	private static final String RESOURCE = "/data/tradeeverything/default_config.json";
	private static volatile Snapshot current = Snapshot.defaults();
	private PriceConfig() { }
	public static synchronized Status load() { return load(configPath(), true); }
	public static synchronized Status reload() { return load(configPath(), true); }
	public static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME); }
	public static synchronized Status load(Path config, boolean createIfMissing) { LoadResult result = parse(config, createIfMissing); current = result.snapshot(); return result.status(); }
	static LoadResult parse(Path config, boolean createIfMissing) { return parse(config, createIfMissing, PriceConfig::registeredMaxStack); }
	static LoadResult parse(Path config, boolean createIfMissing, ItemLookup items) {
		Mutable next = new Mutable(); int rejected;
		try { rejected = readBundled(next, items); }
		catch (Exception exception) { TradeEverything.LOGGER.error("Could not load bundled TradeEverything configuration", exception); Snapshot fallback = Snapshot.defaults(); return new LoadResult(fallback, new Status(false, 0, 1), false); }
		boolean created = false;
		if (Files.notExists(config)) {
			if (createIfMissing) try {
				Files.createDirectories(config.toAbsolutePath().getParent()); try (InputStream defaults = requiredResource()) { Files.copy(defaults, config); }
				created = true; TradeEverything.LOGGER.info("Created default TradeEverything item configuration at {}", config.toAbsolutePath());
			} catch (Exception exception) { rejected++; TradeEverything.LOGGER.warn("Could not create TradeEverything configuration {}: {}. Using bundled defaults.", config.toAbsolutePath(), diagnostic(exception)); }
		} else try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader); if (!parsed.isJsonObject()) throw new IllegalArgumentException("root value must be a JSON object");
			rejected += read(parsed.getAsJsonObject(), next, config.toAbsolutePath().toString(), items);
		} catch (Exception exception) { rejected++; TradeEverything.LOGGER.warn("Could not parse TradeEverything configuration {}: {}. The file was not changed; using bundled defaults.", config.toAbsolutePath(), diagnostic(exception)); }
		Snapshot snapshot = next.freeze(rejected); return new LoadResult(snapshot, new Status(rejected == 0, snapshot.items().size(), rejected), created);
	}
	private static int readBundled(Mutable next, ItemLookup items) throws IOException { try (Reader reader = new InputStreamReader(requiredResource(), StandardCharsets.UTF_8)) { JsonElement parsed = JsonParser.parseReader(reader); if (!parsed.isJsonObject()) throw new IOException("bundled configuration root is not an object"); return read(parsed.getAsJsonObject(), next, "bundled defaults", items); } }
	private static InputStream requiredResource() throws IOException { InputStream stream = PriceConfig.class.getResourceAsStream(RESOURCE); if (stream == null) throw new IOException("missing resource " + RESOURCE); return stream; }
	private static int read(JsonObject root, Mutable next, String source, ItemLookup items) {
		int rejected = 0;
		rejected += setInt(root, "catalog_version", 1, Integer.MAX_VALUE, value -> next.catalogVersion = value, source);
		rejected += setInt(root, "structure_spacing", 16, 4096, value -> next.spacing = value, source);
		rejected += setInt(root, "structure_separation", 1, 4095, value -> next.separation = value, source);
		if (next.separation >= next.spacing) { rejected++; next.separation = Math.max(1, next.spacing / 3); warn("structure_separation", source, "must be less than structure_spacing"); }
		if (root.has("language")) try { next.language = Language.valueOf(root.get("language").getAsString().toUpperCase(Locale.ROOT)); } catch (Exception exception) { rejected++; warn("language", source, "expected en_us or ja_jp"); }
		if (root.has("protect_npcs")) try { next.protectNpcs = root.get("protect_npcs").getAsBoolean(); } catch (Exception exception) { rejected++; warn("protect_npcs", source, "expected a boolean"); }
		if (!root.has("items")) return rejected;
		if (!root.get("items").isJsonObject()) { warn("items", source, "expected a JSON object"); return rejected + 1; }
		for (Map.Entry<String, JsonElement> raw : root.getAsJsonObject("items").entrySet()) {
			Identifier id; try { id = Identifier.parse(raw.getKey()); } catch (RuntimeException exception) { rejected++; warn(raw.getKey(), source, "invalid registry ID"); continue; }
			Integer maxStack = items.maxStack(id);
			if (maxStack == null) { rejected++; warn(raw.getKey(), source, "unregistered item ID; skipped"); continue; }
			if (!raw.getValue().isJsonObject()) { rejected++; warn(raw.getKey(), source, "expected a JSON object"); continue; }
			JsonObject value = raw.getValue().getAsJsonObject(); ItemRule inherited = next.items.getOrDefault(id, new ItemRule(true, null, null));
			Field<Boolean> enabled = optionalBoolean(value, "enabled", inherited.enabled(), source, id); rejected += enabled.rejected();
			BuyRule buy = inherited.buy();
			if (value.has("buy")) { Field<BuyRule> modern = modernBuy(value, buy, maxStack, source, id); buy = modern.value(); rejected += modern.rejected(); }
			else { Field<Integer> emeralds = optionalInt(value, "emeralds", buy == null ? null : buy.emeralds(), 1, MAX_EMERALD_VALUE, source, id); Field<Integer> output = optionalInt(value, "output", buy == null ? null : buy.output(), 1, maxStack, source, id); rejected += emeralds.rejected() + output.rejected(); if (emeralds.value() != null || output.value() != null) buy = new BuyRule(emeralds.value() == null ? fallbackBuy(maxStack).emeralds() : emeralds.value(), output.value() == null ? fallbackBuy(maxStack).output() : output.value()); }
			SellRule sell = inherited.sell(); if (value.has("sell")) { Field<SellRule> modern = modernSell(value, sell, source, id); sell = modern.value(); rejected += modern.rejected(); }
			next.items.put(id, new ItemRule(enabled.value(), buy, sell));
		}
		return rejected;
	}
	private static Field<BuyRule> modernBuy(JsonObject object, BuyRule fallback, int maxStack, String source, Identifier id) {
		if (!object.get("buy").isJsonObject()) { warn(id + ".buy", source, "expected an object"); return new Field<>(fallback, 1); }
		JsonObject buy = object.getAsJsonObject("buy"); Field<Integer> emeralds = optionalInt(buy, "emeralds", null, 1, MAX_EMERALD_VALUE, source, id); Field<Integer> output = optionalInt(buy, "output", null, 1, maxStack, source, id);
		if (emeralds.value() == null || output.value() == null) { warn(id + ".buy", source, "requires emeralds and output"); return new Field<>(fallback, emeralds.rejected() + output.rejected() + 1); }
		return new Field<>(new BuyRule(emeralds.value(), output.value()), emeralds.rejected() + output.rejected());
	}
	private static Field<SellRule> modernSell(JsonObject object, SellRule fallback, String source, Identifier id) {
		if (!object.get("sell").isJsonObject()) { warn(id + ".sell", source, "expected an object"); return new Field<>(fallback, 1); }
		JsonObject sell = object.getAsJsonObject("sell"); Field<Integer> items = optionalInt(sell, "items", null, 1, MAX_SELL_BUNDLE, source, id); Field<Integer> emeralds = optionalInt(sell, "emeralds", null, 1, MAX_EMERALD_VALUE, source, id);
		if (items.value() == null || emeralds.value() == null) { warn(id + ".sell", source, "requires items and emeralds"); return new Field<>(fallback, items.rejected() + emeralds.rejected() + 1); }
		return new Field<>(new SellRule(items.value(), emeralds.value()), items.rejected() + emeralds.rejected());
	}
	private static BuyRule fallbackBuy(int maximumStackSize) { int stack = Math.max(1, maximumStackSize); return new BuyRule(stack == 1 ? 12 : stack <= 16 ? 6 : 2, 1); }
	private static Field<Boolean> optionalBoolean(JsonObject object, String key, Boolean fallback, String source, Identifier id) { if (!object.has(key)) return new Field<>(fallback, 0); try { JsonElement value = object.get(key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(); return new Field<>(value.getAsBoolean(), 0); } catch (RuntimeException exception) { warn(id + "." + key, source, "expected a boolean"); return new Field<>(fallback, 1); } }
	private static Field<Integer> optionalInt(JsonObject object, String key, Integer fallback, int min, int max, String source, Identifier id) { if (!object.has(key)) return new Field<>(fallback, 0); try { JsonElement value = object.get(key); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(); int parsed = value.getAsBigDecimal().intValueExact(); if (parsed < min || parsed > max) throw new IllegalArgumentException(); return new Field<>(parsed, 0); } catch (RuntimeException exception) { warn(id + "." + key, source, "expected an integer from " + min + " through " + max); return new Field<>(fallback, 1); } }
	private static int setInt(JsonObject root, String key, int min, int max, java.util.function.IntConsumer setter, String source) { if (!root.has(key)) return 0; try { JsonElement element = root.get(key); if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(); int value = element.getAsBigDecimal().intValueExact(); if (value < min || value > max) throw new IllegalArgumentException(); setter.accept(value); return 0; } catch (RuntimeException exception) { warn(key, source, "expected an integer from " + min + " through " + max); return 1; } }
	private static void warn(String key, String source, String reason) { TradeEverything.LOGGER.warn("Ignoring invalid configuration field '{}' from {}: {}", key, source, reason); }
	private static String diagnostic(Exception exception) { String message = exception.getMessage(); return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message; }
	public static Price resolve(Item item) { return resolve(current, item); }
	static Price resolve(Snapshot snapshot, Item item) { return resolve(snapshot, BuiltInRegistries.ITEM.getKey(item), item.getDefaultMaxStackSize()); }
	static Price resolve(Snapshot snapshot, Identifier id, int maximumStackSize) { ItemRule exact = snapshot.items().get(id); BuyRule buy = exact == null ? null : exact.buy(); if (buy == null) buy = fallbackBuy(maximumStackSize); return new Price(buy.emeralds(), buy.output()); }
	public static SellOffer sellOffer(Item item, int buyPrice) { ItemRule rule = current.items().get(BuiltInRegistries.ITEM.getKey(item)); return rule != null && rule.sell() != null ? new SellOffer(rule.sell().items(), rule.sell().emeralds()) : SellPricing.sellOfferFor(buyPrice); }
	static SellOffer sellOffer(Snapshot snapshot, Identifier id, int buyPrice) { ItemRule rule = snapshot.items().get(id); return rule != null && rule.sell() != null ? new SellOffer(rule.sell().items(), rule.sell().emeralds()) : SellPricing.sellOfferFor(buyPrice); }
	public static boolean isEnabled(Item item) { return isEnabled(current, item); }
	static boolean isEnabled(Snapshot snapshot, Item item) { return isEnabled(snapshot, BuiltInRegistries.ITEM.getKey(item)); }
	static boolean isEnabled(Snapshot snapshot, Identifier id) { ItemRule rule = snapshot.items().get(id); return rule == null || rule.enabled(); }
	/** Used only after catalog lookup: non-vanilla items require this explicit rule and remain opt-in. */
	public static boolean isExplicitlyConfigured(Identifier id) { return current.items().containsKey(id); }
	public static Map<Identifier, ItemRule> configuredItems() { return current.items(); }
	public static boolean validValues(Item item, Integer emeralds, Integer output) { return (emeralds == null || emeralds >= 1 && emeralds <= MAX_EMERALD_VALUE) && (output == null || output >= 1 && output <= item.getDefaultMaxStackSize()); }
	public static Snapshot snapshot() { return current; }
	public static Status status() { return new Status(current.rejected() == 0, current.items().size(), current.rejected()); }
	static synchronized void installForTesting(Snapshot snapshot) { current = snapshot; }
	private static Integer registeredMaxStack(Identifier id) { return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id).getDefaultMaxStackSize() : null; }
	public enum Language { EN_US, JA_JP }
	public record Price(int emeraldValue, int outputCount) { }
	public record BuyRule(int emeralds, int output) { }
	public record SellRule(int items, int emeralds) { }
	public record ItemRule(boolean enabled, BuyRule buy, SellRule sell) { }
	public record Status(boolean healthy, int accepted, int rejected) { }
	public record Snapshot(Language language, boolean protectNpcs, int catalogVersion, int spacing, int separation, Map<Identifier, ItemRule> items, int rejected) { static Snapshot defaults() { return new Snapshot(Language.EN_US, true, 3, 40, 12, Map.of(), 0); } }
	static record LoadResult(Snapshot snapshot, Status status, boolean created) { }
	@FunctionalInterface interface ItemLookup { Integer maxStack(Identifier id); }
	private record Field<T>(T value, int rejected) { }
	private static final class Mutable { Language language = Language.EN_US; boolean protectNpcs = true; int catalogVersion = 3; int spacing = 40; int separation = 12; final Map<Identifier, ItemRule> items = new HashMap<>(); Snapshot freeze(int rejected) { return new Snapshot(language, protectNpcs, catalogVersion, spacing, separation, Map.copyOf(items), rejected); } }
}
