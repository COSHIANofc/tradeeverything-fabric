package com.coshian.tradeeverything.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;

/** Server-owned legal vanilla potion choices, derived from the 26.2 brewing predicate. */
public final class PotionFamilies {
	private PotionFamilies() { }
	public static List<Family> discover(HolderLookup.Provider registries) {
		PotionBrewing brewing = PotionBrewing.bootstrap(net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
		Map<Identifier, List<Option>> families = new LinkedHashMap<>();
		registries.lookupOrThrow(Registries.POTION).listElements().forEach(holder -> {
			Identifier id = holder.unwrapKey().orElseThrow().identifier();
			if (!"minecraft".equals(id.getNamespace()) || !brewing.isBrewablePotion(holder) || holder.value().getEffects().isEmpty()) return;
			Identifier family = holder.value().getEffects().getFirst().getEffect().unwrapKey().orElseThrow().identifier();
			families.computeIfAbsent(family, ignored -> new ArrayList<>()).add(new Option(id, holder));
		});
		return families.entrySet().stream().map(entry -> new Family(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparingInt((Option option) -> defaultOrder(option.id())).thenComparing(option -> option.id().toString())).toList())).sorted(Comparator.comparing(family -> family.id().toString())).toList();
	}
	/** Registry ID is only a deterministic presentation/default tie-breaker after brewing reachability has been proven. */
	private static int defaultOrder(Identifier id) { String path = id.getPath(); return path.startsWith("long_") ? 1 : path.startsWith("strong_") ? 2 : 0; }
	public record Family(Identifier id, List<Option> options) { public Option defaultOption() { return options.getFirst(); } }
	public record Option(Identifier id, Holder<Potion> potion) { }
	public static List<net.minecraft.world.item.Item> containers() { return List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION); }
	public static boolean isContainer(net.minecraft.world.item.Item item) { return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION; }
}
