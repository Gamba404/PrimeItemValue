package net.primeblocks.itemvalue.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

@Mixin(PlayerTabOverlay.class)
/** Exposes the tab header and footer for CityBuild detection. */
public interface PlayerTabOverlayAccessor {

	@Accessor("header")
	Component primeitemvalue$getHeader();

	@Accessor("footer")
	Component primeitemvalue$getFooter();
}
