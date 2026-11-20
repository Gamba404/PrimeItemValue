package net.primeblocks.itemvalue.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.ServerDetector;

/** Routes screen events to the panel when the mod is active. */
public final class PanelEvents {

	private PanelEvents() {
	}

	public static ItemPanel active() {
		ItemPanel panel = PrimeItemValueClient.panel();
		ItemValueConfig config = PrimeItemValueClient.config();

		if (panel == null || config == null) {
			return null;
		}

		return ServerDetector.active(Minecraft.getInstance(), config) ? panel : null;
	}

	public static boolean handleCharacter(CharacterEvent event) {
		ItemPanel panel = active();

		if (panel == null) {
			return false;
		}

		String typed = event.codepointAsString();
		return !typed.isEmpty() && panel.charTyped(typed.charAt(0));
	}

	public static boolean handleKey(KeyEvent event) {
		ItemPanel panel = active();

		return panel != null && panel.keyPressed(event);
	}

	public static boolean handleClick(MouseButtonEvent event) {
		ItemPanel panel = active();

		return panel != null && panel.mouseClicked(event.x(), event.y(), event.button());
	}

	public static boolean handleDrag(MouseButtonEvent event) {
		ItemPanel panel = active();

		return panel != null && panel.mouseDragged(event.x(), event.y());
	}

	public static boolean handleRelease() {
		ItemPanel panel = active();

		return panel != null && panel.mouseReleased();
	}

	public static boolean handleScroll(double mouseX, double mouseY, double amount) {
		ItemPanel panel = active();

		return panel != null && panel.mouseScrolled(mouseX, mouseY, amount);
	}

	public static void closed() {
		ItemPanel panel = active();

		if (panel != null) {
			panel.onScreenClosed();
		}
	}

	public static void draw(Screen screen, GuiGraphicsExtractor context, int containerRight,
			int mouseX, int mouseY) {
		ItemPanel panel = active();

		if (panel == null) {
			return;
		}

		ItemValueConfig config = PrimeItemValueClient.config();
		long started = System.nanoTime();

		panel.draw(context, Minecraft.getInstance().font,
				screen.width, screen.height, containerRight, mouseX, mouseY);

		record(System.nanoTime() - started, panel, config.debugTiming);
	}

	private static final long REPORT_INTERVAL_NANOS = 5_000_000_000L;

	private static final double WARN_BELOW_FPS = 45.0;

	private static final int LIMIT_TOLERANCE_FPS = 5;

	private static long accumulated;
	private static int frames;
	private static int lastSlots;
	private static long nextReport;
	private static boolean warned;

	private static void record(long nanos, ItemPanel panel, boolean verbose) {
		accumulated += nanos;
		frames++;
		lastSlots = panel.visibleSlots();

		long now = System.nanoTime();

		if (nextReport == 0L) {
			nextReport = now + REPORT_INTERVAL_NANOS;
			return;
		}

		if (now < nextReport) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		int fps = minecraft.getFps();
		int limit = minecraft.options.framerateLimit().get();
		double extractMillis = accumulated / (double) frames / 1_000_000.0;

		boolean slow = fps < WARN_BELOW_FPS && fps < limit - LIMIT_TOLERANCE_FPS;

		if (verbose || (slow && !warned)) {
			String line = "Panel offen: {} fps (Limit {}), {} Slots sichtbar,"
					+ " davon {} ms pro Frame fürs Sammeln der Zeichenbefehle";
			String extractText = String.format(java.util.Locale.GERMANY, "%.2f", extractMillis);

			if (slow) {
				PrimeItemValueClient.LOGGER.warn(line + " — Panel kleiner ziehen hilft",
						fps, limit, lastSlots, extractText);

				warned = true;
			} else {
				PrimeItemValueClient.LOGGER.info(line, fps, limit, lastSlots, extractText);
			}
		}

		accumulated = 0L;
		frames = 0;
		nextReport = now + REPORT_INTERVAL_NANOS;
	}
}
