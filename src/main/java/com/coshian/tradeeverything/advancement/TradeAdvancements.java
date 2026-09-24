package com.coshian.tradeeverything.advancement;

import com.coshian.tradeeverything.TradeEverything;
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
		award(player, FIRST); TradeProgressData data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(TradeProgressData.TYPE);
		AllItemsProgression.key(itemId).ifPresent(key -> {
			if (data.add(player.getUUID(), key)) awardCriterion(player, ALL, AllItemsProgression.criterion(key));
		});
		if (itemId.equals(Identifier.withDefaultNamespace("dragon_egg")) && hasVanillaFirstEgg(player) && count(player, Items.DRAGON_EGG) >= 2) award(player, SECOND_EGG);
		if (itemId.equals(Identifier.withDefaultNamespace("netherite_hoe"))) award(player, HOE);
		var required = AllItemsProgression.requiredKeys();
		if (!required.isEmpty() && data.get(player.getUUID()).containsAll(required)) award(player, ALL);
	}
	/** Restores only persisted canonical progress when the player's advancement tracker is available. */
	public static void synchronize(ServerPlayer player) {
		TradeProgressData data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(TradeProgressData.TYPE);
		for (Identifier key : data.get(player.getUUID())) awardCriterion(player, ALL, AllItemsProgression.criterion(key));
	}
	private static boolean hasVanillaFirstEgg(ServerPlayer player) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(VANILLA_FIRST_EGG);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}
	private static int count(ServerPlayer player, Item item) { return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
	private static void award(ServerPlayer player, Identifier id) { AdvancementHolder holder = player.level().getServer().getAdvancements().get(id); if (holder != null) player.getAdvancements().award(holder, "trade"); }
	private static void awardCriterion(ServerPlayer player, Identifier id, String criterion) { AdvancementHolder holder = player.level().getServer().getAdvancements().get(id); if (holder != null) player.getAdvancements().award(holder, criterion); }
}
