package com.coshian.tradeeverything.advancement;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AdvancementResourceTest {
	@Test void processedAllItemsUsesAllRequiredCanonicalCriteria() throws Exception {
		var json = JsonParser.parseString(Files.readString(Path.of("build/resources/main/data/tradeeverything/advancement/all_items.json"))).getAsJsonObject();
		var criteria = json.getAsJsonObject("criteria"); var requirements = json.getAsJsonArray("requirements");
		assertTrue(criteria.size() > 1); assertEquals(criteria.size(), requirements.size());
		assertTrue(criteria.has("special__enchanted_book")); assertTrue(criteria.has("special__potion"));
		for (var group : requirements) assertEquals(1, group.getAsJsonArray().size());
	}
	@Test void processedRootUsesEmeraldBlockTabBackground() throws Exception {
		var json = JsonParser.parseString(Files.readString(Path.of("build/resources/main/data/tradeeverything/advancement/first_trade.json"))).getAsJsonObject();
		assertEquals("minecraft:textures/block/emerald_block.png", json.getAsJsonObject("display").get("background").getAsString());
	}
}
