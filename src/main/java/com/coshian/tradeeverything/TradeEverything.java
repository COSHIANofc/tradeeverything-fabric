package com.coshian.tradeeverything;

import com.coshian.tradeeverything.catalog.TradeCatalog;
import com.coshian.tradeeverything.command.TradeEverythingCommands;
import com.coshian.tradeeverything.entity.ClerkManager;
import com.coshian.tradeeverything.menu.TradeEverythingMenu;
import com.coshian.tradeeverything.network.TradeNetworking;
import com.coshian.tradeeverything.price.PriceConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TradeEverything implements ModInitializer {
	public static final String MOD_ID = "tradeeverything";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

	@Override public void onInitialize() {
		TradeEverythingMenu.register();
		TradeNetworking.registerCommon();
		TradeEverythingCommands.register();
		ClerkManager.registerEvents();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			PriceConfig.load();
			TradeCatalog.rebuild(server.registryAccess());
			LOGGER.info("Searchable trade catalog ready: {} enabled entries for Swamp Hut merchants", TradeCatalog.enabledEntries().size());
		});
	}
}
