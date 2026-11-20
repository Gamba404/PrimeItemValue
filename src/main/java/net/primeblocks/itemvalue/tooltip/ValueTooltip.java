package net.primeblocks.itemvalue.tooltip;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.primeblocks.itemvalue.Lang;
import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.ServerDetector;
import net.primeblocks.itemvalue.value.ItemValue;
import net.primeblocks.itemvalue.value.ValueFormatter;
import net.primeblocks.itemvalue.value.ValueLookup;

/** Adds unit and stack prices to item tooltips. */
public final class ValueTooltip {

	private ValueTooltip() {
	}

	public static void register(ValueLookup lookup, ItemValueConfig config) {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				append(lookup, config, stack, lines));
	}

	private static void append(ValueLookup lookup, ItemValueConfig config,
			ItemStack stack, List<Component> lines) {
		if (!config.tooltipEnabled || stack == null || stack.isEmpty()) {
			return;
		}

		if (!ServerDetector.active(Minecraft.getInstance(), config)) {
			return;
		}

		Optional<ItemValue> found = lookup.forStack(stack);

		if (found.isEmpty()) {
			return;
		}

		ItemValue value = found.get();
		StringBuilder text = new StringBuilder(48);

		text.append("§7").append(Lang.get("primeitemvalue.tooltip.value")).append("§f")
				.append(ValueFormatter.display(value))
				.append(" §7")
				.append(value.currency().label());

		int count = stack.getCount();

		if (config.tooltipStackTotal && count > 1) {

			text.append(" §8(x").append(count).append(" = ")
					.append(ValueFormatter.display(value.times(count)))
					.append(')');
		}

		lines.add(Component.literal(text.toString()));

		if (lookup.provider().placeholder()) {
			lines.add(Component.literal("§8").append(
					Component.literal(Lang.get("primeitemvalue.tooltip.placeholder"))));
		}
	}
}
