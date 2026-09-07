package com.coshian.tradeeverything.catalog;

import com.coshian.tradeeverything.price.PriceConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Server-owned catalog including one max-level stored-enchantment book per vanilla enchantment. */
public final class TradeCatalog {
	private static volatile List<Entry> entries = List.of();
	private static volatile List<Entry> enabledEntries = List.of();
	private static volatile Map<Key, Entry> enabledByKey = Map.of();
	private TradeCatalog() {}

	/** Tooling fallback has no dynamic enchantments; live servers always pass RegistryAccess. */
	public static synchronized void rebuild() { rebuild(null); }
	public static synchronized void rebuild(HolderLookup.Provider registries) {
		List<Entry> all = new ArrayList<>(); Map<Key, Entry> enabled = new HashMap<>();
		BuiltInRegistries.ITEM.forEach(item -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null || !id.getNamespace().equals("minecraft") || item == Items.ENCHANTED_BOOK) return;
			PriceConfig.Price price = PriceConfig.resolve(item);
			add(all, enabled, new Entry(id, null, item, null, SurvivalEligibility.isEligible(id) && PriceConfig.isEnabled(item), price.emeraldValue(), price.outputCount()));
		});
		if (registries != null) {
			var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
			PriceConfig.Price bookPrice = PriceConfig.resolve(Items.ENCHANTED_BOOK);
			boolean booksEnabled = SurvivalEligibility.isEligible(Items.ENCHANTED_BOOK) && PriceConfig.isEnabled(Items.ENCHANTED_BOOK);
			enchantments.listElements().forEach(holder -> {
				Identifier enchantmentId = holder.unwrapKey().orElseThrow().identifier();
				if (enchantmentId.getNamespace().equals("minecraft")) add(all, enabled, new Entry(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK), enchantmentId, Items.ENCHANTED_BOOK, holder, booksEnabled, bookPrice.emeraldValue(), 1));
			});
		}
		all.sort(Comparator.comparing((Entry entry) -> entry.id().toString()).thenComparing(entry -> entry.variantId() == null ? "" : entry.variantId().toString()));
		entries = List.copyOf(all); enabledEntries = entries.stream().filter(Entry::enabled).toList(); enabledByKey = Map.copyOf(enabled);
	}
	private static void add(List<Entry> all, Map<Key, Entry> enabled, Entry entry) { all.add(entry); if (entry.enabled()) enabled.put(entry.key(), entry); }
	public static List<Entry> entries() { return entries; }
	public static List<Entry> enabledEntries() { return enabledEntries; }
	public static Optional<Entry> enabled(Identifier id) { return enabled(id, null); }
	public static Optional<Entry> enabled(Identifier id, Identifier variantId) { return Optional.ofNullable(enabledByKey.get(new Key(id, variantId))); }
	public static int version() { return PriceConfig.snapshot().catalogVersion(); }

	public static ItemStack output(Entry entry) {
		ItemStack result = new ItemStack(entry.item());
		if (entry.enchantment() != null) {
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(entry.enchantment(), entry.enchantment().value().getMaxLevel());
			result.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		}
		return result;
	}
	public static Audit audit() {
		Set<Key> seen = new HashSet<>(); int duplicates = 0; boolean valid = true;
		for (Entry entry : enabledEntries) {
			if (!seen.add(entry.key())) duplicates++;
			valid &= entry.enabled() && entry.price() > 0 && entry.price() <= PriceConfig.MAX_EMERALD_VALUE && entry.quantity() > 0 && entry.quantity() <= entry.item().getDefaultMaxStackSize();
			if (entry.enchantment() != null) valid &= entry.id().equals(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK)) && entry.variantId().getNamespace().equals("minecraft") && entry.enchantment().value().getMaxLevel() >= 1;
		}
		return new Audit(entries.size(), enabledEntries.size(), entries.size() - enabledEntries.size(), duplicates, valid);
	}
	public record Key(Identifier id, Identifier variantId) {}
	public record Entry(Identifier id, Identifier variantId, Item item, Holder<Enchantment> enchantment, boolean enabled, int price, int quantity) { public Key key() { return new Key(id, variantId); } public boolean enchantedBook() { return enchantment != null; } }
	public record Audit(int registeredVanilla, int enabled, int disabled, int duplicates, boolean validEntries) { public boolean valid() { return duplicates == 0 && validEntries; } }
}
