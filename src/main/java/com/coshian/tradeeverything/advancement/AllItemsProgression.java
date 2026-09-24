package com.coshian.tradeeverything.advancement;

import com.coshian.tradeeverything.catalog.SurvivalEligibility;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** The sole canonicalization point for the static vanilla all-items challenge. */
public final class AllItemsProgression {
	public static final Identifier ENCHANTED_BOOK = Identifier.fromNamespaceAndPath("tradeeverything", "special/enchanted_book");
	public static final Identifier POTION = Identifier.fromNamespaceAndPath("tradeeverything", "special/potion");
	private static final Identifier ENCHANTED_BOOK_ITEM = Identifier.withDefaultNamespace("enchanted_book");
	private static final Identifier POTION_ITEM = Identifier.withDefaultNamespace("potion"), SPLASH_POTION_ITEM = Identifier.withDefaultNamespace("splash_potion"), LINGERING_POTION_ITEM = Identifier.withDefaultNamespace("lingering_potion");
	private AllItemsProgression() { }
	public static Optional<Identifier> key(Identifier item) {
		if (item == null || !"minecraft".equals(item.getNamespace())) return Optional.empty();
		if (item.equals(ENCHANTED_BOOK_ITEM)) return Optional.of(ENCHANTED_BOOK);
		if (item.equals(POTION_ITEM) || item.equals(SPLASH_POTION_ITEM) || item.equals(LINGERING_POTION_ITEM)) return Optional.of(POTION);
		var value = BuiltInRegistries.ITEM.getOptional(item);
		return value.filter(SurvivalEligibility::isEligible).map(ignored -> item);
	}
	public static String criterion(Identifier key) {
		if (key.equals(ENCHANTED_BOOK)) return "special__enchanted_book";
		if (key.equals(POTION)) return "special__potion";
		return "item__" + key.getNamespace() + "__" + key.getPath().replace('/', '_');
	}
	/** Static vanilla universe: deliberately independent of runtime price/mod configuration. */
	public static Set<Identifier> requiredKeys() {
		Set<Identifier> result = new LinkedHashSet<>();
		BuiltInRegistries.ITEM.forEach(item -> key(BuiltInRegistries.ITEM.getKey(item)).ifPresent(result::add));
		return Set.copyOf(result);
	}
}
