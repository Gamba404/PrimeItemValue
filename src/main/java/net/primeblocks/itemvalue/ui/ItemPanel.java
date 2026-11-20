package net.primeblocks.itemvalue.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.primeblocks.itemvalue.Lang;
import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.catalog.CatalogEntry;
import net.primeblocks.itemvalue.catalog.ItemCategory;
import net.primeblocks.itemvalue.value.Currency;
import net.primeblocks.itemvalue.value.ItemValue;
import net.primeblocks.itemvalue.value.ValueFormatter;
import net.primeblocks.itemvalue.value.ValuePoint;
import net.primeblocks.itemvalue.value.ValueHistory;
import net.primeblocks.itemvalue.value.ValueLookup;

/** Draws the item browser and detail view, and handles panel input. */
public final class ItemPanel {

	private static final int SDL_BACKSPACE = 8;

	private static final int MAX_QUERY = 48;

	private static final int EFFECTS_WIDTH = 130;

	private static final int SLOT = 18;
	private static final int MARGIN = 4;
	private static final int GAP = 6;
	private static final int ROW_HEIGHT = 12;

	private static final int TITLE_HEIGHT = ROW_HEIGHT + 2;
	private static final int BUTTON = 12;
	private static final int BUTTON_SPACING = 2;
	private static final int GRIP = 8;

	private static final int GRIP_HIT = 14;

	private static final int COLOUR_BORDER = 0xFF3A3A4A;
	private static final int COLOUR_TAB_ACTIVE = 0xFF4A4A6A;
	private static final int COLOUR_TAB_IDLE = 0xFF24242E;
	private static final int COLOUR_TEXT = 0xFFE8E8E8;
	private static final int COLOUR_MUTED = 0xFF9A9A9A;
	private static final int COLOUR_SEARCH_BG = 0xFF101014;
	private static final int COLOUR_SLOT_HOVER = 0x60FFFFFF;
	private static final int COLOUR_ON = 0xFF9BD9A0;
	private static final int COLOUR_OFF = 0xFFE08A8A;
	private static final int COLOUR_GRIP = 0xFF6A6A8A;
	private static final int COLOUR_MENU = 0xFF1C1C24;
	private static final int COLOUR_CHART = 0xFF7FC7A0;
	private static final int COLOUR_CHART_WEAK = 0xFF46705C;

	private static final int COLOUR_GAP = 0xFF4A3A3A;

	private static final int COLOUR_COLUMN_HOVER = 0x30FFFFFF;

	private static final int COLOUR_VALUE = 0xFFFFD966;

	private static final int MENU_ROW = ROW_HEIGHT + 2;

	private static final int[] RANGES = {7, 30, 90};

	private final ValueLookup lookup;
	private final ItemValueConfig config;
	private final ItemStackCache stacks = new ItemStackCache();

	private ItemCategory category = ItemCategory.CUSTOM;
	private String query = "";
	private boolean searchFocused;
	private int scrollRow;

	private Drag drag = Drag.NONE;

	private boolean menuOpen;

	private CatalogEntry detail;

	private int detailDays = 30;
	private ValueHistory detailHistory;
	private boolean detailStale;
	private double dragOffsetX;
	private double dragOffsetY;

	private List<CatalogEntry> results = List.of();
	private boolean resultsStale = true;

	private int[] collapsedBox;

	private List<Component> pendingTooltip;
	private int pendingTooltipX;
	private int pendingTooltipY;

	private Layout layout;
	private int visibleSlots;

	private double lastMouseX = Double.NaN;
	private double lastMouseY = Double.NaN;

	private static final long SLOW_SEARCH_MICROS = 3_000L;

	// Update the text immediately, but wait briefly before rebuilding the grid.
	private static final long SEARCH_DELAY_MILLIS = 160L;

	private long searchDueAt;

	private boolean warnedSlowSearch;

	public ItemPanel(ValueLookup lookup, ItemValueConfig config) {
		this.lookup = lookup;
		this.config = config;
	}

	private enum Drag {
		NONE,
		MOVE,
		RESIZE
	}

	private Layout computeLayout(int screenWidth, int screenHeight, int containerRight) {
		float scale = config.scale();
		boolean placed = config.panelX != null && config.panelY != null;

		int columns = config.wantedColumns();

		if (!placed) {

			int taken = containerRight + GAP + effectsReserve();
			int room = (int) ((screenWidth - taken - MARGIN) / scale);
			int fitting = (room - MARGIN * 2) / SLOT;

			columns = Math.clamp(config.panelAutoSize ? fitting : Math.min(columns, fitting),
					ItemValueConfig.MIN_COLUMNS, ItemValueConfig.MAX_COLUMNS);
		}

		int width = columns * SLOT + MARGIN * 2;

		int headerHeight = TITLE_HEIGHT + ROW_HEIGHT + 2 + ROW_HEIGHT + 4;
		int footerHeight = ROW_HEIGHT + 2;
		int chrome = MARGIN * 2 + headerHeight + footerHeight;

		int localHeightAvailable = (int) ((screenHeight - MARGIN * 2) / scale);
		int fittingRows = (localHeightAvailable - chrome) / SLOT;

		if (fittingRows < 1) {
			return null;
		}

		int rows = config.panelAutoSize && !placed
				? fittingRows
				: Math.max(1, Math.min(config.wantedRows(), fittingRows));
		int height = chrome + rows * SLOT;

		int onScreenWidth = Math.round(width * scale);
		int onScreenHeight = Math.round(height * scale);

		int originX = placed ? config.panelX : screenWidth - onScreenWidth - MARGIN;
		int originY = placed ? config.panelY : Math.max(MARGIN, (screenHeight - onScreenHeight) / 2);

		originX = Math.clamp(originX, 0, Math.max(0, screenWidth - onScreenWidth));
		originY = Math.clamp(originY, 0, Math.max(0, screenHeight - onScreenHeight));

		return new Layout(originX, originY, scale, width, height, columns, rows, headerHeight);
	}

