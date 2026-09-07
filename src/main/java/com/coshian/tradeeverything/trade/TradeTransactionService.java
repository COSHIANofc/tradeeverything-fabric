package com.coshian.tradeeverything.trade;

import com.coshian.tradeeverything.catalog.TradeCatalog;
import com.coshian.tradeeverything.advancement.TradeAdvancements;
import com.coshian.tradeeverything.catalog.SurvivalEligibility;
import com.coshian.tradeeverything.menu.TradeEverythingMenu;
import com.coshian.tradeeverything.price.SellOffer;
import com.coshian.tradeeverything.price.SellPricing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;

public final class TradeTransactionService {
	public static final int MAX_SELL_QUANTITY = 576;
	public static final int MAX_BUY_QUANTITY = 576;
	private TradeTransactionService() {}

	public static Result purchase(ServerPlayer player, int containerId, int requestedVersion, Identifier itemId) {
		return purchase(player, containerId, requestedVersion, itemId, null, 1);
	}

	/** Atomically buys transaction units; price and output are recomputed solely on the server. */
	public static Result purchase(ServerPlayer player, int containerId, int requestedVersion, Identifier itemId, int quantity) {
		return purchase(player, containerId, requestedVersion, itemId, null, quantity);
	}
	public static Result purchase(ServerPlayer player, int containerId, int requestedVersion, Identifier itemId, Identifier variantId, int quantity) {
		Result session = validateSession(player, containerId, requestedVersion);
		if (session != null) return session;
		if (quantity <= 0 || quantity > MAX_BUY_QUANTITY) return Result.INVALID_BUY_QUANTITY;
		var entry = TradeCatalog.enabled(itemId, variantId);
		if (entry.isEmpty()) return Result.DISABLED_ITEM;
		TradeCatalog.Entry trade = entry.orElseThrow();
		if (trade.price() < 1 || trade.quantity() < 1 || trade.quantity() > trade.item().getDefaultMaxStackSize()) return Result.INVALID_CATALOG_ENTRY;

		final int totalPrice, totalOutput;
		try { totalPrice = Math.multiplyExact(trade.price(), quantity); totalOutput = Math.multiplyExact(trade.quantity(), quantity); }
		catch (ArithmeticException exception) { return Result.ARITHMETIC_OVERFLOW; }
		List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
		List<ItemStack> updated = copy(inventory);
		try { if (!Currency.pay(updated, totalPrice)) return Result.INSUFFICIENT_PAYMENT; }
		catch (ArithmeticException exception) { return Result.ARITHMETIC_OVERFLOW; }
		if (!insert(updated, TradeCatalog.output(trade), totalOutput)) return Result.INVENTORY_FULL;
		commit(player, inventory, updated);
		TradeAdvancements.recordTrade(player, trade.id());
		return Result.SUCCESS;
	}

	/** Performs one authoritative, component-safe item sell without client price or reward inputs. */
	public static Result sell(ServerPlayer player, int containerId, int requestedVersion, Identifier itemId, int quantity) {
		return sell(player, containerId, requestedVersion, itemId, quantity, -1);
	}

	/** A non-negative slot is required for a filled Shulker so the server empties the exact selected stack. */
	public static Result sell(ServerPlayer player, int containerId, int requestedVersion, Identifier itemId, int quantity, int inventorySlot) {
		Result session = validateSession(player, containerId, requestedVersion);
		if (session != null) return session;
		if (quantity <= 0 || quantity > MAX_SELL_QUANTITY) return Result.INVALID_SELL_QUANTITY;
		if (itemId == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) return Result.INVALID_ITEM;
		var entry = TradeCatalog.enabled(itemId);
		if (entry.isEmpty()) return Result.DISABLED_ITEM;
		TradeCatalog.Entry trade = entry.orElseThrow();
		if (!SurvivalEligibility.isEligible(trade.item()) || !trade.enabled() || trade.price() < 1) return Result.INVALID_CATALOG_ENTRY;
		if (trade.item().builtInRegistryHolder().is(ItemTags.SHULKER_BOXES) && inventorySlot >= 0) {
			if (quantity != 1) return Result.INVALID_SELL_QUANTITY;
			return sellShulker(player, inventoryFor(player), trade, inventorySlot);
		}

		SellOffer offer;
		try { offer = SellPricing.sellOfferFor(trade.price()); }
		catch (IllegalArgumentException exception) { return Result.INVALID_CATALOG_ENTRY; }
		if (quantity % offer.itemQuantity() != 0) return Result.INVALID_SELL_BUNDLE;

		final int reward;
		try { reward = Math.multiplyExact(quantity / offer.itemQuantity(), offer.emeraldReward()); }
		catch (ArithmeticException exception) { return Result.ARITHMETIC_OVERFLOW; }
		List<ItemStack> inventory = inventoryFor(player);
		if (countSafe(inventory, trade.item()) < quantity) {
			return count(inventory, trade.item()) >= quantity ? Result.UNSUPPORTED_ITEM_COMPONENTS : Result.INSUFFICIENT_SELLABLE_ITEMS;
		}
		List<ItemStack> updated = copy(inventory);
		if (!removeSafe(updated, trade.item(), quantity)) return Result.INSUFFICIENT_SELLABLE_ITEMS;
		if (!insert(updated, Items.EMERALD.getDefaultInstance(), reward)) return Result.REWARD_INVENTORY_FULL;
		commit(player, inventory, updated);
		TradeAdvancements.recordTrade(player, trade.id());
		return Result.SUCCESS;
	}

