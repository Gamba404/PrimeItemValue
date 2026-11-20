package net.primeblocks.itemvalue.catalog;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Resolves a stack to its custom item ID or vanilla registry ID. */
public final class ItemIdentity {

	private static final Pattern NAME_ID = Pattern.compile("#(\\d{1,9})(?:§[0-9a-fk-or])*\\s*$");

	private ItemIdentity() {
	}

	public static String of(ItemStack stack, String customDataKey) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		// Prefer custom data, then the name suffix, then model data.
		String fromData = fromCustomData(stack, customDataKey);

		if (fromData != null) {
			return fromData;
		}

		String fromName = fromDisplayName(stack);

		if (fromName != null) {
			return fromName;
		}

		String fromModel = fromCustomModelData(stack);

		if (fromModel != null) {
			return fromModel;
		}

		return registryId(stack);
	}

	public static String registryId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String fromCustomData(ItemStack stack, String customDataKey) {
		if (customDataKey == null || customDataKey.isBlank()) {
			return null;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);

		if (data == null || data.isEmpty()) {
			return null;
		}

		CompoundTag tag = data.copyTag();

		String asString = tag.getString(customDataKey).orElse(null);

		if (asString != null && !asString.isBlank()) {
			return asString;
		}

		Integer asInt = tag.getInt(customDataKey).orElse(null);
		return asInt != null ? asInt.toString() : null;
	}

	private static String fromDisplayName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);

		if (name == null) {
			return null;
		}

		Matcher matcher = NAME_ID.matcher(name.getString());
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String fromCustomModelData(ItemStack stack) {
		CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);

		if (model == null) {
			return null;
		}

		// Modern model data uses lists; legacy numeric IDs are stored in the first float.
		List<Float> floats = model.floats();

		if (!floats.isEmpty() && floats.get(0) != null) {
			float value = floats.get(0);

			if (value == Math.rint(value)) {
				return Long.toString((long) value);
			}

			return Float.toString(value);
		}

		List<String> strings = model.strings();

		if (!strings.isEmpty() && strings.get(0) != null && !strings.get(0).isBlank()) {
			return strings.get(0);
		}

		return null;
	}

	public static ItemStack toStack(CatalogEntry entry) {
		Identifier id = Identifier.tryParse(entry.baseItem());

		if (id == null) {
			return ItemStack.EMPTY;
		}

		var item = BuiltInRegistries.ITEM.getValue(id);

		if (item == null) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = new ItemStack(item);

		if (entry.customModelData() != null) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
					List.of((float) entry.customModelData()),
					List.of(),
					List.of(Integer.toString(entry.customModelData())),
					List.of()));
		}

		return stack;
	}
}
