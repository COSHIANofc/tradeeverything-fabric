package com.coshian.tradeeverything.advancement;

import com.coshian.tradeeverything.TradeEverything;
import com.coshian.tradeeverything.catalog.TradeCatalog;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Awards only after an authoritative transaction has committed. */
public final class TradeAdvancements {
	private static final Identifier FIRST = TradeEverything.id("first_trade"), SECOND_EGG = TradeEverything.id("second_dragon_egg"), HOE = TradeEverything.id("netherite_hoe"), ALL = TradeEverything.id("all_items");
	private static final Identifier VANILLA_FIRST_EGG = Identifier.withDefaultNamespace("end/dragon_egg");
	private TradeAdvancements() {}
	public static void recordTrade(ServerPlayer player, Identifier itemId) {
		award(player, FIRST); TradeProgressData data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(TradeProgressData.TYPE); data.add(player.getUUID(), itemId);
		if (itemId.equals(Identifier.withDefaultNamespace("dragon_egg")) && hasVanillaFirstEgg(player) && count(player, Items.DRAGON_EGG) >= 2) award(player, SECOND_EGG);
		if (itemId.equals(Identifier.withDefaultNamespace("netherite_hoe"))) award(player, HOE);
		Set<Identifier> required = TradeCatalog.enabledEntries().stream().map(TradeCatalog.Entry::id).collect(java.util.stream.Collectors.toSet());
		if (!required.isEmpty() && data.get(player.getUUID()).containsAll(required)) award(player, ALL);
	}
	private static boolean hasVanillaFirstEgg(ServerPlayer player) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(VANILLA_FIRST_EGG);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
	private static int count(ServerPlayer player, Item item) { return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
	private static void award(ServerPlayer player, Identifier id) { AdvancementHolder holder = player.level().getServer().getAdvancements().get(id); if (holder != null) player.getAdvancements().award(holder, "trade"); }
}