	/** Filled Shulkers sell their contents only. Nested Shulkers are rejected, never recursed. */
	private static Result sellShulker(ServerPlayer player, List<ItemStack> inventory, TradeCatalog.Entry outer, int slot) {
		if (slot >= inventory.size()) return Result.INSUFFICIENT_SELLABLE_ITEMS;
		ItemStack box = inventory.get(slot);
		if (!box.is(outer.item())) return Result.INSUFFICIENT_SELLABLE_ITEMS;
		var contents = box.get(DataComponents.CONTAINER);
		if (contents == null || contents.nonEmptyItemCopyStream().findAny().isEmpty()) return Result.INSUFFICIENT_SELLABLE_ITEMS;
		if (box.getCount() != 1) return Result.UNSUPPORTED_ITEM_COMPONENTS;
		long reward;
		try { reward = shulkerReward(box); }
		catch (InvalidShulkerContents exception) { return Result.UNSUPPORTED_CONTAINER_CONTENTS; }
		catch (ArithmeticException exception) { return Result.ARITHMETIC_OVERFLOW; }
		List<ItemStack> updated = copy(inventory);
		ItemStack retainedShell = box.copy();
		retainedShell.remove(DataComponents.CONTAINER);
		updated.set(slot, retainedShell);
		if (!insertInto(updated, Items.EMERALD.getDefaultInstance(), reward)) return Result.REWARD_INVENTORY_FULL;
		commit(player, inventory, updated);
		for (ItemStack contained : contents.nonEmptyItemCopyStream().toList()) TradeAdvancements.recordTrade(player, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(contained.getItem()));
		return Result.SUCCESS;
	}

	private static long shulkerReward(ItemStack box) {
		long total = 0;
		var contents = box.get(DataComponents.CONTAINER);
		if (contents == null) return total;
		for (ItemStack contained : contents.nonEmptyItemCopyStream().toList()) {
			if (!SellEligibility.isSafeDefaultStack(contained) || contained.getItem().builtInRegistryHolder().is(ItemTags.SHULKER_BOXES)) throw new InvalidShulkerContents();
			var entry = TradeCatalog.enabled(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(contained.getItem())).orElseThrow(InvalidShulkerContents::new);
			if (!SurvivalEligibility.isEligible(entry.item())) throw new InvalidShulkerContents();
			total = Math.addExact(total, rewardFor(entry, contained.getCount()));
		}
		return total;
	}
	private static long rewardFor(TradeCatalog.Entry entry, int quantity) {
		SellOffer offer = SellPricing.sellOfferFor(entry.price());
		if (quantity <= 0 || quantity % offer.itemQuantity() != 0) throw new InvalidShulkerContents();
		return Math.multiplyExact((long)quantity / offer.itemQuantity(), offer.emeraldReward());
	}
	private static final class InvalidShulkerContents extends RuntimeException { }
	private static List<ItemStack> inventoryFor(ServerPlayer player) { return player.getInventory().getNonEquipmentItems(); }

