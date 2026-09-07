package com.coshian.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClientArchitectureMetadataTest {
	@Test void metadataDeclaresCommonAndClientEntrypoints() throws Exception {
		var metadata = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
		assertEquals("*", metadata.get("environment").getAsString());
		assertTrue(metadata.getAsJsonObject("entrypoints").has("main"));
		assertTrue(metadata.getAsJsonObject("entrypoints").has("client"));
		assertTrue(Files.exists(Path.of("src/main/resources/assets/tradeeverything/lang/en_us.json")));
		assertTrue(Files.exists(Path.of("src/main/resources/assets/tradeeverything/lang/ja_jp.json")));
	}

	@Test void networkingAndClientLayersArePresent() throws Exception {
		String common = Files.walk(Path.of("src/main/java")).filter(path -> path.toString().endsWith(".java")).map(ClientArchitectureMetadataTest::read).reduce("", String::concat);
		String client = Files.walk(Path.of("src/client/java")).filter(path -> path.toString().endsWith(".java")).map(ClientArchitectureMetadataTest::read).reduce("", String::concat);
		assertTrue(common.contains("CustomPacketPayload") && common.contains("ServerPlayNetworking"));
		assertTrue(client.contains("ClientModInitializer") && client.contains("EditBox") && client.contains("ClientPlayNetworking"));
		assertEquals(List.of("containerId", "version", "itemId", "variantId", "quantity"), java.util.Arrays.stream(com.coshian.tradeeverything.network.TradePayloads.PurchaseRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList(),
			"Client purchase requests may name a catalog variant but must not contain price, output, or ItemStack data");
		assertEquals(List.of("containerId", "version", "itemId", "quantity", "inventorySlot"), java.util.Arrays.stream(com.coshian.tradeeverything.network.TradePayloads.SellRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList(),
			"Client Sell requests may select one inventory stack, but must not contain price, reward, or contents");
		assertEquals(List.of("containerId", "transactionType", "success", "message"), java.util.Arrays.stream(com.coshian.tradeeverything.network.TradePayloads.PurchaseResult.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList(),
			"Transaction responses must carry their operation type independently of the active UI tab");
	}

	@Test void visibleBrandingUsesCoshianWhileRepositoryUrlRemainsWorking() throws Exception {
		var metadata = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
		assertEquals("COSHIAN", metadata.getAsJsonArray("authors").get(0).getAsString());
		assertTrue(metadata.getAsJsonObject("contact").get("sources").getAsString().contains("github.com/COSHIANofc/TradeEverything-Mod-"));
	}
	private static String read(Path path) { try { return Files.readString(path); } catch (Exception exception) { throw new RuntimeException(exception); } }
}
