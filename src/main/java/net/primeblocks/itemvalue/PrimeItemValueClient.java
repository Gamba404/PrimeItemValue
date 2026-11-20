package net.primeblocks.itemvalue;

import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import net.primeblocks.itemvalue.catalog.CatalogManager;
import net.primeblocks.itemvalue.market.MarketSync;
import net.primeblocks.itemvalue.tooltip.ValueTooltip;
import net.primeblocks.itemvalue.ui.ItemPanel;
import net.primeblocks.itemvalue.ui.InventoryScreenEvents;
import net.primeblocks.itemvalue.value.ValueLookup;

/** Registers the client features and loads the catalog after joining a world. */
public final class PrimeItemValueClient implements ClientModInitializer {
	public static final String MOD_ID = "primeitemvalue";
	public static final Logger LOGGER = LoggerFactory.getLogger("PrimeItemValue");

	private static ItemValueConfig config;
	private static MarketSync market;
	private static ValueLookup lookup;
	private static ItemPanel panel;

	@Override
	public void onInitializeClient() {
		config = ItemValueConfig.load();

		CatalogManager catalogManager = new CatalogManager();
		lookup = new ValueLookup(catalogManager, config);
		panel = new ItemPanel(lookup, config);

		market = new MarketSync(config, lookup, catalogManager, modVersion());

		ValueTooltip.register(lookup, config);
		InventoryScreenEvents.register();
		registerPanelToggle();
		loadCatalogOnJoin(catalogManager);

		LOGGER.info("PrimeItemValue bereit; Katalog wird beim Betreten der Welt aufgebaut");
	}

	// Item components are not bound during client initialization.
	private static void loadCatalogOnJoin(CatalogManager catalogManager) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ServerDetector.active(client, config)) {
				catalogManager.catalog();
				market.resync();
			}
		});
	}

	private static void registerPanelToggle() {

		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.primeitemvalue.toggle_panel", InputConstants.UNKNOWN.getValue(),
				KeyMapping.Category.INVENTORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ServerDetector.active(client, config)) {
				market.tick();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = false;

			// Drain queued presses so they cannot trigger again later.
			while (toggle.consumeClick()) {
				pressed = true;
			}

			if (!pressed || config == null) {
				return;
			}

			config.panelEnabled = !config.panelEnabled;
			config.save();

			if (client.player != null) {
				client.player.sendSystemMessage(Component.translatable(config.panelEnabled
						? "primeitemvalue.chat.panel.on"
						: "primeitemvalue.chat.panel.off"));
			}
		});
	}

	private static String modVersion() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	public static MarketSync market() {
		return market;
	}

	public static ItemValueConfig config() {
		return config;
	}

	public static ItemPanel panel() {
		return panel;
	}
}