	private static Result validateSession(ServerPlayer player, int containerId, int requestedVersion) {
		if (!(player.containerMenu instanceof TradeEverythingMenu menu) || menu.containerId != containerId) return Result.INVALID_SESSION;
		if (requestedVersion != TradeCatalog.version() || menu.catalogVersion() != requestedVersion) return Result.STALE_CATALOG;
		Entity merchant = player.level().getEntity(menu.merchantId());
		if (merchant == null || !merchant.isAlive() || !merchant.entityTags().contains("tradeeverything.clerk") || player.distanceToSqr(merchant) > 64.0) return Result.INVALID_MERCHANT;
		return null;
	}

	private static List<ItemStack> copy(List<ItemStack> inventory) {
		List<ItemStack> copy = new ArrayList<>(inventory.size());
		for (ItemStack stack : inventory) copy.add(stack.copy());
		return copy;
	}

	static boolean insertInto(List<ItemStack> inventory, ItemStack inserted, long requestedAmount) {
		if (requestedAmount < 0 || requestedAmount > Integer.MAX_VALUE) return false;
		return insert(inventory, inserted, (int)requestedAmount);
	}
	private static boolean insert(List<ItemStack> inventory, ItemStack inserted, int amount) {
		for (ItemStack stack : inventory) {
			if (amount == 0) return true;
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, inserted)) {
				int moved = Math.min(amount, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
				stack.grow(moved); amount -= moved;
			}
		}
		for (int slot = 0; slot < inventory.size() && amount > 0; slot++) {
			if (inventory.get(slot).isEmpty()) {
				int moved = Math.min(amount, inserted.getMaxStackSize());
				ItemStack stack = inserted.copy(); stack.setCount(moved); inventory.set(slot, stack); amount -= moved;
			}
		}
		return amount == 0;
	}

	private static void commit(ServerPlayer player, List<ItemStack> inventory, List<ItemStack> updated) {
		for (int slot = 0; slot < inventory.size(); slot++) inventory.set(slot, updated.get(slot));
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastChanges(); player.containerMenu.broadcastChanges();
	}

	private static int count(List<ItemStack> inventory, net.minecraft.world.item.Item item) {
		int total = 0;
		for (ItemStack stack : inventory) if (stack.is(item)) total = Math.addExact(total, stack.getCount());
		return total;
	}
	private static int countSafe(List<ItemStack> inventory, net.minecraft.world.item.Item item) {
		int total = 0;
		for (ItemStack stack : inventory) if (stack.is(item) && SellEligibility.isSafeDefaultStack(stack)) total = Math.addExact(total, stack.getCount());
		return total;
	}
	private static boolean remove(List<ItemStack> inventory, net.minecraft.world.item.Item item, int amount) {
		for (ItemStack stack : inventory) {
			if (amount == 0) return true;
			if (stack.is(item)) { int removed = Math.min(amount, stack.getCount()); stack.shrink(removed); amount -= removed; }
		}
		return amount == 0;
	}
	private static boolean removeSafe(List<ItemStack> inventory, net.minecraft.world.item.Item item, int amount) {
		for (ItemStack stack : inventory) {
			if (amount == 0) return true;
			if (stack.is(item) && SellEligibility.isSafeDefaultStack(stack)) { int removed = Math.min(amount, stack.getCount()); stack.shrink(removed); amount -= removed; }
		}
		return amount == 0;
	}

	public enum Result {
		SUCCESS("success"), INVALID_SESSION("invalid_session"), STALE_CATALOG("stale_catalog"), INVALID_MERCHANT("invalid_merchant"),
		DISABLED_ITEM("disabled_item"), INVALID_CATALOG_ENTRY("invalid_entry"), INSUFFICIENT_PAYMENT("insufficient_payment"), INVENTORY_FULL("inventory_full"),
		INVALID_BUY_QUANTITY("invalid_buy_quantity"),
		INVALID_ITEM("invalid_item"), INVALID_SELL_QUANTITY("invalid_sell_quantity"), INVALID_SELL_BUNDLE("invalid_sell_bundle"),
		INSUFFICIENT_SELLABLE_ITEMS("insufficient_sellable_items"), UNSUPPORTED_ITEM_COMPONENTS("unsupported_item_components"),
		REWARD_INVENTORY_FULL("reward_inventory_full"), UNSUPPORTED_CONTAINER_CONTENTS("unsupported_container_contents"), ARITHMETIC_OVERFLOW("arithmetic_overflow");
		private final String code;
		Result(String code) { this.code = code; }
		public String translationKey() { return "screen.tradeeverything.result." + code; }
		public boolean success() { return this == SUCCESS; }
	}
}
