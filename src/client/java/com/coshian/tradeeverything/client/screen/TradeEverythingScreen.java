package com.coshian.tradeeverything.client.screen;

import com.coshian.tradeeverything.menu.TradeEverythingMenu;
import com.coshian.tradeeverything.client.TradeNetworkingClient;
import com.coshian.tradeeverything.network.TradePayloads.PurchaseRequest;
import com.coshian.tradeeverything.network.TradePayloads.TransactionType;
import com.coshian.tradeeverything.price.SellPricing;
import com.coshian.tradeeverything.search.SearchInputRouting;
import com.coshian.tradeeverything.search.TradeSearchIndex;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.tags.ItemTags;

public final class TradeEverythingScreen extends AbstractContainerScreen<TradeEverythingMenu> {
	private static final int WIDTH = 320, HEIGHT = 330, ROW_HEIGHT = 24, VISIBLE_ROWS = 9;
	private static final int HEADER_MODE_Y = 22, HEADER_SEARCH_Y = 43, LIST_Y = 76;
	private static final int DETAIL_LEFT = 213, DETAIL_WIDTH = 99, DETAIL_TOP = 76, DETAIL_CENTER = DETAIL_LEFT + DETAIL_WIDTH / 2;
	private static final int DETAIL_ITEM_Y = DETAIL_TOP + 23, DETAIL_REGISTRY_Y = DETAIL_ITEM_Y + 13, DETAIL_PRIMARY_Y = DETAIL_REGISTRY_Y + 13;
	private static final int DETAIL_SECONDARY_Y = DETAIL_PRIMARY_Y + 13, DETAIL_TOTAL_Y = DETAIL_SECONDARY_Y + 13, DETAIL_STATUS_Y = DETAIL_TOTAL_Y + 13;
	private static final int QUANTITY_CONTROL_Y = 168, ACTION_BUTTON_Y = 193, STATUS_AREA_Y = 306;
	private EditBox search;
	private Button buy, buyMode, sellMode, minus, plus;
	private List<ClientTradeEntry> catalogEntries = List.of(), all = List.of(), filtered = List.of();
	private TradeSearchIndex<ClientTradeEntry> index = new TradeSearchIndex<>(List.of());
	private List<?> catalogIdentity = List.of();
	private ClientTradeEntry selected;
	private int scroll;
	private int buyQuantity = 1, sellQuantity;
	private long inventoryFingerprint = Long.MIN_VALUE;
	private TradeMode mode = TradeMode.BUY;
	private TradeMode sourceMode;
	private boolean buyPending, sellPending;
	private boolean affordable;
	private long resultRevision;
	private String language = "";

