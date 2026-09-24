package com.coshian.tradeeverything.network;

import com.coshian.tradeeverything.TradeEverything;
import com.coshian.tradeeverything.catalog.TradeVariantKind;
import com.coshian.tradeeverything.catalog.TradeVariantSelection.PotionContainer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class TradePayloads {
	public static final int MAX_CATALOG_ENTRIES = 4096;
	public static final int MAX_POTION_OPTIONS = 64;
	private TradePayloads() { }

	/** Client-safe descriptive potion data. Index is implied by deterministic list order. */
	public record PotionOptionData(Identifier potionId, PotionOptionKind optionKind) { }
	public enum PotionOptionKind { NORMAL, STRONG, EXTENDED }
	public sealed interface VariantData permits NoneVariantData, EnchantmentVariantData, PotionVariantData { TradeVariantKind kind(); }
	public record NoneVariantData() implements VariantData { @Override public TradeVariantKind kind() { return TradeVariantKind.NONE; } }
	public record EnchantmentVariantData(int minimumLevel, int maximumLevel, int defaultLevel) implements VariantData { @Override public TradeVariantKind kind() { return TradeVariantKind.ENCHANTMENT; } }
	public record PotionVariantData(List<PotionOptionData> options, List<PotionContainer> containers, int defaultOption, PotionContainer defaultContainer) implements VariantData {
		public PotionVariantData { options = List.copyOf(options); containers = List.copyOf(containers); }
		@Override public TradeVariantKind kind() { return TradeVariantKind.POTION; }
	}

	/** Variant metadata is descriptive only; server holders and output components never cross this payload. */
	public record CatalogEntryData(Identifier id, Identifier variantId, int price, int quantity, int sellItems, int sellEmeralds, VariantData variant) {
		private static CatalogEntryData read(RegistryFriendlyByteBuf buffer) {
			Identifier id = buffer.readIdentifier(); Identifier variantId = buffer.readBoolean() ? buffer.readIdentifier() : null;
			int price = buffer.readVarInt(), quantity = buffer.readVarInt(), sellItems = buffer.readVarInt(), sellEmeralds = buffer.readVarInt();
			int discriminator = buffer.readVarInt();
			if (discriminator < 0 || discriminator >= TradeVariantKind.values().length) throw new IllegalArgumentException("Invalid trade variant kind " + discriminator);
			return new CatalogEntryData(id, variantId, price, quantity, sellItems, sellEmeralds, readVariant(buffer, TradeVariantKind.values()[discriminator]));
		}
		private void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeIdentifier(id); buffer.writeBoolean(variantId != null); if (variantId != null) buffer.writeIdentifier(variantId);
			buffer.writeVarInt(price); buffer.writeVarInt(quantity); buffer.writeVarInt(sellItems); buffer.writeVarInt(sellEmeralds); buffer.writeVarInt(variant.kind().ordinal()); writeVariant(buffer, variant);
		}
		public TradeVariantKind variantKind() { return variant.kind(); }
		private static VariantData readVariant(RegistryFriendlyByteBuf buffer, TradeVariantKind kind) {
			return switch (kind) {
				case NONE -> new NoneVariantData();
				case ENCHANTMENT -> new EnchantmentVariantData(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
				case POTION -> {
					int count = buffer.readVarInt(); if (count < 1 || count > MAX_POTION_OPTIONS) throw new IllegalArgumentException("Invalid potion option count " + count);
					List<PotionOptionData> options = new ArrayList<>(count);
					for (int i = 0; i < count; i++) { int optionKind = buffer.readVarInt(); if (optionKind < 0 || optionKind >= PotionOptionKind.values().length) throw new IllegalArgumentException("Invalid potion option kind"); options.add(new PotionOptionData(buffer.readIdentifier(), PotionOptionKind.values()[optionKind])); }
					int containerCount = buffer.readVarInt(); if (containerCount < 1 || containerCount > PotionContainer.values().length) throw new IllegalArgumentException("Invalid potion container count");
					List<PotionContainer> containers = new ArrayList<>(containerCount);
					for (int i = 0; i < containerCount; i++) { PotionContainer container = PotionContainer.fromNetworkId(buffer.readVarInt()); if (container == null || containers.contains(container)) throw new IllegalArgumentException("Invalid potion container"); containers.add(container); }
					int defaultOption = buffer.readVarInt(); PotionContainer defaultContainer = PotionContainer.fromNetworkId(buffer.readVarInt());
					if (defaultOption < 0 || defaultOption >= options.size() || defaultContainer == null || !containers.contains(defaultContainer)) throw new IllegalArgumentException("Invalid potion defaults");
					yield new PotionVariantData(options, containers, defaultOption, defaultContainer);
				}
			};
		}
		private static void writeVariant(RegistryFriendlyByteBuf buffer, VariantData variant) {
			switch (variant) {
				case NoneVariantData ignored -> { }
				case EnchantmentVariantData enchantment -> { buffer.writeVarInt(enchantment.minimumLevel()); buffer.writeVarInt(enchantment.maximumLevel()); buffer.writeVarInt(enchantment.defaultLevel()); }
				case PotionVariantData potion -> {
					if (potion.options().isEmpty() || potion.options().size() > MAX_POTION_OPTIONS || potion.containers().isEmpty() || potion.containers().size() > PotionContainer.values().length) throw new IllegalArgumentException("Invalid bounded potion metadata");
					buffer.writeVarInt(potion.options().size());
					for (PotionOptionData option : potion.options()) { buffer.writeVarInt(option.optionKind().ordinal()); buffer.writeIdentifier(option.potionId()); }
					buffer.writeVarInt(potion.containers().size()); for (PotionContainer container : potion.containers()) buffer.writeVarInt(container.ordinal());
					buffer.writeVarInt(potion.defaultOption()); buffer.writeVarInt(potion.defaultContainer().ordinal());
				}
			}
		}
	}

	public record CatalogSync(int containerId, int merchantId, int version, List<CatalogEntryData> entries) implements CustomPacketPayload {
		public static final Type<CatalogSync> TYPE = new Type<>(TradeEverything.id("catalog_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CatalogSync> CODEC = StreamCodec.ofMember(CatalogSync::write, CatalogSync::read);
		private static CatalogSync read(RegistryFriendlyByteBuf buffer) {
			int containerId = buffer.readContainerId(); int merchantId = buffer.readVarInt(); int version = buffer.readVarInt(); int count = buffer.readVarInt();
			if (count < 0 || count > MAX_CATALOG_ENTRIES) throw new IllegalArgumentException("Invalid TradeEverything catalog size " + count);
			List<CatalogEntryData> entries = new ArrayList<>(count); for (int i = 0; i < count; i++) entries.add(CatalogEntryData.read(buffer));
			return new CatalogSync(containerId, merchantId, version, List.copyOf(entries));
		}
		private void write(RegistryFriendlyByteBuf buffer) { buffer.writeContainerId(containerId); buffer.writeVarInt(merchantId); buffer.writeVarInt(version); buffer.writeVarInt(entries.size()); entries.forEach(entry -> entry.write(buffer)); }
		@Override public Type<CatalogSync> type() { return TYPE; }
	}

	/** Only bounded selection primitives cross the network; the server owns all variant output. */
	public record PurchaseRequest(int containerId, int version, Identifier itemId, Identifier variantId, int enchantmentLevel, int potionOption, int potionContainer, int quantity) implements CustomPacketPayload {
		public static final Type<PurchaseRequest> TYPE = new Type<>(TradeEverything.id("purchase"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseRequest> CODEC = StreamCodec.ofMember(PurchaseRequest::write, PurchaseRequest::read);
		private static PurchaseRequest read(RegistryFriendlyByteBuf buffer) { int containerId = buffer.readContainerId(); int version = buffer.readVarInt(); Identifier itemId = buffer.readIdentifier(); Identifier variant = buffer.readBoolean() ? buffer.readIdentifier() : null; return new PurchaseRequest(containerId, version, itemId, variant, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()); }
		private void write(RegistryFriendlyByteBuf buffer) { buffer.writeContainerId(containerId); buffer.writeVarInt(version); buffer.writeIdentifier(itemId); buffer.writeBoolean(variantId != null); if (variantId != null) buffer.writeIdentifier(variantId); buffer.writeVarInt(enchantmentLevel); buffer.writeVarInt(potionOption); buffer.writeVarInt(potionContainer); buffer.writeVarInt(quantity); }
		@Override public Type<PurchaseRequest> type() { return TYPE; }
	}

	/** Bounded request data only; the slot selects a filled container, never its contents or price. */
	public record SellRequest(int containerId, int version, Identifier itemId, int quantity, int inventorySlot) implements CustomPacketPayload {
		public static final Type<SellRequest> TYPE = new Type<>(TradeEverything.id("sell"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SellRequest> CODEC = StreamCodec.ofMember(SellRequest::write, SellRequest::read);
		private static SellRequest read(RegistryFriendlyByteBuf buffer) { return new SellRequest(buffer.readContainerId(), buffer.readVarInt(), buffer.readIdentifier(), buffer.readVarInt(), buffer.readVarInt()); }
		private void write(RegistryFriendlyByteBuf buffer) { buffer.writeContainerId(containerId); buffer.writeVarInt(version); buffer.writeIdentifier(itemId); buffer.writeVarInt(quantity); buffer.writeVarInt(inventorySlot); }
		@Override public Type<SellRequest> type() { return TYPE; }
	}

	public enum TransactionType { BUY, SELL }
	public record PurchaseResult(int containerId, TransactionType transactionType, boolean success, String message) implements CustomPacketPayload {
		public static final Type<PurchaseResult> TYPE = new Type<>(TradeEverything.id("purchase_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseResult> CODEC = StreamCodec.ofMember(PurchaseResult::write, PurchaseResult::read);
		private static PurchaseResult read(RegistryFriendlyByteBuf buffer) { int containerId = buffer.readContainerId(); int operation = buffer.readVarInt(); if (operation < 0 || operation >= TransactionType.values().length) throw new IllegalArgumentException("Invalid TradeEverything transaction type " + operation); return new PurchaseResult(containerId, TransactionType.values()[operation], buffer.readBoolean(), buffer.readUtf(128)); }
		private void write(RegistryFriendlyByteBuf buffer) { buffer.writeContainerId(containerId); buffer.writeVarInt(transactionType.ordinal()); buffer.writeBoolean(success); buffer.writeUtf(message, 128); }
		@Override public Type<PurchaseResult> type() { return TYPE; }
	}
}