	public void draw(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight,
			int containerRight, int mouseX, int mouseY) {
		if (!config.panelEnabled) {
			layout = null;
			return;
		}

		pendingTooltip = null;
		pendingTooltipX = mouseX;
		pendingTooltipY = mouseY;

		if (config.panelCollapsed) {
			drawHandle(context, font, screenWidth, screenHeight, mouseX, mouseY);
			layout = null;
			emitTooltip(context, font);
			return;
		}

		collapsedBox = null;

		Layout box = computeLayout(screenWidth, screenHeight, containerRight);
		layout = box;

		if (box == null) {
			return;
		}

		refreshResults();

		lastMouseX = mouseX;
		lastMouseY = mouseY;

		int localX = (int) box.toLocalX(mouseX);
		int localY = (int) box.toLocalY(mouseY);

		context.pose().pushMatrix();
		context.pose().translate(box.originX(), box.originY());

		if (Math.abs(box.scale() - 1.0f) > 1e-4f) {
			context.pose().scale(box.scale());
		}

		int background = config.backgroundColour();

		if ((background >>> 24) != 0) {
			context.fill(0, 0, box.width(), box.height(), background);
		}

		drawBorder(context, box);

		List<Component> titleTooltip = drawTitle(context, font, box, localX, localY);

		CatalogEntry hovered = null;
		List<Component> viewTooltip;

		if (detail != null) {
			viewTooltip = drawDetail(context, font, box, localX, localY);
		} else {
			drawTabs(context, font, box, localX, localY);
			drawSearch(context, font, box);

			hovered = drawGrid(context, box, localX, localY);
			viewTooltip = null;

			drawFooter(context, font, box, hovered);
		}
		drawGrip(context, box, localX, localY);

		List<Component> menuTooltip = drawMenu(context, font, box, localX, localY);
		List<Component> buttonTooltip = menuTooltip != null ? menuTooltip
				: titleTooltip != null ? titleTooltip : viewTooltip;

		context.pose().popMatrix();

		if (buttonTooltip != null) {
			pendingTooltip = buttonTooltip;
		} else if (hovered != null) {
			pendingTooltip = tooltipFor(hovered);
		}

		emitTooltip(context, font);
	}

	public void emitTooltip(GuiGraphicsExtractor context, Font font) {
		if (pendingTooltip != null) {
			context.setComponentTooltipForNextFrame(font, pendingTooltip,
					pendingTooltipX, pendingTooltipY);
		}
	}

	private void drawHandle(GuiGraphicsExtractor context, Font font, int screenWidth,
			int screenHeight, int mouseX, int mouseY) {
		String label = Lang.get("primeitemvalue.panel.handle");
		int width = font.width(label) + MARGIN * 4;
		int height = ROW_HEIGHT + MARGIN;
		int x = screenWidth - MARGIN - width;
		int y = screenHeight - MARGIN - height;

		collapsedBox = new int[] {x, y, width, height};

		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

		context.fill(x, y, x + width, y + height, config.backgroundColour());
		context.fill(x, y, x + width, y + 1, COLOUR_BORDER);
		context.fill(x, y + height - 1, x + width, y + height, COLOUR_BORDER);
		context.fill(x, y, x + 1, y + height, COLOUR_BORDER);
		context.fill(x + width - 1, y, x + width, y + height, COLOUR_BORDER);
		context.text(font, label, x + MARGIN * 2, y + MARGIN / 2 + 1,
				hovered ? COLOUR_ON : COLOUR_TEXT);

		if (hovered) {
			pendingTooltip = List.of(
					Component.literal(Lang.get("primeitemvalue.panel.handle.title")),
					Component.literal("§7").append(
							Component.literal(Lang.get("primeitemvalue.panel.handle.hint"))));
		}
	}

	private static void drawBorder(GuiGraphicsExtractor context, Layout box) {
		context.fill(0, 0, box.width(), 1, COLOUR_BORDER);
		context.fill(0, box.height() - 1, box.width(), box.height(), COLOUR_BORDER);
		context.fill(0, 0, 1, box.height(), COLOUR_BORDER);
		context.fill(box.width() - 1, 0, box.width(), box.height(), COLOUR_BORDER);
	}

	private void drawTabs(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		int tabWidth = (box.width() - MARGIN * 2) / 2;
		int y = tabsY();

		for (ItemCategory candidate : ItemCategory.values()) {
			int x = MARGIN + (candidate == ItemCategory.CUSTOM ? 0 : tabWidth);
			boolean active = candidate == category;

			context.fill(x, y, x + tabWidth, y + ROW_HEIGHT,
					active ? COLOUR_TAB_ACTIVE : COLOUR_TAB_IDLE);

			if (!active && within(localX, localY, x, y, tabWidth, ROW_HEIGHT)) {
				context.fill(x, y, x + tabWidth, y + ROW_HEIGHT, COLOUR_SLOT_HOVER);
			}

			String label = candidate.label() + " " + lookup.catalog().countOf(candidate);
			int textX = x + Math.max(2, (tabWidth - font.width(label)) / 2);
			context.text(font, label, textX, y + 2, active ? COLOUR_TEXT : COLOUR_MUTED);
		}
	}

	private void drawSearch(GuiGraphicsExtractor context, Font font, Layout box) {
		int x = MARGIN;
		int y = searchY();
		int width = box.width() - MARGIN * 2;

		context.fill(x, y, x + width, y + ROW_HEIGHT, COLOUR_SEARCH_BG);

		if (searchFocused) {
			context.fill(x, y, x + width, y + 1, COLOUR_TAB_ACTIVE);
			context.fill(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, COLOUR_TAB_ACTIVE);
		}

		String shown = query.isEmpty() && !searchFocused
				? Lang.get("primeitemvalue.panel.search.hint")
				: query;
		int colour = query.isEmpty() && !searchFocused ? COLOUR_MUTED : COLOUR_TEXT;
		int maxWidth = width - 6;

		while (font.width(shown) > maxWidth && shown.length() > 1) {
			shown = shown.substring(1);
		}

		context.text(font, shown, x + 3, y + 2, colour);

		if (searchFocused) {
			int caretX = x + 3 + font.width(shown);
			context.fill(caretX, y + 2, caretX + 1, y + ROW_HEIGHT - 2, COLOUR_TEXT);
		}
	}

	private CatalogEntry drawGrid(GuiGraphicsExtractor context, Layout box,
			int localX, int localY) {
		int gridY = gridY(box);
		CatalogEntry hovered = null;

		clampScroll(box);
		int first = scrollRow * box.columns();
		int drawn = 0;

		for (int slot = 0; slot < box.columns() * box.rows(); slot++) {
			int index = first + slot;

			if (index >= results.size()) {
				break;
			}

			drawn++;

			int x = MARGIN + (slot % box.columns()) * SLOT;
			int y = gridY + (slot / box.columns()) * SLOT;

			CatalogEntry entry = results.get(index);
			ItemStack stack = stacks.get(entry);

			if (!stack.isEmpty()) {

				context.fakeItem(stack, x + 1, y + 1);
			} else {
				context.fill(x + 1, y + 1, x + 17, y + 17, 0x40FF5555);
			}

			if (within(localX, localY, x, y, SLOT, SLOT)) {
				context.fill(x + 1, y + 1, x + 17, y + 17, COLOUR_SLOT_HOVER);
				hovered = entry;
			}
		}

		visibleSlots = drawn;
		return hovered;
	}