	public TradeEverythingScreen(TradeEverythingMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, WIDTH, HEIGHT); }

	@Override protected void init() {
		super.init();
		search = new EditBox(font, leftPos + 10, topPos + HEADER_SEARCH_Y, 300, 20, Component.translatable("screen.tradeeverything.search"));
		search.setHint(Component.translatable("screen.tradeeverything.search_placeholder")); search.setMaxLength(128); search.setResponder(this::filter);
		addRenderableWidget(search);
		buyMode = Button.builder(Component.translatable("screen.tradeeverything.buy"), b -> setMode(TradeMode.BUY)).bounds(leftPos + 215, topPos + HEADER_MODE_Y, 45, 18).build();
		sellMode = Button.builder(Component.translatable("screen.tradeeverything.sell"), b -> setMode(TradeMode.SELL)).bounds(leftPos + 265, topPos + HEADER_MODE_Y, 45, 18).build();
		minus = Button.builder(Component.literal("-"), b -> adjust(-1, false, false)).bounds(leftPos + 215, topPos + QUANTITY_CONTROL_Y, 20, 18).build();
		plus = Button.builder(Component.literal("+"), b -> adjust(1, false, false)).bounds(leftPos + 290, topPos + QUANTITY_CONTROL_Y, 20, 18).build();
		buy = Button.builder(Component.translatable("screen.tradeeverything.buy"), button -> action()).bounds(leftPos + 215, topPos + ACTION_BUTTON_Y, 95, 20).build();
		addRenderableWidget(buyMode); addRenderableWidget(sellMode); addRenderableWidget(minus); addRenderableWidget(plus); addRenderableWidget(buy); setInitialFocus(search); rebuildIfNeeded();
	}

	@Override protected void containerTick() {
		if (resultRevision != menu.resultRevision()) {
			resultRevision = menu.resultRevision();
			if (menu.statusType() == TransactionType.BUY) buyPending = false;
			else if (menu.statusType() == TransactionType.SELL) sellPending = false;
		}
		rebuildIfNeeded(); updateBuyState();
	}

	private void rebuildIfNeeded() {
		String selectedLanguage = minecraft.getLanguageManager().getSelected();
		boolean catalogChanged = catalogIdentity != menu.catalog() || !language.equals(selectedLanguage);
		if (catalogChanged) {
			catalogIdentity = menu.catalog(); language = selectedLanguage;
			catalogEntries = menu.catalog().stream().map(data -> {
				var item = BuiltInRegistries.ITEM.getOptional(data.id()).orElse(Items.AIR);
				ItemStack stack = stackFor(data, item);
				String label = data.variantId() == null ? stack.getHoverName().getString() : Component.translatable("screen.tradeeverything.enchanted_book", enchantmentName(data)).getString();
				return new ClientTradeEntry(data, stack, label, data.id().toString(), data.variantId() == null ? data.id().toString() : data.id() + " " + data.variantId(), 0, -1);
			}).filter(entry -> !entry.stack().isEmpty()).sorted(Comparator.comparing(ClientTradeEntry::localizedName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(ClientTradeEntry::registryId)).toList();
		}
		long currentFingerprint = mode == TradeMode.SELL ? fingerprint() : inventoryFingerprint;
		if (!catalogChanged && sourceMode == mode && (mode != TradeMode.SELL || inventoryFingerprint == currentFingerprint)) return;
		var selectedId = selected == null ? null : selected.data().id();
		var selectedVariant = selected == null ? null : selected.data().variantId();
		int selectedSlot = selected == null ? -1 : selected.inventorySlot();
		sourceMode = mode; inventoryFingerprint = currentFingerprint;
		all = mode == TradeMode.BUY ? catalogEntries : sellEntries();
		index = new TradeSearchIndex<>(all.stream().map(entry -> new TradeSearchIndex.Searchable<>(entry, entry.localizedName(), entry.searchRegistryId(), true)).toList());
		filter(search == null ? "" : search.getValue());
		if (selectedId != null) {
			for (ClientTradeEntry entry : filtered) if (entry.data().id().equals(selectedId) && java.util.Objects.equals(entry.data().variantId(), selectedVariant) && entry.inventorySlot() == selectedSlot) { selected = entry; break; }
			updateBuyState();
		}
	}

	private List<ClientTradeEntry> sellEntries() {
		if (minecraft.player == null) return List.of();
		Map<net.minecraft.world.item.Item, Integer> available = new IdentityHashMap<>();
		var filledShulkers = new java.util.ArrayList<ClientTradeEntry>();
		List<ItemStack> inventory = minecraft.player.getInventory().getNonEquipmentItems();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) continue;
			if (isFilledShulker(stack)) {
				for (ClientTradeEntry entry : catalogEntries) if (entry.stack().is(stack.getItem())) {
					filledShulkers.add(new ClientTradeEntry(entry.data(), stack.copy(), stack.getHoverName().getString(), entry.registryId(), entry.searchRegistryId(), 1, slot));
					break;
				}
			} else if (ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance())) {
				available.merge(stack.getItem(), stack.getCount(), Math::addExact);
			}
		}
		var result = new java.util.ArrayList<ClientTradeEntry>(available.size() + filledShulkers.size());
		for (ClientTradeEntry entry : catalogEntries) {
			int count = available.getOrDefault(entry.stack().getItem(), 0);
			if (count > 0) result.add(entry.withAvailable(count));
		}
		result.addAll(filledShulkers);
		result.sort(Comparator.comparing(ClientTradeEntry::localizedName, String.CASE_INSENSITIVE_ORDER).thenComparing(ClientTradeEntry::registryId).thenComparingInt(ClientTradeEntry::inventorySlot));
		return List.copyOf(result);
	}
	private static boolean isFilledShulker(ItemStack stack) {
		var contents = stack.get(DataComponents.CONTAINER);
		return stack.getItem().builtInRegistryHolder().is(ItemTags.SHULKER_BOXES) && contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent();
	}

	private void filter(String query) {
		filtered = index.filter(query); scroll = 0;
		if (selected == null || !filtered.contains(selected)) selected = filtered.isEmpty() ? null : filtered.getFirst();
		updateBuyState();
	}

	private void purchase() {
		if (!buyPending && selected != null && menu.catalogVersion() > 0) {
			ClientPlayNetworking.send(new PurchaseRequest(menu.containerId, menu.catalogVersion(), selected.data().id(), selected.data().variantId(), buyQuantity));
			buyPending = true;
		}
	}
	private void action() {
		if (mode == TradeMode.BUY) purchase();
		else if (!sellPending && selected != null) {
			TradeNetworkingClient.sendSellRequest(menu.containerId, menu.catalogVersion(), selected.data().id(), sellQuantity, selected.inventorySlot());
			sellPending = true;
		}
		updateBuyState();
	}
	private void setMode(TradeMode next) { if (mode != next) { mode = next; selected = null; sourceMode = null; rebuildIfNeeded(); } }
	private void adjust(int change, boolean shiftHeld, boolean controlHeld) { if (selected != null) { if (mode == TradeMode.BUY) buyQuantity = com.coshian.tradeeverything.trade.QuantityAdjustment.adjust(buyQuantity, change, 1, shiftHeld, controlHeld, 1, com.coshian.tradeeverything.trade.TradeTransactionService.MAX_BUY_QUANTITY); else { int step = SellPricing.sellOfferFor(selected.data().price()).itemQuantity(); int max = Math.min(com.coshian.tradeeverything.trade.TradeTransactionService.MAX_SELL_QUANTITY, selected.available()) / step * step; sellQuantity = com.coshian.tradeeverything.trade.QuantityAdjustment.adjust(sellQuantity, change, step, shiftHeld, controlHeld, step, max); } updateBuyState(); } }
	private long fingerprint() {
		if (minecraft.player == null) return 0;
		long hash = 1;
		for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems())
			hash = 31 * hash + 31L * stack.getCount() + ItemStack.hashItemAndComponents(stack);
		return hash;
	}
	private enum TradeMode { BUY, SELL }

	private void updateBuyState() { if (buy != null) { buyMode.active = mode != TradeMode.BUY; sellMode.active = mode != TradeMode.SELL; minus.visible = plus.visible = true; if (mode == TradeMode.BUY) { buyQuantity = net.minecraft.util.Mth.clamp(buyQuantity, 1, com.coshian.tradeeverything.trade.TradeTransactionService.MAX_BUY_QUANTITY); affordable = selected != null && canAfford(selected); buy.setMessage(Component.translatable("screen.tradeeverything.buy").withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED)); buy.active = !buyPending && affordable; minus.active = buyQuantity > 1; plus.active = buyQuantity < com.coshian.tradeeverything.trade.TradeTransactionService.MAX_BUY_QUANTITY; } else { affordable = false; buy.setMessage(Component.translatable("screen.tradeeverything.sell")); int step = selected == null ? 1 : SellPricing.sellOfferFor(selected.data().price()).itemQuantity(); int max = selected == null ? 0 : Math.min(com.coshian.tradeeverything.trade.TradeTransactionService.MAX_SELL_QUANTITY, selected.available()) / step * step; sellQuantity = max < step ? 0 : Math.max(step, Math.min(max, sellQuantity / step * step)); buy.active = !sellPending && selected != null && sellQuantity >= step; minus.active = sellQuantity > step; plus.active = sellQuantity + step <= max; } } }
	private boolean canAfford(ClientTradeEntry entry) {
		long cost = (long)entry.data().price() * buyQuantity;
		return com.coshian.tradeeverything.trade.Currency.value(minecraft.player.getInventory().getNonEquipmentItems()) >= cost;
	}
	private int count(net.minecraft.world.item.Item item) { return minecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }

	@Override public boolean keyPressed(KeyEvent event) {
		boolean focused = search != null && search.isFocused();
		boolean editBoxHandled = focused && search.keyPressed(event);
		if (SearchInputRouting.consumesFocusedKey(focused, editBoxHandled, focused && search.canConsumeInput(), event.isEscape())) return true;
		return super.keyPressed(event);
	}

	@Override public boolean charTyped(CharacterEvent event) {
		if (search != null && search.isFocused() && search.charTyped(event)) return true;
		return super.charTyped(event);
	}

	@Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
		graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF808080);
	}

	@Override public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractContents(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, title, leftPos + 10, topPos + 7, 0xFFFFFFFF, false);
		graphics.text(font, Component.translatable("screen.tradeeverything.results", filtered.size()), leftPos + 10, topPos + 66, 0xFFB0B0B0, false);
		int first = Math.min(scroll, Math.max(0, filtered.size() - VISIBLE_ROWS));
		for (int row = 0; row < VISIBLE_ROWS && first + row < filtered.size(); row++) {
			ClientTradeEntry entry = filtered.get(first + row); int y = topPos + LIST_Y + row * ROW_HEIGHT;
			if (entry == selected) graphics.fill(leftPos + 8, y, leftPos + 208, y + ROW_HEIGHT - 2, 0x805A7FA8);
			else if (inside(mouseX, mouseY, leftPos + 8, y, 200, ROW_HEIGHT - 2)) graphics.fill(leftPos + 8, y, leftPos + 208, y + ROW_HEIGHT - 2, 0x40404040);
			graphics.item(entry.stack(), leftPos + 11, y + 3);
			graphics.text(font, font.plainSubstrByWidth(entry.localizedName(), 122), leftPos + 31, y + 3, 0xFFFFFFFF, false);
			graphics.text(font, mode == TradeMode.BUY ? priceText(entry) : Component.translatable("screen.tradeeverything.available", entry.available()), leftPos + 31, y + 13, 0xFFFFD060, false);
			graphics.text(font, "×" + (mode == TradeMode.BUY ? entry.data().quantity() : entry.available()), leftPos + 178, y + 8, 0xFFFFFFFF, false);
			if (inside(mouseX, mouseY, leftPos + 8, y, 200, ROW_HEIGHT - 2)) graphics.setTooltipForNextFrame(font, entry.stack(), mouseX, mouseY);
		}
		graphics.fill(leftPos + DETAIL_LEFT, topPos + LIST_Y, leftPos + DETAIL_LEFT + DETAIL_WIDTH, topPos + 187, 0x40202020);
		if (selected != null) {
			graphics.item(selected.stack(), leftPos + DETAIL_CENTER - 8, topPos + DETAIL_TOP + 1);
			centeredBounded(graphics, Component.literal(selected.localizedName()), DETAIL_ITEM_Y, 0xFFFFFFFF);
			centeredBounded(graphics, Component.literal(selected.data().variantId() == null ? selected.registryId() : selected.data().variantId().toString()), DETAIL_REGISTRY_Y, 0xFFB0B0B0);
			if (mode == TradeMode.BUY) {
				centeredBounded(graphics, priceText(selected), DETAIL_PRIMARY_Y, 0xFFFFD060);
				centeredBounded(graphics, Component.translatable("screen.tradeeverything.quantity", buyQuantity), DETAIL_SECONDARY_Y, 0xFFFFFFFF);
				centeredBounded(graphics, totalPriceText(selected), DETAIL_TOTAL_Y, 0xFFFFD060);
			} else {
				var offer = SellPricing.sellOfferFor(selected.data().price()); int reward = sellQuantity / offer.itemQuantity() * offer.emeraldReward();
				centeredBounded(graphics, Component.translatable("screen.tradeeverything.available", selected.available()), DETAIL_PRIMARY_Y, 0xFFFFFFFF);
				centeredBounded(graphics, Component.translatable("screen.tradeeverything.quantity", sellQuantity), DETAIL_SECONDARY_Y, 0xFFFFFFFF);
				if (selected.inventorySlot() >= 0) {
					centeredBounded(graphics, Component.translatable("screen.tradeeverything.sell_shulker_contents"), DETAIL_TOTAL_Y, 0xFFFFD060);
					centeredBounded(graphics, Component.translatable("screen.tradeeverything.server_calculates_reward"), DETAIL_STATUS_Y, 0xFF55FF55);
				} else {
					centeredBounded(graphics, Component.translatable("screen.tradeeverything.sell_offer", offer.itemQuantity(), offer.emeraldReward()), DETAIL_TOTAL_Y, 0xFFFFD060);
					centeredBounded(graphics, Component.translatable("screen.tradeeverything.receive", reward), DETAIL_STATUS_Y, 0xFF55FF55);
				}
			}
			graphics.centeredText(font, Component.literal(Integer.toString(mode == TradeMode.BUY ? buyQuantity : sellQuantity)), leftPos + DETAIL_CENTER, topPos + QUANTITY_CONTROL_Y + 5, 0xFFFFFFFF);
		}
		if (!menu.status().isEmpty()) {
			String operation = menu.statusType() == TransactionType.SELL ? Component.translatable("screen.tradeeverything.sell").getString() : Component.translatable("screen.tradeeverything.buy").getString();
			String status = operation + ": " + Component.translatable(menu.status()).getString();
			graphics.centeredText(font, Component.literal(font.plainSubstrByWidth(status, 280)), leftPos + 150, topPos + STATUS_AREA_Y, 0xFFFFFFFF);
		}
	}
	private void centeredBounded(GuiGraphicsExtractor graphics, Component text, int y, int color) { graphics.centeredText(font, Component.literal(font.plainSubstrByWidth(text.getString(), DETAIL_WIDTH - 6)), leftPos + DETAIL_CENTER, topPos + y, color); }
	/** Centralized regression boundary: the viewport must never silently return to four rows. */
	public static int visibleRows() { return VISIBLE_ROWS; }
	private ItemStack stackFor(com.coshian.tradeeverything.network.TradePayloads.CatalogEntryData data, net.minecraft.world.item.Item item) {
		ItemStack stack = new ItemStack(item, data.quantity());
		if (data.variantId() != null && minecraft.level != null) {
			Holder<Enchantment> enchantment = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, data.variantId()));
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); stored.set(enchantment, enchantment.value().getMaxLevel()); stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		}
		return stack;
	}
	private String enchantmentName(com.coshian.tradeeverything.network.TradePayloads.CatalogEntryData data) {
		if (data.variantId() == null || minecraft.level == null) return "";
		Holder<Enchantment> enchantment = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, data.variantId()));
		return Enchantment.getFullname(enchantment, enchantment.value().getMaxLevel()).getString();
	}
	private static Component priceText(ClientTradeEntry entry) {
		return Component.translatable("screen.tradeeverything.price", entry.data().price());
	}
	private Component totalPriceText(ClientTradeEntry entry) {
		long total = (long)entry.data().price() * buyQuantity;
		if (total > Integer.MAX_VALUE) return Component.translatable("screen.tradeeverything.total_price", "?");
		Component price = Component.translatable("screen.tradeeverything.price", total);
		return Component.translatable("screen.tradeeverything.total_price", price);
	}

	@Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (inside(x, y, leftPos + 8, topPos + 60, 200, VISIBLE_ROWS * ROW_HEIGHT)) {
			scroll = net.minecraft.util.Mth.clamp(scroll - (int)Math.signum(scrollY), 0, Math.max(0, filtered.size() - VISIBLE_ROWS)); return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (inside(event.x(), event.y(), leftPos + 215, topPos + QUANTITY_CONTROL_Y, 20, 18)) { adjust(-1, event.hasShiftDown(), event.hasControlDown()); return true; }
		if (inside(event.x(), event.y(), leftPos + 290, topPos + QUANTITY_CONTROL_Y, 20, 18)) { adjust(1, event.hasShiftDown(), event.hasControlDown()); return true; }
		if (inside(event.x(), event.y(), leftPos + 215, topPos + 22, 45, 18)) { setMode(TradeMode.BUY); return true; }
		if (inside(event.x(), event.y(), leftPos + 265, topPos + 22, 45, 18)) { setMode(TradeMode.SELL); return true; }
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int index = scroll + row, y = topPos + LIST_Y + row * ROW_HEIGHT;
			if (index < filtered.size() && inside(event.x(), event.y(), leftPos + 8, y, 200, ROW_HEIGHT - 2)) { selected = filtered.get(index); updateBuyState(); return true; }
		}
		return super.mouseClicked(event, doubleClick);
	}
	private static boolean inside(double x, double y, int left, int top, int width, int height) { return x >= left && x < left + width && y >= top && y < top + height; }
}
