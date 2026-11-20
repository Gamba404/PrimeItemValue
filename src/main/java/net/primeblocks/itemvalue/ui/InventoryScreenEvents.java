package net.primeblocks.itemvalue.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import net.primeblocks.itemvalue.mixin.ContainerGeometryAccessor;

/** Registers panel drawing and mouse input for inventory screens. */
public final class InventoryScreenEvents {

	private InventoryScreenEvents() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}

			// Draw after inventory contents but before tooltips.
			ScreenEvents.afterForeground(screen).register((drawn, context, mouseX, mouseY, tick) ->
					PanelEvents.draw(drawn, context, containerRight(container), mouseX, mouseY));

			// Keyboard input is handled separately by KeyboardHandlerMixin.
			ScreenMouseEvents.allowMouseClick(screen).register(
					(target, event) -> !PanelEvents.handleClick(event));

			ScreenMouseEvents.allowMouseDrag(screen).register(
					(target, event, dragX, dragY) -> !PanelEvents.handleDrag(event));

			ScreenMouseEvents.allowMouseRelease(screen).register(
					(target, event) -> !PanelEvents.handleRelease());

			ScreenMouseEvents.allowMouseScroll(screen).register(
					(target, mouseX, mouseY, scrollX, scrollY) ->
							!PanelEvents.handleScroll(mouseX, mouseY, scrollY));

			ScreenEvents.remove(screen).register(closed -> PanelEvents.closed());
		});
	}

	private static int containerRight(AbstractContainerScreen<?> container) {
		ContainerGeometryAccessor geometry = (ContainerGeometryAccessor) container;

		return geometry.primeitemvalue$leftPos() + geometry.primeitemvalue$imageWidth();
	}
}