	private void drawFooter(GuiGraphicsExtractor context, Font font, Layout box,
			CatalogEntry hovered) {
		int y = box.height() - MARGIN - ROW_HEIGHT + 2;
		int room = box.width() - MARGIN * 2 - GRIP;

		if (hovered == null) {
			String count = Lang.get("primeitemvalue.panel.count", results.size());

			if (font.width(count) <= room) {
				context.text(font, count, MARGIN, y, COLOUR_MUTED);
			}

			return;
		}

		Optional<ItemValue> value = lookup.forEntry(hovered);
		String price = value
				.map(item -> ValueFormatter.display(item) + " " + item.currency().label())
				.orElse("");

		int priceWidth = price.isEmpty() ? 0 : font.width(price);

		if (priceWidth > 0 && priceWidth <= room) {
			context.text(font, price, MARGIN + room - priceWidth, y, COLOUR_VALUE);
		}

		int nameRoom = room - priceWidth - MARGIN;

		if (nameRoom > 8) {
			context.text(font, trim(font, hovered.plainName(), nameRoom), MARGIN, y, COLOUR_TEXT);
		}
	}

	private static String trim(Font font, String text, int room) {
		if (font.width(text) <= room) {
			return text;
		}

		String cut = text;

		while (!cut.isEmpty() && font.width(cut + "...") > room) {
			cut = cut.substring(0, cut.length() - 1);
		}

		return cut + "...";
	}

	private List<Component> drawTitle(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		int y = titleY();

		context.fill(MARGIN, y, box.width() - MARGIN, y + ROW_HEIGHT,
				drag == Drag.MOVE ? COLOUR_TAB_ACTIVE : COLOUR_TAB_IDLE);

		String title = Lang.get("primeitemvalue.panel.title");
		int buttonsWidth = BUTTON * 2 + BUTTON_SPACING + MARGIN;
		int titleRoom = box.width() - MARGIN * 2 - buttonsWidth;

		if (font.width(title) <= titleRoom) {
			context.text(font, title, MARGIN + 3, y + 2, COLOUR_MUTED);

			String build = marketGroup();

			if (build != null) {
				int at = MARGIN + 3 + font.width(title) + MARGIN;

                                if (at + font.width(build) <= MARGIN + titleRoom) {
					context.text(font, build, at, y + 2, COLOUR_ON);
				}
			}
		}

		List<Component> tooltip = null;

		if (within(localX, localY, MARGIN, y, box.width() - MARGIN * 2, ROW_HEIGHT)) {
			tooltip = List.of(
					Component.literal(Lang.get("primeitemvalue.panel.move")),
					grey("primeitemvalue.panel.move.hint"),
					grey("primeitemvalue.panel.move.reset"));
		}

		if (drawButton(context, font, menuX(box), y, "≡",
				menuOpen ? COLOUR_ON : COLOUR_TEXT, localX, localY)) {
			tooltip = hint("primeitemvalue.panel.menu", "primeitemvalue.panel.menu.hint");
		}

		if (drawButton(context, font, collapseX(box), y, "_", COLOUR_TEXT, localX, localY)) {
			tooltip = hint("primeitemvalue.panel.collapse", "primeitemvalue.panel.collapse.hint");
		}

		return tooltip;
	}

	private List<Component> drawMenu(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		if (!menuOpen) {
			return null;
		}

		List<String> rows = menuRows();
		int width = menuWidth(font, box, rows);
		int x = menuLeft(box, width);
		int y = titleY() + ROW_HEIGHT + 1;
		int height = rows.size() * MENU_ROW + 2;

		context.fill(x, y, x + width, y + height, COLOUR_MENU);
		drawFrame(context, x, y, width, height);

		List<Component> tooltip = null;

		for (int i = 0; i < rows.size(); i++) {
			int rowY = y + 1 + i * MENU_ROW;
			boolean hovered = within(localX, localY, x + 1, rowY, width - 2, MENU_ROW);

			if (hovered) {
				context.fill(x + 1, rowY, x + width - 1, rowY + MENU_ROW, COLOUR_TAB_ACTIVE);
				tooltip = menuTooltip(i);
			}

			context.text(font, rows.get(i), x + MARGIN * 2, rowY + 2,
					hovered ? COLOUR_ON : COLOUR_TEXT);
		}

		return tooltip;
	}

