package com.coshian.tradeeverything.client.screen;

import com.coshian.tradeeverything.network.TradePayloads.CatalogEntryData;
import net.minecraft.world.item.ItemStack;

record ClientTradeEntry(CatalogEntryData data, ItemStack stack, String localizedName, String registryId, String searchRegistryId, int available, int inventorySlot) {
	ClientTradeEntry withAvailable(int count) { return new ClientTradeEntry(data, stack, localizedName, registryId, searchRegistryId, count, inventorySlot); }
}
