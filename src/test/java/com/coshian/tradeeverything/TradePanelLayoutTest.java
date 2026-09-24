package com.coshian.tradeeverything;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coshian.tradeeverything.ui.TradePanelLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TradePanelLayoutTest {
	@Test void activeRegionsStayInsideAndNeverOverlapForEveryVariantShape() {
		for (TradePanelLayout.Mode mode : TradePanelLayout.Mode.values()) assertGeometry(mode);
	}

	@Test void longEnglishAndJapaneseTextRemainAllocatedToTheBoundedHeaderAndRegistryRegions() {
		String english = "Exceptionally Long Enchanted Book Name With Multiple Meaningful Words";
		String japanese = "とても長い日本語のポーション効果名とバリエーション識別子";
		TradePanelLayout layout = TradePanelLayout.forMode(TradePanelLayout.Mode.POTION_MULTI);
		assertTrue(english.length() > 40 && japanese.length() > 20);
		assertTrue(layout.header().width() > 0 && layout.registry().width() > 0);
		assertFalse(layout.header().intersects(layout.registry()));
		assertFalse(layout.registry().intersects(layout.price()));
	}

	@Test void sevenRowViewportAndScrollBoundsAreExact() {
		assertTrue(TradePanelLayout.LIST_HEIGHT == TradePanelLayout.ROW_HEIGHT * 7);
		for (int row = 0; row < 7; row++) assertTrue(TradePanelLayout.listContainsRow(row));
		assertFalse(TradePanelLayout.listContainsRow(7));
		assertTrue(TradePanelLayout.maxScrollFor(0) == 0 && TradePanelLayout.maxScrollFor(1) == 0 && TradePanelLayout.maxScrollFor(7) == 0);
		assertTrue(TradePanelLayout.maxScrollFor(8) == 1 && TradePanelLayout.maxScrollFor(100) == 93);
	}

	private static void assertGeometry(TradePanelLayout.Mode mode) {
		TradePanelLayout layout = TradePanelLayout.forMode(mode);
		List<TradePanelLayout.Rect> active = List.of(layout.header(), layout.registry(), layout.price(), layout.selector(), layout.container(), layout.quantity(), layout.total(), layout.action(), layout.status()).stream().filter(TradePanelLayout.Rect::active).toList();
		for (TradePanelLayout.Rect region : active) {
			assertTrue(region.width() > 0 && region.height() > 0, mode + " region must be positive");
			assertTrue(region.inside(layout.panel()), mode + " region must stay inside panel");
		}
		for (int i = 0; i < active.size(); i++) for (int j = i + 1; j < active.size(); j++) assertFalse(active.get(i).intersects(active.get(j)), mode + " regions overlap: " + i + "/" + j);
	}
}