	private static void drawFrame(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + 1, COLOUR_BORDER);
		context.fill(x, y + h - 1, x + w, y + h, COLOUR_BORDER);
		context.fill(x, y, x + 1, y + h, COLOUR_BORDER);
		context.fill(x + w - 1, y, x + w, y + h, COLOUR_BORDER);
	}

	private List<String> menuRows() {
		return List.of(
				Lang.get("primeitemvalue.panel.scale", config.scaleLabel()),
				Lang.get("primeitemvalue.panel.background", config.backgroundLabel()),
				Lang.get(config.tooltipEnabled
						? "primeitemvalue.panel.values.on"
						: "primeitemvalue.panel.values.off"),
				Lang.get(config.database == null || config.database.isBlank()
						? "primeitemvalue.panel.citybuild.auto"
						: "primeitemvalue.panel.citybuild.fixed", groupLabel()),
				Lang.get("primeitemvalue.panel.language", languageLabel()),
				Lang.get("primeitemvalue.panel.detail_style", Lang.get(config.detailCompact
						? "primeitemvalue.detail.style.compact"
						: "primeitemvalue.detail.style.wide")),
				Lang.get("primeitemvalue.panel.reset"));
	}

	private static String marketGroup() {
		String group = groupLabel();

		return group == null ? null : group;
	}

	private static String groupLabel() {
		net.primeblocks.itemvalue.market.MarketSync sync =
				net.primeblocks.itemvalue.PrimeItemValueClient.market();

		if (sync == null) {
			return null;
		}

		String group = sync.group();

		return group == null ? null : group.toUpperCase(java.util.Locale.ROOT)
				+ (sync.syncing() ? " …" : "");
	}

	private List<Component> menuTooltip(int row) {
		return switch (row) {
			case 0 -> List.of(grey("primeitemvalue.panel.scale.hint"));
			case 1 -> List.of(grey("primeitemvalue.panel.background.hint"));
			case 2 -> List.of(Component.literal("§7" + lookup.provider().sourceLabel()));
			case 3 -> List.of(grey("primeitemvalue.panel.citybuild.hint"));
			case 4 -> List.of(grey("primeitemvalue.panel.language.hint"));
			case 5 -> List.of(grey("primeitemvalue.panel.detail_style.hint"));
			default -> List.of(grey("primeitemvalue.panel.reset.hint"));
		};
	}

	private static int menuWidth(Font font, Layout box, List<String> rows) {
		int width = 0;

		for (String row : rows) {
			width = Math.max(width, font.width(row));
		}

		return Math.min(width + MARGIN * 4, box.width() - MARGIN * 2);
	}

	private static int menuLeft(Layout box, int width) {
		return Math.max(MARGIN, collapseX(box) + BUTTON - width);
	}

	private boolean clickMenu(Layout box, Font font, int localX, int localY, int button) {
		List<String> rows = menuRows();
		int width = menuWidth(font, box, rows);
		int x = menuLeft(box, width);
		int y = titleY() + ROW_HEIGHT + 1;

		for (int i = 0; i < rows.size(); i++) {
			if (!within(localX, localY, x + 1, y + 1 + i * MENU_ROW, width - 2, MENU_ROW)) {
				continue;
			}

			switch (i) {
				case 0 -> config.changeScale(button == 1
						? -ItemValueConfig.SCALE_STEP
						: ItemValueConfig.SCALE_STEP);
				case 1 -> config.cycleBackground();
				case 2 -> config.tooltipEnabled = !config.tooltipEnabled;
				case 3 -> cycleCityBuild();
				case 4 -> cycleLanguage();
				case 5 -> config.detailCompact = !config.detailCompact;
				default -> {
					config.panelX = null;
					config.panelY = null;
					config.panelColumns = 0;
					config.panelRows = 0;
					config.panelScale = 1.0;
					config.panelAutoSize = true;
					menuOpen = false;
				}
			}

			config.markDirty();
			return true;
		}

		return false;
	}

	private String languageLabel() {
		String chosen = config.language;

		if (chosen == null || chosen.isBlank() || "auto".equalsIgnoreCase(chosen)) {
			return Lang.get("primeitemvalue.language.auto",
					Lang.get("primeitemvalue.language." + Lang.fromGame()));
		}

		return Lang.get("primeitemvalue.language." + Lang.current());
	}

	private void cycleLanguage() {
		String current = config.language == null ? "auto" : config.language.toLowerCase(
				java.util.Locale.ROOT);
		int next = Lang.CHOICES.indexOf(current) + 1;

		config.language = Lang.CHOICES.get(next >= Lang.CHOICES.size() ? 0 : next);
		config.markDirty();
		config.save();
	}

	private void cycleCityBuild() {
		List<String> known = net.primeblocks.itemvalue.market.ServerGroup.KNOWN;
		String current = config.database;

		if (current == null || current.isBlank()) {
			config.database = known.get(0);
		} else {
			int next = known.indexOf(current.toLowerCase(java.util.Locale.ROOT)) + 1;
			config.database = next <= 0 || next >= known.size() ? null : known.get(next);
		}

		config.markDirty();
		config.save();

		net.primeblocks.itemvalue.market.MarketSync sync =
				net.primeblocks.itemvalue.PrimeItemValueClient.market();

		if (sync != null) {
			sync.resync();
		}
	}

	private static int menuX(Layout box) {
		return collapseX(box) - BUTTON - BUTTON_SPACING;
	}

	private static int collapseX(Layout box) {
		return box.width() - MARGIN - BUTTON;
	}

	private static boolean drawButton(GuiGraphicsExtractor context, Font font, int x, int y,
			String label, int labelColour, int localX, int localY) {
		boolean hovered = within(localX, localY, x, y, BUTTON, ROW_HEIGHT);

		context.fill(x, y, x + BUTTON, y + ROW_HEIGHT, hovered ? COLOUR_TAB_ACTIVE : COLOUR_TAB_IDLE);

		int textX = x + Math.max(1, (BUTTON - font.width(label)) / 2);
		context.text(font, label, textX, y + 2, labelColour);

		return hovered;
	}

	private void drawGrip(GuiGraphicsExtractor context, Layout box, int localX, int localY) {
		int x = gripX(box);
		int y = gripY(box);
		int colour = drag == Drag.RESIZE
				|| within(localX, localY, x - (GRIP_HIT - GRIP), y - (GRIP_HIT - GRIP), GRIP_HIT, GRIP_HIT)
				? COLOUR_TEXT
				: COLOUR_GRIP;

		for (int step = 0; step < 3; step++) {
			int offset = step * 3;
			context.fill(x + offset, y + GRIP - 1 - offset, x + GRIP, y + GRIP - offset, colour);
		}
	}

	private static int titleY() {
		return MARGIN;
	}

	private static int tabsY() {
		return MARGIN + TITLE_HEIGHT;
	}

	private static int searchY() {
		return tabsY() + ROW_HEIGHT + 2;
	}

	private static int gridY(Layout box) {
		return MARGIN + box.headerHeight() - 4;
	}

	private static int gripX(Layout box) {
		return box.width() - GRIP - 1;
	}

	private static int gripY(Layout box) {
		return box.height() - GRIP - 1;
	}

	private static int effectsReserve() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || minecraft.player.getActiveEffects().isEmpty()) {
			return 0;
		}

		return EFFECTS_WIDTH;
	}

	private static List<Component> hint(String titleKey, String hintKey) {
		return List.of(Component.translatable(titleKey), grey(hintKey));
	}

	private static Component grey(String key) {
		return Component.literal("§7").append(Component.translatable(key));
	}

	private List<Component> drawDetail(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		return config.detailCompact
				? drawDetailCompact(context, font, box, localX, localY)
				: drawDetailWide(context, font, box, localX, localY);
	}

	private List<Component> drawDetailCompact(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		ValueHistory history = history();
		List<Component> tooltip = null;

		int y = tabsY();

		if (drawButton(context, font, MARGIN, y, "<", COLOUR_TEXT, localX, localY)) {
			tooltip = List.of(Component.literal(Lang.get("primeitemvalue.detail.back")));
		}

		ItemStack stack = stacks.get(detail);

		if (!stack.isEmpty()) {
			context.fakeItem(stack, MARGIN + BUTTON + BUTTON_SPACING, y - 3);
		}

		int nameX = MARGIN + BUTTON + BUTTON_SPACING + SLOT;
		int nameRoom = box.width() - MARGIN - nameX;
		context.text(font, trim(font, detail.plainName(), nameRoom), nameX, y + 2, COLOUR_TEXT);

		y = searchY();
		Optional<ItemValue> now = lookup.forEntry(detail);
		boolean rising = history == null || history.change() >= 0;
		int trendColour = rising ? COLOUR_ON : COLOUR_OFF;

		if (now.isPresent()) {
			ItemValue value = now.get();

			context.text(font, ValueFormatter.display(value) + " " + value.currency().label(),
					nameX, y + 2, history == null || history.empty() ? COLOUR_VALUE : trendColour);
		}

		if (history != null && !history.empty()) {
			String change = formatChange(history.change());
			int width = font.width(change);

			if (nameX + width < box.width() - MARGIN) {
				context.text(font, change, box.width() - MARGIN - width, y + 2, trendColour);
			}
		}

		int top = gridY(box);

		if (history != null && history.firstKnown() != null) {
			String from = Lang.get("primeitemvalue.detail.from",
					detailDays, ValueFormatter.shortForm(history.firstKnown()));

			if (font.width(from) <= box.width() - MARGIN * 2) {
				context.text(font, from, MARGIN, top, COLOUR_MUTED);
			}

			top += ROW_HEIGHT;
		}

		int bottom = gridY(box) + box.rows() * SLOT;
		int rangeY = bottom - ROW_HEIGHT;
		int chartBottom = rangeY - MARGIN - ROW_HEIGHT;

		if (history == null || history.empty()) {

			context.text(font, emptyChartText(history), MARGIN, top + MARGIN, COLOUR_MUTED);
		} else {
			List<Component> onColumn = drawChart(context, font, box, history, top, chartBottom,
					localX, localY);

			if (onColumn != null) {
				tooltip = onColumn;
			}
		}

		int x = MARGIN;

		for (int days : RANGES) {
			String label = Lang.get("primeitemvalue.detail.range", days);
			int width = font.width(label) + MARGIN * 2;
			boolean active = detailDays == days;
			boolean hovered = within(localX, localY, x, rangeY, width, ROW_HEIGHT);

			context.fill(x, rangeY, x + width, rangeY + ROW_HEIGHT,
					active || hovered ? COLOUR_TAB_ACTIVE : COLOUR_TAB_IDLE);
			context.text(font, label, x + MARGIN, rangeY + 2,
					active ? COLOUR_ON : COLOUR_TEXT);

			x += width + BUTTON_SPACING;
		}

		if (lookup.provider().placeholder()) {
			String note = Lang.get("primeitemvalue.detail.placeholder");

			if (x + font.width(note) < box.width() - MARGIN) {
				context.text(font, note, box.width() - MARGIN - font.width(note), rangeY + 2,
						COLOUR_MUTED);
			}
		}

		return tooltip;
	}

	private List<Component> drawDetailWide(GuiGraphicsExtractor context, Font font, Layout box,
			int localX, int localY) {
		ValueHistory history = history();
		List<Component> tooltip = null;
		int right = box.width() - MARGIN;

		int y = tabsY();

		if (drawButton(context, font, MARGIN, y, "<", COLOUR_TEXT, localX, localY)) {
			tooltip = List.of(Component.literal(Lang.get("primeitemvalue.detail.back")));
		}

		ItemStack stack = stacks.get(detail);

		if (!stack.isEmpty()) {
			context.fakeItem(stack, MARGIN + BUTTON + BUTTON_SPACING, y - 3);
		}

		int nameX = MARGIN + BUTTON + BUTTON_SPACING + SLOT;
		context.text(font, trim(font, detail.plainName(), right - nameX), nameX, y + 2,
				COLOUR_TEXT);

		String origin = detail.collection() != null ? detail.collection() : "";

		if (advancedTooltips()) {
			origin = origin.isEmpty() ? detail.baseItem() : origin + " · " + detail.baseItem();
		}

		if (!origin.isEmpty()) {
			context.text(font, trim(font, origin, right - nameX), nameX, searchY() + 2,
					COLOUR_MUTED);
		}

		int top = gridY(box);
		int bottom = gridY(box) + box.rows() * SLOT;
		int rangeY = bottom - ROW_HEIGHT;
		int statsY = rangeY - MARGIN - ROW_HEIGHT * 2;
		boolean roomForStats = statsY - top > ROW_HEIGHT * 4;

		int chartBottom = (roomForStats ? statsY - MARGIN : rangeY - MARGIN) - ROW_HEIGHT;

		if (history == null || history.empty()) {
			context.text(font, emptyChartText(history), MARGIN, top + MARGIN, COLOUR_MUTED);
		} else {
			List<Component> onColumn = drawChart(context, font, box, history, top, chartBottom,
					localX, localY);

			if (onColumn != null) {
				tooltip = onColumn;
			}

			String from = Lang.get("primeitemvalue.detail.days_ago", detailDays);
			String to = Lang.get("primeitemvalue.detail.today");

			context.text(font, from, MARGIN, chartBottom + 1, COLOUR_MUTED);
			context.text(font, to, right - font.width(to), chartBottom + 1, COLOUR_MUTED);
		}

		if (roomForStats && history != null && !history.empty()) {
			drawStats(context, font, box, history, statsY);
		}

		int x = MARGIN;

		for (int days : RANGES) {
			String label = Lang.get("primeitemvalue.detail.range", days);
			int width = font.width(label) + MARGIN * 2;
			boolean active = detailDays == days;
			boolean hovered = within(localX, localY, x, rangeY, width, ROW_HEIGHT);

			context.fill(x, rangeY, x + width, rangeY + ROW_HEIGHT,
					active || hovered ? COLOUR_TAB_ACTIVE : COLOUR_TAB_IDLE);
			context.text(font, label, x + MARGIN, rangeY + 2, active ? COLOUR_ON : COLOUR_TEXT);

			x += width + BUTTON_SPACING;
		}

		String source = lookup.provider().sourceLabel();

		if (x + font.width(source) < right) {
			context.text(font, source, right - font.width(source), rangeY + 2, COLOUR_MUTED);
		}

		return tooltip;
	}

	private void drawStats(GuiGraphicsExtractor context, Font font, Layout box,
			ValueHistory history, int y) {
		Optional<ItemValue> now = lookup.forEntry(detail);
		int trendColour = history.change() >= 0 ? COLOUR_ON : COLOUR_OFF;

		List<String> labels = List.of(
				Lang.get("primeitemvalue.detail.now"),
				Lang.get("primeitemvalue.detail.low"),
				Lang.get("primeitemvalue.detail.high"),
				Lang.get("primeitemvalue.detail.trades_total"),
				Lang.get("primeitemvalue.detail.change"));

		List<String> values = List.of(
				now.map(value -> ValueFormatter.shortForm(value.amount())).orElse("—"),
				ValueFormatter.shortForm(history.low()),
				ValueFormatter.shortForm(history.high()),
				String.valueOf(history.trades()),
				formatChange(history.change()));

		List<Integer> colours = List.of(COLOUR_VALUE, COLOUR_TEXT, COLOUR_TEXT, COLOUR_MUTED,
				trendColour);

		int[] widths = new int[labels.size()];

		for (int i = 0; i < labels.size(); i++) {
			widths[i] = Math.max(font.width(labels.get(i)), font.width(values.get(i)));
		}

		int usable = box.width() - MARGIN * 2;
		boolean[] keep = new boolean[labels.size()];
		int used = 0;
		int kept = 0;

		for (int index : new int[] { 0, 4, 1, 2, 3 }) {
			int extra = widths[index] + (kept > 0 ? MARGIN * 2 : 0);

			if (used + extra > usable) {
				continue;
			}

			keep[index] = true;
			used += extra;
			kept++;
		}

		if (kept == 0) {
			return;
		}

		context.fill(MARGIN, y - MARGIN / 2, box.width() - MARGIN, y - MARGIN / 2 + 1,
				COLOUR_BORDER);

		int slack = kept > 1 ? (usable - used) / (kept - 1) : 0;
		int x = MARGIN;

		for (int i = 0; i < labels.size(); i++) {
			if (!keep[i]) {
				continue;
			}

			context.text(font, labels.get(i), x, y, COLOUR_MUTED);
			context.text(font, values.get(i), x, y + ROW_HEIGHT, colours.get(i));

			x += widths[i] + MARGIN * 2 + slack;
		}
	}

	private List<Component> drawChart(GuiGraphicsExtractor context, Font font, Layout box,
			ValueHistory history, int top, int bottom, int localX, int localY) {
		List<ValuePoint> points = history.points();
		int left = MARGIN;
		int right = box.width() - MARGIN;
		int height = bottom - top - ROW_HEIGHT;

		if (height < 8 || points.isEmpty()) {
			return null;
		}

		long span = Math.max(1, history.high() - history.low());
		int columns = points.size();
		int fullWidth = right - left;
		List<Component> tooltip = null;

		context.fill(left, bottom - 1, right, bottom, COLOUR_BORDER);

		for (int i = 0; i < columns; i++) {
			ValuePoint point = points.get(i);

			int x = left + i * fullWidth / columns;
			int next = left + (i + 1) * fullWidth / columns;
			int width = Math.max(1, next - x - (columns <= 30 ? 1 : 0));

			boolean hovered = within(localX, localY, x, top, Math.max(2, next - x),
					bottom - top);

			if (hovered) {
				context.fill(x, top, x + Math.max(2, next - x), bottom - 1, COLOUR_COLUMN_HOVER);
				tooltip = columnTooltip(point, columns - 1 - i);
			}

			if (!point.known()) {

				context.fill(x, bottom - 2, x + width, bottom - 1, COLOUR_GAP);
				continue;
			}

			int columnHeight = history.high() == history.low()
					? height / 2
					: (int) ((point.value() - history.low()) * (long) height / span);

			columnHeight = Math.max(1, columnHeight);

			int colour = point.sampleSize() >= 10 ? COLOUR_CHART : COLOUR_CHART_WEAK;

			context.fill(x, bottom - 1 - columnHeight, x + width, bottom - 1, colour);
		}

		String high = ValueFormatter.shortForm(history.high());
		String low = ValueFormatter.shortForm(history.low());

		context.text(font, high, left, top, COLOUR_TEXT);
		context.text(font, low, left, bottom - ROW_HEIGHT + 2, COLOUR_TEXT);

		return tooltip;
	}

	private List<Component> columnTooltip(ValuePoint point, int daysAgo) {
		Component when = daysAgo == 0
				? Component.literal(Lang.get("primeitemvalue.detail.today"))
				: Component.literal(Lang.get("primeitemvalue.detail.days_ago", daysAgo));

		if (!point.known()) {
			return List.of(when, grey("primeitemvalue.detail.no_trades"));
		}

		return List.of(
				when,
				Component.literal("§f" + ValueFormatter.shortForm(point.value())
						+ " §7" + Currency.COINS.label()),
				Component.literal("§8" + Lang.get("primeitemvalue.detail.trades",
						point.sampleSize())));
	}

	private String emptyChartText(ValueHistory history) {
		if (history != null) {
			return Lang.get("primeitemvalue.detail.no_trades_in_range", detailDays);
		}

		long throttled = lookup.provider().throttledSeconds();

		if (throttled > 0) {
			return Lang.get("primeitemvalue.detail.throttled", throttled);
		}

		if (lookup.provider().historyLoading(detail.id(), detailDays)) {
			return Lang.get("primeitemvalue.detail.loading");
		}

		return Lang.get("primeitemvalue.detail.unavailable");
	}

	private ValueHistory history() {
		if (detail == null) {
			return null;
		}

		if (detailHistory == null || detailStale) {
			detailHistory = lookup.historyFor(detail, detailDays).orElse(null);
			detailStale = false;
		}

		return detailHistory;
	}

	private static String formatChange(double change) {
		String sign = change >= 0 ? "+" : "-";
		double percent = Math.abs(change) * 100;

		return sign + String.format(java.util.Locale.ROOT, "%.1f", percent) + "%";
	}

	private void openDetail(CatalogEntry entry) {
		detail = entry;
		detailStale = true;
		setSearchFocused(false);
	}

	private boolean clickDetail(Layout box, Font font, int localX, int localY) {
		if (within(localX, localY, MARGIN, tabsY(), BUTTON, ROW_HEIGHT)) {
			detail = null;
			detailHistory = null;
			return true;
		}

		int bottom = gridY(box) + box.rows() * SLOT;
		int rangeY = bottom - ROW_HEIGHT;
		int x = MARGIN;

		for (int days : RANGES) {
			int width = font.width(Lang.get("primeitemvalue.detail.range", days)) + MARGIN * 2;

			if (within(localX, localY, x, rangeY, width, ROW_HEIGHT)) {
				if (detailDays != days) {
					detailDays = days;
					detailStale = true;
				}

				return true;
			}

			x += width + BUTTON_SPACING;
		}

		return true;
	}

	private CatalogEntry entryAt(Layout box, int localX, int localY) {
		int gridY = gridY(box);
		int first = scrollRow * box.columns();

		for (int slot = 0; slot < box.columns() * box.rows(); slot++) {
			int index = first + slot;

			if (index >= results.size()) {
				return null;
			}

			int x = MARGIN + (slot % box.columns()) * SLOT;
			int y = gridY + (slot / box.columns()) * SLOT;

			if (within(localX, localY, x, y, SLOT, SLOT)) {
				return results.get(index);
			}
		}

		return null;
	}

	private static boolean advancedTooltips() {
		Minecraft minecraft = Minecraft.getInstance();

		return minecraft != null && minecraft.options != null
				&& minecraft.options.advancedItemTooltips;
	}

	private List<Component> tooltipFor(CatalogEntry entry) {
		List<Component> lines = new ArrayList<>(5);
		lines.add(Component.literal(entry.displayName()));

		if (entry.collection() != null && !entry.collection().isBlank()) {
			lines.add(Component.literal("§7" + entry.collection()));
		}

		if (advancedTooltips()) {
			lines.add(Component.literal("§8" + entry.baseItem()));
		}

		Optional<ItemValue> value = lookup.forEntry(entry);

		if (value.isPresent()) {
			ItemValue item = value.get();
			lines.add(Component.literal("§f" + ValueFormatter.display(item)
					+ " §7" + item.currency().label()
					+ " §8(" + ValueFormatter.longForm(item.amount()) + ")"));
		} else if (!entry.tradeable()) {
			lines.add(Component.literal("§8").append(
					Component.literal(Lang.get("primeitemvalue.entry.untradeable"))));
		} else {
			lines.add(Component.literal("§8").append(
					Component.literal(Lang.get("primeitemvalue.entry.no_value"))));
		}

		if (!entry.obtainable()) {
			lines.add(Component.literal("§8").append(
					Component.literal(Lang.get("primeitemvalue.entry.unobtainable"))));
		}

		lines.add(grey("primeitemvalue.detail.open"));

		return lines;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (config.panelCollapsed) {
			int[] handle = collapsedBox;

			if (handle != null && mouseX >= handle[0] && mouseX < handle[0] + handle[2]
					&& mouseY >= handle[1] && mouseY < handle[1] + handle[3]) {
				config.panelCollapsed = false;
				config.markDirty();
				return true;
			}

			return false;
		}

		Layout box = layout;

		if (box == null || !config.panelEnabled) {
			return false;
		}

		if (!box.containsScreen(mouseX, mouseY)) {
			setSearchFocused(false);
			menuOpen = false;
			return false;
		}

		int localX = (int) box.toLocalX(mouseX);
		int localY = (int) box.toLocalY(mouseY);
		Font font = Minecraft.getInstance().font;

		if (menuOpen && clickMenu(box, font, localX, localY, button)) {
			return true;
		}

		if (within(localX, localY, gripX(box) - (GRIP_HIT - GRIP), gripY(box) - (GRIP_HIT - GRIP),
				GRIP_HIT, GRIP_HIT)) {
			menuOpen = false;
			return startResize(box);
		}

		if (within(localX, localY, menuX(box), titleY(), BUTTON, ROW_HEIGHT)) {
			menuOpen = !menuOpen;
			return true;
		}

		if (within(localX, localY, collapseX(box), titleY(), BUTTON, ROW_HEIGHT)) {
			config.panelCollapsed = true;
			menuOpen = false;
			setSearchFocused(false);
			config.markDirty();
			return true;
		}

		menuOpen = false;

		if (within(localX, localY, MARGIN, titleY(), box.width() - MARGIN * 2, ROW_HEIGHT)) {
			return startMove(box, mouseX, mouseY, button);
		}

		if (detail != null) {
			return clickDetail(box, font, localX, localY);
		}

		int tabWidth = (box.width() - MARGIN * 2) / 2;

		for (ItemCategory candidate : ItemCategory.values()) {
			int x = MARGIN + (candidate == ItemCategory.CUSTOM ? 0 : tabWidth);

			if (within(localX, localY, x, tabsY(), tabWidth, ROW_HEIGHT)) {
				if (category != candidate) {
					category = candidate;
					scrollRow = 0;
					searchDueAt = 0L;
					resultsStale = true;
				}

				return true;
			}
		}

		if (within(localX, localY, MARGIN, searchY(), box.width() - MARGIN * 2, ROW_HEIGHT)) {
			setSearchFocused(true);
			return true;
		}

		CatalogEntry clicked = entryAt(box, localX, localY);

		if (clicked != null) {
			openDetail(clicked);
			return true;
		}

		setSearchFocused(false);
		return true;
	}

	private boolean startMove(Layout box, double mouseX, double mouseY, int button) {

		if (button == 1) {
			config.panelX = null;
			config.panelY = null;
			config.panelColumns = 0;
			config.panelRows = 0;
			config.panelScale = 1.0;
			config.panelAutoSize = true;
			config.markDirty();
			drag = Drag.NONE;
			return true;
		}

		pinCurrentGeometry(box);
		dragOffsetX = mouseX - box.originX();
		dragOffsetY = mouseY - box.originY();
		drag = Drag.MOVE;

		return true;
	}

	private boolean startResize(Layout box) {

		config.panelAutoSize = false;
		pinCurrentGeometry(box);
		drag = Drag.RESIZE;

		return true;
	}

	private void pinCurrentGeometry(Layout box) {
		config.panelX = box.originX();
		config.panelY = box.originY();
		config.panelColumns = box.columns();
		config.panelRows = box.rows();
		config.markDirty();
	}

	public boolean mouseDragged(double mouseX, double mouseY) {
		Layout box = layout;

		if (drag == Drag.NONE || box == null || !config.panelEnabled) {
			return false;
		}

		if (drag == Drag.MOVE) {
			config.panelX = (int) Math.round(mouseX - dragOffsetX);
			config.panelY = (int) Math.round(mouseY - dragOffsetY);
			config.markDirty();
			return true;
		}

		double localX = box.toLocalX(mouseX);
		double localY = box.toLocalY(mouseY);

		int horizontalChrome = box.width() - box.columns() * SLOT;
		int verticalChrome = box.height() - box.rows() * SLOT;

		int columns = (int) Math.round((localX - horizontalChrome + GRIP + 1) / (double) SLOT);
		int rows = (int) Math.round((localY - verticalChrome + GRIP + 1) / (double) SLOT);

		config.panelColumns = Math.clamp(columns,
				ItemValueConfig.MIN_COLUMNS, ItemValueConfig.MAX_COLUMNS);
		config.panelRows = Math.clamp(rows, ItemValueConfig.MIN_ROWS, ItemValueConfig.MAX_ROWS);
		config.markDirty();

		return true;
	}

	public boolean mouseReleased() {
		if (drag == Drag.NONE) {
			return false;
		}

		drag = Drag.NONE;
		config.saveIfDirty();

		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		Layout box = layout;

		if (box == null || !config.panelEnabled || !box.containsScreen(mouseX, mouseY)) {
			return false;
		}

		scrollRow -= (int) Math.signum(amount);
		clampScroll(box);
		return true;
	}

	// Use SDL shortcutKey codes here; key() contains USB HID codes.
	public boolean keyPressed(KeyEvent event) {
		if (!config.panelEnabled || layout == null) {
			return false;
		}

		int shortcut = event.shortcutKey();

		if (!searchFocused) {

			if (event.isCycleFocus()) {
				setSearchFocused(true);
				return true;
			}

			if (isTypingKey(shortcut) && mouseOverPanel()) {
				setSearchFocused(true);
				append((char) shortcut);
				return true;
			}

			return false;
		}

		if (event.isEscape() || event.isConfirmation()) {
			setSearchFocused(false);
			return true;
		}

		if (shortcut == SDL_BACKSPACE) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				scrollRow = 0;
				scheduleSearch();
			}

			return true;
		}

		if (isTypingKey(shortcut)) {
			append((char) shortcut);
			return true;
		}

		return true;
	}

	private void append(char typed) {
		if (query.length() >= MAX_QUERY) {
			return;
		}

		query += typed;
		scrollRow = 0;
		scheduleSearch();

		if (config.debugSearch) {
			PrimeItemValueClient.LOGGER.info("[Eingabe] Zeichen '{}' angekommen,"
					+ " Anfrage jetzt \"{}\"", typed, query);
		}
	}

	// ASCII is already handled by keyPressed; accept other characters here.
	public boolean charTyped(char typed) {
		if (!config.panelEnabled || isTypingKey(typed)) {
			return false;
		}

		if (!searchFocused && mouseOverPanel()) {
			setSearchFocused(true);
		}

		if (!searchFocused) {
			return false;
		}

		if (typed < ' ' || typed == 127 || query.length() >= MAX_QUERY) {
			return true;
		}

		query += typed;
		scrollRow = 0;
		scheduleSearch();

		if (config.debugSearch) {
			PrimeItemValueClient.LOGGER.info("Zeichen '{}' angekommen, Anfrage jetzt \"{}\"",
					typed, query);
		}

		return true;
	}

	public void onScreenClosed() {
		detail = null;
		detailHistory = null;
		menuOpen = false;
		setSearchFocused(false);
		drag = Drag.NONE;
		config.saveIfDirty();
	}

	public int visibleSlots() {
		return visibleSlots;
	}

	private void scheduleSearch() {
		searchDueAt = System.currentTimeMillis() + SEARCH_DELAY_MILLIS;
	}

	private void refreshResults() {
		if (searchDueAt != 0L && System.currentTimeMillis() >= searchDueAt) {
			searchDueAt = 0L;
			resultsStale = true;
		}

		if (!resultsStale) {
			return;
		}

		long started = System.nanoTime();
		results = lookup.catalog().search(query, category);
		resultsStale = false;

		long micros = (System.nanoTime() - started) / 1000L;

		if (micros >= SLOW_SEARCH_MICROS && !warnedSlowSearch) {
			warnedSlowSearch = true;
			PrimeItemValueClient.LOGGER.warn(
					"Suche hat {} µs gebraucht (Reiter {}, Anfrage \"{}\", {} Treffer)"
							+ " — das ist deutlich zu lang, bitte melden",
					micros, category, query, results.size());
		} else if (config.debugSearch) {
			PrimeItemValueClient.LOGGER.info(
					"Suche: Reiter {}, Anfrage \"{}\", {} Treffer, {} µs",
					category, query, results.size(), micros);
		}
	}

	private static boolean isTypingKey(int shortcutKey) {
		return shortcutKey >= ' ' && shortcutKey <= '~';
	}

	private boolean mouseOverPanel() {
		Layout box = layout;

		if (box == null || Double.isNaN(lastMouseX)) {
			return false;
		}

		return box.containsScreen(lastMouseX, lastMouseY);
	}

	private void setSearchFocused(boolean focused) {
		if (searchFocused == focused) {
			return;
		}

		searchFocused = focused;

		if (config.debugSearch) {
			PrimeItemValueClient.LOGGER.info("Suchfeld {}",
					focused ? "fokussiert" : "nicht mehr fokussiert");
		}
	}

	private void clampScroll(Layout box) {
		int perPage = box.columns() * box.rows();
		int maxRow = results.size() <= perPage
				? 0
				: Math.max(0, (results.size() - 1) / box.columns() - box.rows() + 1);

		scrollRow = Math.clamp(scrollRow, 0, maxRow);
	}

	private static boolean within(int x, int y, int boxX, int boxY, int width, int height) {
		return x >= boxX && x < boxX + width && y >= boxY && y < boxY + height;
	}

	private record Layout(int originX, int originY, float scale,
			int width, int height, int columns, int rows, int headerHeight) {

		double toLocalX(double screenX) {
			return (screenX - originX) / scale;
		}

		double toLocalY(double screenY) {
			return (screenY - originY) / scale;
		}

		boolean containsScreen(double screenX, double screenY) {
			double localX = toLocalX(screenX);
			double localY = toLocalY(screenY);

			return localX >= 0 && localX < width && localY >= 0 && localY < height;
		}
	}

}
