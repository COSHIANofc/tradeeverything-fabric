package com.coshian.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coshian.tradeeverything.catalog.TradeVariantSelection.PotionContainer;
import com.coshian.tradeeverything.network.TradePayloads;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class TradePayloadsTest {
	@Test void catalogVariantMetadataRoundTripsAsBoundedDescriptors() {
		var sync = new TradePayloads.CatalogSync(4, 19, 7, List.of(
			new TradePayloads.CatalogEntryData(Identifier.withDefaultNamespace("diamond"), null, 3, 1, 1, 1, new TradePayloads.NoneVariantData()),
			new TradePayloads.CatalogEntryData(Identifier.withDefaultNamespace("enchanted_book"), Identifier.withDefaultNamespace("mending"), 9, 1, 1, 4, new TradePayloads.EnchantmentVariantData(1, 1, 1)),
			new TradePayloads.CatalogEntryData(Identifier.withDefaultNamespace("enchanted_book"), Identifier.withDefaultNamespace("sharpness"), 9, 1, 1, 4, new TradePayloads.EnchantmentVariantData(1, 5, 1)),
			new TradePayloads.CatalogEntryData(Identifier.withDefaultNamespace("potion"), Identifier.withDefaultNamespace("speed"), 4, 1, 4, 3,
				new TradePayloads.PotionVariantData(List.of(new TradePayloads.PotionOptionData(Identifier.withDefaultNamespace("swiftness"), TradePayloads.PotionOptionKind.NORMAL), new TradePayloads.PotionOptionData(Identifier.withDefaultNamespace("long_swiftness"), TradePayloads.PotionOptionKind.EXTENDED)), List.of(PotionContainer.NORMAL, PotionContainer.SPLASH, PotionContainer.LINGERING), 0, PotionContainer.NORMAL))));
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TradePayloads.CatalogSync.CODEC.encode(buffer, sync);
		assertEquals(sync, TradePayloads.CatalogSync.CODEC.decode(buffer));
	}

	@Test void purchaseRequestRoundTripsOnlyBoundedPrimitiveSelections() {
		var request = new TradePayloads.PurchaseRequest(3, 5, Identifier.withDefaultNamespace("potion"), Identifier.withDefaultNamespace("speed"), 1, 2, PotionContainer.LINGERING.ordinal(), 64);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TradePayloads.PurchaseRequest.CODEC.encode(buffer, request);
		assertEquals(request, TradePayloads.PurchaseRequest.CODEC.decode(buffer));
	}
}
