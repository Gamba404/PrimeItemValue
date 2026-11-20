package net.primeblocks.itemvalue.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.ui.PanelEvents;

@Mixin(KeyboardHandler.class)
/** Routes keyboard input to the panel before the inventory handles it. */
public abstract class KeyboardHandlerMixin {

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void primeitemvalue$charTyped(long window, CharacterEvent event, CallbackInfo ci) {
		if (PrimeItemValueClient.config() != null && PrimeItemValueClient.config().debugSearch) {
			PrimeItemValueClient.LOGGER.info("[Eingabe] charTyped im Mixin: '{}', Screen {}",
					event.codepointAsString(), primeitemvalue$screenName());
		}

		if (primeitemvalue$onContainerScreen() && PanelEvents.handleCharacter(event)) {
			ci.cancel();
		}
	}

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void primeitemvalue$keyPress(long window, int action, KeyEvent event,
			CallbackInfo ci) {
		if (action == 0) {
			return;
		}

		if (PrimeItemValueClient.config() != null && PrimeItemValueClient.config().debugSearch) {
			PrimeItemValueClient.LOGGER.info("[Eingabe] keyPress im Mixin: HID {}, SDL {}, Screen {}",
					event.key(), event.shortcutKey(), primeitemvalue$screenName());
		}

		if (primeitemvalue$onContainerScreen() && PanelEvents.handleKey(event)) {
			ci.cancel();
		}
	}

	private static String primeitemvalue$screenName() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui == null || minecraft.gui.screen() == null) {
			return "(keiner)";
		}

		return minecraft.gui.screen().getClass().getSimpleName();
	}

	private static boolean primeitemvalue$onContainerScreen() {
		Minecraft minecraft = Minecraft.getInstance();

		return minecraft.gui != null
				&& minecraft.gui.screen() instanceof AbstractContainerScreen<?>;
	}
}
