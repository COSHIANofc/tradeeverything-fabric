package com.coshian.tradeeverything.ui;

/** GUI-coordinate layout shared by the client renderer/widgets and non-client geometry tests. */
public final class TradePanelLayout {
	public static final int PANEL_LEFT = 213;
	public static final int PANEL_WIDTH = 99;
	public static final int PANEL_TOP = 76;
	public static final int SCREEN_HEIGHT = 292;
	public static final int ROW_HEIGHT = 24;
	public static final int VISIBLE_ROWS = 7;
	public static final int LIST_TOP = 76;
	public static final int LIST_HEIGHT = ROW_HEIGHT * VISIBLE_ROWS;
	public enum Mode { ORDINARY, ENCHANTMENT_SINGLE, ENCHANTMENT_MULTI, POTION_SINGLE, POTION_MULTI }
	public record Rect(int x, int y, int width, int height) {
		public boolean active() { return width > 0 && height > 0; }
		public boolean inside(Rect outer) { return x >= outer.x && y >= outer.y && x + width <= outer.x + outer.width && y + height <= outer.y + outer.height; }
		public boolean intersects(Rect other) { return active() && other.active() && x < other.x + other.width && x + width > other.x && y < other.y + other.height && y + height > other.y; }
	}
	private final Rect panel = new Rect(PANEL_LEFT, PANEL_TOP, PANEL_WIDTH, 174);
	private final Rect header = new Rect(PANEL_LEFT + 3, PANEL_TOP + 2, PANEL_WIDTH - 6, 16);
	private final Rect registry = new Rect(PANEL_LEFT + 3, PANEL_TOP + 20, PANEL_WIDTH - 6, 12);
	private final Rect price = new Rect(PANEL_LEFT + 3, PANEL_TOP + 34, PANEL_WIDTH - 6, 12);
	private final Rect selector;
	private final Rect container;
	private final Rect quantity;
	private final Rect total;
	private final Rect action;
	private final Rect status;

	private TradePanelLayout(Mode mode) {
		int cursor = PANEL_TOP + 50;
		if (mode == Mode.ENCHANTMENT_SINGLE || mode == Mode.ENCHANTMENT_MULTI || mode == Mode.POTION_SINGLE || mode == Mode.POTION_MULTI) {
			selector = new Rect(PANEL_LEFT + 2, cursor, PANEL_WIDTH - 4, 18); cursor += 21;
		} else selector = empty(cursor);
		if (mode == Mode.POTION_SINGLE || mode == Mode.POTION_MULTI) { container = new Rect(PANEL_LEFT + 2, cursor, PANEL_WIDTH - 4, 18); cursor += 21; }
		else container = empty(cursor);
		quantity = new Rect(PANEL_LEFT + 2, cursor + 2, PANEL_WIDTH - 4, 18);
		total = new Rect(PANEL_LEFT + 3, cursor + 23, PANEL_WIDTH - 6, 12);
		action = new Rect(PANEL_LEFT + 2, cursor + 38, PANEL_WIDTH - 4, 20);
		status = new Rect(PANEL_LEFT + 3, cursor + 61, PANEL_WIDTH - 6, 12);
	}
	private static Rect empty(int y) { return new Rect(PANEL_LEFT, y, 0, 0); }
	public static TradePanelLayout forMode(Mode mode) { return new TradePanelLayout(mode); }
	public Rect panel() { return panel; }
	public Rect header() { return header; }
	public Rect registry() { return registry; }
	public Rect price() { return price; }
	public Rect selector() { return selector; }
	public Rect container() { return container; }
	public Rect quantity() { return quantity; }
	public Rect total() { return total; }
	public Rect action() { return action; }
	public Rect status() { return status; }
	public static boolean listContainsRow(int row) { return row >= 0 && row < VISIBLE_ROWS; }
	public static int maxScrollFor(int resultCount) { return Math.max(0, resultCount - VISIBLE_ROWS); }
}
