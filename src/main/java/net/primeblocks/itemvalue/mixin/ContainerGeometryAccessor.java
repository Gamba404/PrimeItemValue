package net.primeblocks.itemvalue.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)

/** Exposes the inventory position and width for panel placement. */
public interface ContainerGeometryAccessor {

	@Accessor("leftPos")
	int primeitemvalue$leftPos();

	@Accessor("imageWidth")
	int primeitemvalue$imageWidth();
}
