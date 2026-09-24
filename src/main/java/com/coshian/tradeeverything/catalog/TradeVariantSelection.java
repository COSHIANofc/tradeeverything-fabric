package com.coshian.tradeeverything.catalog;

/** Bounded client selection values; the catalog determines their meaning server-side. */
public record TradeVariantSelection(int enchantmentLevel, int potionOption, PotionContainer potionContainer) {
	public static final int NO_POTION_OPTION = -1;
	public static TradeVariantSelection ordinary() { return new TradeVariantSelection(1, NO_POTION_OPTION, PotionContainer.NORMAL); }
	public static TradeVariantSelection enchantment(int level) { return new TradeVariantSelection(level, NO_POTION_OPTION, PotionContainer.NORMAL); }
	public static TradeVariantSelection potion(int option, PotionContainer container) { return new TradeVariantSelection(1, option, container); }
	public enum PotionContainer {
		NORMAL, SPLASH, LINGERING;
		public static PotionContainer fromNetworkId(int id) { return id >= 0 && id < values().length ? values()[id] : null; }
		public net.minecraft.world.item.Item item() { return switch (this) { case NORMAL -> net.minecraft.world.item.Items.POTION; case SPLASH -> net.minecraft.world.item.Items.SPLASH_POTION; case LINGERING -> net.minecraft.world.item.Items.LINGERING_POTION; }; }
	}
}
