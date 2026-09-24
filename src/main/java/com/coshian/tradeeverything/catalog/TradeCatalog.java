package com.coshian.tradeeverything.catalog;

import com.coshian.tradeeverything.price.PriceConfig;
import com.coshian.tradeeverything.price.SellOffer;
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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Server-owned buy catalog. Variant objects retain all authoritative game data server-side. */
public final class TradeCatalog {
	private static volatile List<Entry> entries = List.of();
	private static volatile List<Entry> enabledEntries = List.of();
	private static volatile Map<Key, Entry> enabledByKey = Map.of();
	private TradeCatalog() { }

	/** Tooling fallback has no dynamic registries; live servers always provide them. */
	public static synchronized void rebuild() { rebuild(null); }
	public static synchronized void rebuild(HolderLookup.Provider registries) {
		List<Entry> all = new ArrayList<>();
		Map<Key, Entry> enabled = new HashMap<>();
		BuiltInRegistries.ITEM.forEach(item -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			// Potion containers have one bounded POTION-family route, never a generic BUY route.
			if (id == null || !id.getNamespace().equals("minecraft") || item == Items.ENCHANTED_BOOK || PotionFamilies.isContainer(item)) return;
			PriceConfig.Price price = PriceConfig.resolve(item);
			add(all, enabled, Entry.ordinary(id, item, SurvivalEligibility.isEligible(id) && PriceConfig.isEnabled(item), price.emeraldValue(), price.outputCount()));
		});
		// Non-vanilla items never auto-enumerate: only an enabled, registry-resolved explicit rule reaches the catalog.
		PriceConfig.configuredItems().forEach((id, rule) -> {
			if (id.getNamespace().equals("minecraft") || !rule.enabled() || !BuiltInRegistries.ITEM.containsKey(id)) return;
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item == Items.AIR || PotionFamilies.isContainer(item) || item == Items.ENCHANTED_BOOK) return;
			PriceConfig.Price price = PriceConfig.resolve(item);
			add(all, enabled, Entry.ordinary(id, item, true, price.emeraldValue(), price.outputCount()));
		});
		if (registries != null) {
			var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
			PriceConfig.Price bookPrice = PriceConfig.resolve(Items.ENCHANTED_BOOK);
			boolean booksEnabled = SurvivalEligibility.isEligible(Items.ENCHANTED_BOOK) && PriceConfig.isEnabled(Items.ENCHANTED_BOOK);
			enchantments.listElements().forEach(holder -> {
				Identifier enchantmentId = holder.unwrapKey().orElseThrow().identifier();
				if (enchantmentId.getNamespace().equals("minecraft")) {
					int minimum = 1;
					int maximum = holder.value().getMaxLevel();
					add(all, enabled, Entry.enchantment(enchantmentId, holder, minimum, maximum, minimum, booksEnabled, bookPrice.emeraldValue()));
				}
			});
			PriceConfig.Price potionPrice = PriceConfig.resolve(Items.POTION);
			boolean potionsEnabled = SurvivalEligibility.isEligible(Items.POTION) && PriceConfig.isEnabled(Items.POTION);
			for (PotionFamilies.Family family : PotionFamilies.discover(registries)) {
				add(all, enabled, Entry.potion(family, potionsEnabled, potionPrice.emeraldValue(), potionPrice.outputCount()));
			}
		}
		all.sort(Comparator.comparing((Entry entry) -> entry.id().toString()).thenComparing(entry -> entry.variantId() == null ? "" : entry.variantId().toString()));
		entries = List.copyOf(all);
		enabledEntries = entries.stream().filter(Entry::enabled).toList();
		enabledByKey = Map.copyOf(enabled);
	}
	private static void add(List<Entry> all, Map<Key, Entry> enabled, Entry entry) { all.add(entry); if (entry.enabled()) enabled.put(entry.key(), entry); }
	public static List<Entry> entries() { return entries; }
	public static List<Entry> enabledEntries() { return enabledEntries; }
	public static Optional<Entry> enabled(Identifier id) { return enabled(id, null); }
	public static Optional<Entry> enabled(Identifier id, Identifier variantId) { return Optional.ofNullable(enabledByKey.get(new Key(id, variantId))); }
	public static int version() { return PriceConfig.snapshot().catalogVersion(); }

	/** Central bounded resolver. It never accepts a client ItemStack or component map. */
	public static Optional<ItemStack> resolveOutput(Entry entry, TradeVariantSelection selection) {
		if (entry == null || selection == null) return Optional.empty();
		return switch (entry.variant().kind()) {
			case NONE -> selection.enchantmentLevel() == 1 && selection.potionOption() == TradeVariantSelection.NO_POTION_OPTION && selection.potionContainer() == TradeVariantSelection.PotionContainer.NORMAL
				? Optional.of(new ItemStack(entry.item())) : Optional.empty();
			case ENCHANTMENT -> resolveEnchantment(entry, selection);
			case POTION -> resolvePotion(entry, selection);
		};
	}
	private static Optional<ItemStack> resolveEnchantment(Entry entry, TradeVariantSelection selection) {
		if (!(entry.variant() instanceof EnchantmentVariant variant) || selection.potionOption() != TradeVariantSelection.NO_POTION_OPTION || selection.potionContainer() != TradeVariantSelection.PotionContainer.NORMAL
			|| selection.enchantmentLevel() < variant.minimumLevel() || selection.enchantmentLevel() > variant.maximumLevel()) return Optional.empty();
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(variant.enchantment(), selection.enchantmentLevel());
		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		result.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		return Optional.of(result);
	}
	private static Optional<ItemStack> resolvePotion(Entry entry, TradeVariantSelection selection) {
		if (!(entry.variant() instanceof PotionVariant variant) || selection.enchantmentLevel() != 1 || selection.potionOption() < 0
			|| selection.potionOption() >= variant.family().options().size() || selection.potionContainer() == null) return Optional.empty();
		PotionFamilies.Option option = variant.family().options().get(selection.potionOption());
		return Optional.of(PotionContents.createItemStack(selection.potionContainer().item(), option.potion()));
	}
	public static ItemStack output(Entry entry) { return resolveOutput(entry, entry.defaultSelection()).orElseThrow(() -> new IllegalStateException("invalid catalog default")); }
	/** Compatibility seam for existing callers; it remains bounded and uses neutral potion fields. */
	public static ItemStack output(Entry entry, int level) { return resolveOutput(entry, new TradeVariantSelection(level, TradeVariantSelection.NO_POTION_OPTION, TradeVariantSelection.PotionContainer.NORMAL)).orElseThrow(() -> new IllegalArgumentException("invalid variant selection")); }

	public static Audit audit() {
		Set<Key> seen = new HashSet<>(); int duplicates = 0; boolean valid = true;
		for (Entry entry : enabledEntries) {
			if (!seen.add(entry.key())) duplicates++;
			valid &= entry.enabled() && entry.price() > 0 && entry.price() <= PriceConfig.MAX_EMERALD_VALUE && entry.quantity() > 0 && entry.quantity() <= entry.item().getDefaultMaxStackSize();
			if (entry.variant() instanceof EnchantmentVariant enchantment) valid &= entry.id().equals(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK)) && entry.variantId().getNamespace().equals("minecraft") && enchantment.minimumLevel() >= 1 && enchantment.maximumLevel() >= enchantment.minimumLevel();
			if (entry.variant() instanceof PotionVariant potion) valid &= entry.id().equals(BuiltInRegistries.ITEM.getKey(Items.POTION)) && !potion.family().options().isEmpty();
		}
		return new Audit(entries.size(), enabledEntries.size(), entries.size() - enabledEntries.size(), duplicates, valid);
	}

	public record Key(Identifier id, Identifier variantId) { }
	public sealed interface Variant permits OrdinaryVariant, EnchantmentVariant, PotionVariant { TradeVariantKind kind(); }
	public record OrdinaryVariant() implements Variant { @Override public TradeVariantKind kind() { return TradeVariantKind.NONE; } }
	public record EnchantmentVariant(Holder<Enchantment> enchantment, int minimumLevel, int maximumLevel, int defaultLevel) implements Variant { @Override public TradeVariantKind kind() { return TradeVariantKind.ENCHANTMENT; } }
	public record PotionVariant(PotionFamilies.Family family) implements Variant { @Override public TradeVariantKind kind() { return TradeVariantKind.POTION; } }
	public record Entry(Identifier id, Identifier variantId, Item item, Variant variant, boolean enabled, int price, int quantity, SellOffer sellOffer) {
		public static Entry ordinary(Identifier id, Item item, boolean enabled, int price, int quantity) { return new Entry(id, null, item, new OrdinaryVariant(), enabled, price, quantity, PriceConfig.sellOffer(item, price)); }
		public static Entry enchantment(Identifier id, Holder<Enchantment> enchantment, int minimum, int maximum, int defaultLevel, boolean enabled, int price) { return new Entry(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK), id, Items.ENCHANTED_BOOK, new EnchantmentVariant(enchantment, minimum, maximum, defaultLevel), enabled, price, 1, PriceConfig.sellOffer(Items.ENCHANTED_BOOK, price)); }
		public static Entry potion(PotionFamilies.Family family, boolean enabled, int price, int quantity) { return new Entry(BuiltInRegistries.ITEM.getKey(Items.POTION), family.id(), Items.POTION, new PotionVariant(family), enabled, price, quantity, PriceConfig.sellOffer(Items.POTION, price)); }
		public Key key() { return new Key(id, variantId); }
		public boolean enchantedBook() { return variant instanceof EnchantmentVariant; }
		public Holder<Enchantment> enchantment() { return variant instanceof EnchantmentVariant enchantment ? enchantment.enchantment() : null; }
		public PotionFamilies.Family potionFamily() { return variant instanceof PotionVariant potion ? potion.family() : null; }
		public TradeVariantSelection defaultSelection() { return switch (variant.kind()) {
			case NONE -> TradeVariantSelection.ordinary();
			case ENCHANTMENT -> TradeVariantSelection.enchantment(((EnchantmentVariant) variant).defaultLevel());
			case POTION -> TradeVariantSelection.potion(0, TradeVariantSelection.PotionContainer.NORMAL);
		}; }
	}
	public record Audit(int registeredVanilla, int enabled, int disabled, int duplicates, boolean validEntries) { public boolean valid() { return duplicates == 0 && validEntries; } }
}
