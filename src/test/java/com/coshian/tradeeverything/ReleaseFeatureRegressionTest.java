package com.coshian.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fast source-level guardrails for resources/client geometry that do not need a running game. */
final class ReleaseFeatureRegressionTest {
	@Test void customTradingPostResourcesAndCommandAreGone() throws Exception {
		assertFalse(Files.exists(Path.of("src/main/resources/data/tradeeverything/worldgen/structure/trading_post.json")));
		String commands = Files.readString(Path.of("src/main/java/com/coshian/tradeeverything/command/TradeEverythingCommands.java"));
		assertFalse(commands.contains("Commands.literal(\"place\")"));
	}
	@Test void rightPanelAndViewportUseOneAuthoritativeGeometryModel() throws Exception {
		String screen = Files.readString(Path.of("src/client/java/com/coshian/tradeeverything/client/screen/TradeEverythingScreen.java"));
		assertTrue(screen.contains("TradePanelLayout") && screen.contains("panelLayout()") && screen.contains("layoutWidgets()"));
		assertTrue(com.coshian.tradeeverything.ui.TradePanelLayout.VISIBLE_ROWS == 7);
		assertTrue(com.coshian.tradeeverything.ui.TradePanelLayout.LIST_HEIGHT == com.coshian.tradeeverything.ui.TradePanelLayout.ROW_HEIGHT * 7);
	}
	@Test void swampHookIsLimitedToTheStructureWitchAdd() throws Exception {
		String mixin = Files.readString(Path.of("src/main/java/com/coshian/tradeeverything/mixin/SwampHutPieceMixin.java"));
		assertTrue(mixin.contains("@Mixin(SwampHutPiece.class)") && mixin.contains("ordinal = 0") && mixin.contains("instanceof Witch"));
		assertFalse(mixin.contains("setNoAi"));
	}
}
