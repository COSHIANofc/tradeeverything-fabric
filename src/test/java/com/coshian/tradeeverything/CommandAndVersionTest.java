package com.coshian.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.coshian.tradeeverything.search.SearchInputRouting;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class CommandAndVersionTest {
	@Test void authoritativeVersionIsDevRevisionFormat() throws Exception {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(Path.of("gradle.properties"))) { properties.load(reader); }
		assertEquals("0.7.a-dev", properties.getProperty("mod_version"));
		var metadata = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
		assertEquals("${version}", metadata.get("version").getAsString());
		assertTrue(Files.readString(Path.of("README.md")).contains("0.7.a-dev"));
		assertTrue(Files.readString(Path.of("README_ja.md")).contains("0.7.a-dev"));
	}

	@Test void focusedSearchConsumesEditableKeysWithoutSpecialCasingBindings() {
		assertTrue(SearchInputRouting.consumesFocusedKey(true, false, true, false), "A focused editable box must block parent key bindings before its character event");
		assertTrue(SearchInputRouting.consumesFocusedKey(true, true, true, false), "EditBox editing keys remain consumed");
		assertFalse(SearchInputRouting.consumesFocusedKey(true, false, true, true), "Escape must continue to normal screen closing behavior");
		assertFalse(SearchInputRouting.consumesFocusedKey(false, false, true, false), "An unfocused field must not consume input");
	}

	@Test void localizedSearchPlaceholdersAreAvailable() throws Exception {
		var english = language("en_us"); var japanese = language("ja_jp");
		assertEquals("Search", english.get("screen.tradeeverything.search_placeholder").getAsString());
		assertEquals("検索", japanese.get("screen.tradeeverything.search_placeholder").getAsString());
	}

	private static com.google.gson.JsonObject language(String locale) throws Exception {
		return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tradeeverything/lang/" + locale + ".json"))).getAsJsonObject();
	}
}
