package com.coshian.tradeeverything;

import com.coshian.tradeeverything.catalog.SurvivalEligibility;
import com.coshian.tradeeverything.advancement.AllItemsProgression;
import com.coshian.tradeeverything.advancement.TradeProgressData;
import com.coshian.tradeeverything.advancement.TradeAdvancements;
import com.google.gson.JsonParser;
import com.coshian.tradeeverything.catalog.TradeCatalog;
import com.coshian.tradeeverything.catalog.TradeVariantKind;
import com.coshian.tradeeverything.catalog.TradeVariantSelection;
import com.coshian.tradeeverything.command.TradeEverythingCommands;
import com.coshian.tradeeverything.entity.ClerkManager;
import com.coshian.tradeeverything.menu.TradeEverythingMenu;
import com.coshian.tradeeverything.network.TradeNetworking;
import com.coshian.tradeeverything.network.TradePayloads.SellRequest;
import com.coshian.tradeeverything.price.PriceConfig;
import com.coshian.tradeeverything.trade.TradeTransactionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TradeEverythingGameTest {
	@GameTest
	public void allItemsGeneratedUniverseMatchesBootstrappedRegistry(GameTestHelper helper) {
		try (var stream = TradeEverythingGameTest.class.getResourceAsStream("/data/tradeeverything/advancement/all_items.json")) {
			helper.assertTrue(stream != null, "Generated all_items advancement must be on the runtime resource path");
			var json = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
			var generated = json.getAsJsonObject("criteria").keySet();
			var runtime = AllItemsProgression.requiredKeys().stream().map(AllItemsProgression::criterion).collect(java.util.stream.Collectors.toSet());
			helper.assertTrue(runtime.equals(generated) && runtime.size() == 1411, "Bootstrapped registry canonical universe must equal generated criteria");
			helper.assertTrue(json.getAsJsonArray("requirements").size() == runtime.size() && generated.contains("special__enchanted_book") && generated.contains("special__potion"), "All canonical criteria must be AND requirements with one book and potion unit");
			helper.succeed();
		} catch (Exception exception) { throw new RuntimeException(exception); }
	}

	@GameTest
	public void allItemsAdvancementProgressUsesVanillaRequirements(GameTestHelper helper) {
		var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
		var holder = helper.getLevel().getServer().getAdvancements().get(TradeEverything.id("all_items"));
		helper.assertTrue(holder != null, "Generated all-items advancement must load");
		var progress = player.getAdvancements().getOrStartProgress(holder);
		helper.assertTrue(count(progress.getCompletedCriteria()) == 0 && count(progress.getRemainingCriteria()) == 1411 && !progress.isDone(), "Fresh progress must be 0/1411");
		for (String criterion : java.util.List.of("item__minecraft__diamond", "special__enchanted_book", "special__potion")) player.getAdvancements().award(holder, criterion);
		progress = player.getAdvancements().getOrStartProgress(holder);
		helper.assertTrue(count(progress.getCompletedCriteria()) == 3 && count(progress.getRemainingCriteria()) == 1408 && !progress.isDone() && Math.abs(progress.getPercent() - 3.0F / 1411.0F) < 0.00001F, "Partial progress must expose vanilla 3/1411 progress data");
		for (String criterion : holder.value().criteria().keySet()) player.getAdvancements().award(holder, criterion);
		progress = player.getAdvancements().getOrStartProgress(holder);
		helper.assertTrue(count(progress.getCompletedCriteria()) == 1411 && count(progress.getRemainingCriteria()) == 0 && progress.isDone(), "All AND requirements must complete advancement");
		helper.succeed();
	}
	@GameTest
	public void legacyProgressMigrationAndSynchronizationAreIdempotent(GameTestHelper helper) {
		var migrated = TradeProgressData.canonicalizeLegacy(java.util.List.of(Identifier.withDefaultNamespace("diamond"), Identifier.withDefaultNamespace("enchanted_book"), Identifier.withDefaultNamespace("potion"), Identifier.withDefaultNamespace("splash_potion"), Identifier.withDefaultNamespace("lingering_potion"), Identifier.parse("examplemod:fake_item")));
		helper.assertTrue(migrated.equals(java.util.Set.of(Identifier.withDefaultNamespace("diamond"), AllItemsProgression.ENCHANTED_BOOK, AllItemsProgression.POTION)), "Production legacy decoder must canonicalize and deduplicate representative IDs");
		var player=(net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL); var data=helper.getLevel().getServer().overworld().getDataStorage().computeIfAbsent(TradeProgressData.TYPE);
		migrated.forEach(key -> data.add(player.getUUID(), key)); TradeAdvancements.synchronize(player);
		var holder=helper.getLevel().getServer().getAdvancements().get(TradeEverything.id("all_items")); var progress=player.getAdvancements().getOrStartProgress(holder); int first=count(progress.getCompletedCriteria()); TradeAdvancements.synchronize(player);
		helper.assertTrue(first==3 && count(player.getAdvancements().getOrStartProgress(holder).getCompletedCriteria())==3 && !progress.isDone(), "Production join synchronization must restore only saved criteria and be idempotent"); helper.succeed();
	}
	private static int count(Iterable<String> criteria) { int count = 0; for (String ignored : criteria) count++; return count; }
	@GameTest
	public void treCommandTreeReplacesLegacyRoot(GameTestHelper helper) {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		TradeEverythingCommands.registerForTesting(dispatcher);
		var root = dispatcher.getRoot().getChild("tre");
		helper.assertTrue(root != null && root.getChild("place") == null && root.getChild("summon") != null && root.getChild("verify") != null && root.getChild("reload") != null, "The obsolete Trading Post placement command must not be registered");
		helper.assertTrue(((ArgumentCommandNode<CommandSourceStack, ?>)root.getChild("summon").getChild("pos")).getType() instanceof net.minecraft.commands.arguments.coordinates.BlockPosArgument, "/tre summon must use Minecraft's block position argument");
		helper.assertTrue(dispatcher.getRoot().getChild("tradeeverything") == null, "The legacy /tradeeverything root must not be registered");
		helper.succeed();
	}

	@GameTest
	public void catalogIntegrityAndEligibility(GameTestHelper helper) {
		TradeCatalog.rebuild(helper.getLevel().registryAccess()); TradeCatalog.Audit audit = TradeCatalog.audit();
		for (Item item : new Item[] {Items.AIR, Items.BARRIER, Items.COMMAND_BLOCK, Items.STRUCTURE_BLOCK, Items.DEBUG_STICK, Items.PLAYER_HEAD, Items.VILLAGER_SPAWN_EGG})
			helper.assertTrue(!SurvivalEligibility.isEligible(item), BuiltInRegistries.ITEM.getKey(item) + " must be excluded");
		for (Item item : new Item[] {Items.OAK_LOG, Items.DIAMOND, Items.ELYTRA, Items.NETHERITE_INGOT})
			helper.assertTrue(SurvivalEligibility.isEligible(item), BuiltInRegistries.ITEM.getKey(item) + " must be eligible");
		helper.assertTrue(audit.enabled() > 1000 && audit.disabled() > 0 && audit.valid(), "Enabled catalog IDs and values must be unique and valid");
		helper.assertTrue(TradeCatalog.enabled(TradeEverything.id("forged")).isEmpty(), "Unknown IDs must be rejected");
		helper.assertTrue(TradeCatalog.enabled(BuiltInRegistries.ITEM.getKey(Items.BARRIER)).isEmpty(), "Disabled items must not be purchasable");
		helper.assertTrue(!com.coshian.tradeeverything.price.PriceConfig.validValues(Items.STONE, 0, 1)
			&& !com.coshian.tradeeverything.price.PriceConfig.validValues(Items.STONE, 1, 0)
			&& !com.coshian.tradeeverything.price.PriceConfig.validValues(Items.STONE, 1, 65), "Invalid prices and quantities must be rejected");
		helper.succeed();
	}

	@GameTest
	public void vanillaEnchantedBooksAreRealCatalogVariants(GameTestHelper helper) {
		TradeCatalog.rebuild(helper.getLevel().registryAccess());
		Identifier book = BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK);
		var vanilla = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(holder -> holder.unwrapKey().orElseThrow().identifier().getNamespace().equals("minecraft")).toList();
		var variants = TradeCatalog.enabledEntries().stream().filter(entry -> entry.id().equals(book) && entry.variantId() != null).toList();
		helper.assertTrue(variants.size() == vanilla.size() && !variants.isEmpty(), "Every and only vanilla enchantment must have one book variant");
		for (var entry : variants) {
			ItemStack output = TradeCatalog.output(entry); var stored = output.get(DataComponents.STORED_ENCHANTMENTS);
			helper.assertTrue(stored != null && stored.getLevel(entry.enchantment()) == ((TradeCatalog.EnchantmentVariant)entry.variant()).defaultLevel(), "Book output must carry its authoritative default stored enchantment level");
		}
		helper.assertTrue(TradeCatalog.enabled(book).isEmpty() && TradeCatalog.enabled(book, TradeEverything.id("forged_enchantment")).isEmpty(), "Blank books and forged variants must not be Buy entries");
		helper.succeed();
	}

	@GameTest
	public void typedVariantsResolveOnlyBoundedServerSelections(GameTestHelper helper) {
		TradeCatalog.rebuild(helper.getLevel().registryAccess());
		Identifier book = BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK);
		var mending = TradeCatalog.enabled(book, Identifier.withDefaultNamespace("mending")).orElseThrow();
		var sharpness = TradeCatalog.enabled(book, Identifier.withDefaultNamespace("sharpness")).orElseThrow();
		helper.assertTrue(mending.variant().kind() == TradeVariantKind.ENCHANTMENT && TradeCatalog.resolveOutput(mending, TradeVariantSelection.enchantment(1)).isPresent(), "Mending I must resolve from the server catalog");
		helper.assertTrue(TradeCatalog.resolveOutput(mending, TradeVariantSelection.enchantment(2)).isEmpty(), "Mending II must be rejected before transaction mutation");
		int sharpnessMax = ((TradeCatalog.EnchantmentVariant)sharpness.variant()).maximumLevel();
		helper.assertTrue(TradeCatalog.resolveOutput(sharpness, TradeVariantSelection.enchantment(1)).isPresent() && TradeCatalog.resolveOutput(sharpness, TradeVariantSelection.enchantment(sharpnessMax)).isPresent() && TradeCatalog.resolveOutput(sharpness, TradeVariantSelection.enchantment(sharpnessMax + 1)).isEmpty(), "Sharpness bounds must come from the authoritative entry");
		var potion = TradeCatalog.enabledEntries().stream().filter(entry -> entry.variant().kind() == TradeVariantKind.POTION).findFirst().orElseThrow();
		var normal = TradeCatalog.resolveOutput(potion, TradeVariantSelection.potion(0, TradeVariantSelection.PotionContainer.NORMAL)).orElseThrow();
		var splash = TradeCatalog.resolveOutput(potion, TradeVariantSelection.potion(0, TradeVariantSelection.PotionContainer.SPLASH)).orElseThrow();
		var lingering = TradeCatalog.resolveOutput(potion, TradeVariantSelection.potion(0, TradeVariantSelection.PotionContainer.LINGERING)).orElseThrow();
		helper.assertTrue(normal.is(Items.POTION) && splash.is(Items.SPLASH_POTION) && lingering.is(Items.LINGERING_POTION), "Potion container selection must map only to vanilla potion containers");
		helper.assertTrue(!com.coshian.tradeeverything.trade.SellEligibility.isSafeDefaultStack(normal) && !com.coshian.tradeeverything.trade.SellEligibility.isSafeDefaultStack(splash) && !com.coshian.tradeeverything.trade.SellEligibility.isSafeDefaultStack(TradeCatalog.resolveOutput(mending, TradeVariantSelection.enchantment(1)).orElseThrow()), "Component-bearing potion and enchanted-book stacks must remain outside default-stack Sell matching");
		var selectedPotion = potion.potionFamily().options().getFirst().potion();
		helper.assertTrue(normal.get(DataComponents.POTION_CONTENTS).potion().equals(java.util.Optional.of(selectedPotion)) && splash.get(DataComponents.POTION_CONTENTS).potion().equals(java.util.Optional.of(selectedPotion)) && lingering.get(DataComponents.POTION_CONTENTS).potion().equals(java.util.Optional.of(selectedPotion)), "Potion contents must be one server-selected holder across containers");
		helper.assertTrue(TradeCatalog.resolveOutput(potion, TradeVariantSelection.potion(-1, TradeVariantSelection.PotionContainer.NORMAL)).isEmpty() && TradeCatalog.resolveOutput(potion, TradeVariantSelection.potion(potion.potionFamily().options().size(), TradeVariantSelection.PotionContainer.NORMAL)).isEmpty(), "Potion option bounds must reject forged selections");
		Villager merchant = createMerchant(helper, new BlockPos(3, 1, 3));
		var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(merchant.position()); player.containerMenu = new TradeEverythingMenu(37, player.getInventory(), merchant.getId(), TradeCatalog.version());
		player.getInventory().add(new ItemStack(Items.EMERALD, 64)); int emeraldsBefore = count(player, Items.EMERALD);
		helper.assertTrue(TradeTransactionService.purchase(player, 37, TradeCatalog.version(), mending.id(), mending.variantId(), TradeVariantSelection.enchantment(2), 1) == TradeTransactionService.Result.INVALID_VARIANT && count(player, Items.EMERALD) == emeraldsBefore && count(player, Items.ENCHANTED_BOOK) == 0, "Invalid bounded variant selection must fail before payment or inventory mutation");
		helper.assertTrue(TradeTransactionService.purchase(player, 37, TradeCatalog.version(), potion.id(), potion.variantId(), TradeVariantSelection.potion(0, TradeVariantSelection.PotionContainer.SPLASH), 1) == TradeTransactionService.Result.SUCCESS, "A valid server-resolved potion selection must use the ordinary atomic transaction path");
		helper.succeed();
	}

	@GameTest
	public void oneMerchantInitializationIsIdempotentAndSerializable(GameTestHelper helper) {
		TradeCatalog.rebuild(); BlockPos relative = new BlockPos(1, 1, 1);
		Villager merchant = createMerchant(helper, relative);
		ClerkManager.initializeMarker(helper.getLevel(), createMarker(helper, relative, 0), 0);
		var merchants = helper.getLevel().getEntities(EntityTypes.VILLAGER, new AABB(merchant.blockPosition()).inflate(4), v -> v.entityTags().contains(ClerkManager.CLERK_TAG));
		helper.assertTrue(merchants.size() == 1 && merchant.entityTags().contains(ClerkManager.PRIMARY_TAG), "Repeated initialization must retain one primary merchant");
		helper.assertTrue(merchant.getOffers().isEmpty(), "Complete catalog must not be stored as MerchantOffers");
		helper.assertTrue(!merchant.isNoAi() && ClerkManager.anchor(merchant).isPresent(), "Merchant must retain normal villager AI and its anchor");
		helper.assertTrue(merchant.getCustomName() == null && !merchant.isCustomNameVisible(), "Merchant identity must not create a visible nameplate");
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess()); merchant.save(output);
		Entity loaded = EntityType.loadEntityRecursive(output.buildResult(), helper.getLevel(), new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.LOAD, false), entity -> entity);
		helper.assertTrue(loaded instanceof Villager restored && !restored.isNoAi() && restored.entityTags().contains(ClerkManager.PRIMARY_TAG)
			&& ClerkManager.anchor(restored).equals(ClerkManager.anchor(merchant)), "Primary identity and anchor must serialize");
		loaded.discard(); helper.succeed();
	}

	@GameTest
	public void legacyPagesMigrateWithoutTouchingNormalVillagers(GameTestHelper helper) {
		Villager ordinary = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Villager obsolete = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(ordinary != null && obsolete != null, "Villagers must be creatable");
		snapCenter(ordinary, helper.absolutePos(new BlockPos(1, 1, 1))); helper.getLevel().addFreshEntity(ordinary);
		snapCenter(obsolete, helper.absolutePos(new BlockPos(2, 1, 1))); obsolete.addTag(ClerkManager.CLERK_TAG); obsolete.addTag(ClerkManager.PAGE_PREFIX + 3); helper.getLevel().addFreshEntity(obsolete);
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(ordinary.isAlive() && !ordinary.entityTags().contains(ClerkManager.CLERK_TAG), "Ordinary villagers must remain untouched");
			helper.assertTrue(obsolete.isRemoved(), "Obsolete managed page merchants must be retired"); helper.succeed();
		});
	}

	@GameTest
	public void serverTradeValidationAndExactlyOnceDelivery(GameTestHelper helper) {
		TradeCatalog.rebuild(); Villager merchant = createMerchant(helper, new BlockPos(2, 1, 2));
		var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(merchant.position());
		TradeEverythingMenu menu = new TradeEverythingMenu(7, player.getInventory(), merchant.getId(), TradeCatalog.version()); player.containerMenu = menu;
		var oak = TradeCatalog.enabled(BuiltInRegistries.ITEM.getKey(Items.OAK_LOG)).orElseThrow();
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version(), oak.id(), 0) == TradeTransactionService.Result.INVALID_BUY_QUANTITY, "Zero Buy quantity rejected");
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version(), oak.id(), TradeTransactionService.MAX_BUY_QUANTITY + 1) == TradeTransactionService.Result.INVALID_BUY_QUANTITY, "Oversized Buy quantity rejected");
		helper.assertTrue(TradeTransactionService.purchase(player, 8, TradeCatalog.version(), oak.id()) == TradeTransactionService.Result.INVALID_SESSION, "Forged session rejected");
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version() + 1, oak.id()) == TradeTransactionService.Result.STALE_CATALOG, "Stale request rejected");
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version(), BuiltInRegistries.ITEM.getKey(Items.BARRIER)) == TradeTransactionService.Result.DISABLED_ITEM, "Disabled item rejected");
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version(), oak.id()) == TradeTransactionService.Result.INSUFFICIENT_PAYMENT, "Insufficient payment rejected");
		player.getInventory().add(new ItemStack(Items.EMERALD, oak.price() * 2));
		helper.assertTrue(TradeTransactionService.purchase(player, 7, TradeCatalog.version(), oak.id(), 2) == TradeTransactionService.Result.SUCCESS, "Multi-quantity transaction succeeds");
		int delivered = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
		int emeralds = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(delivered == oak.quantity() * 2 && emeralds == 0, "Output and payment must match requested transaction units");

		var fullPlayer = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
		fullPlayer.setPos(merchant.position()); fullPlayer.containerMenu = new TradeEverythingMenu(8, fullPlayer.getInventory(), merchant.getId(), TradeCatalog.version());
		List<ItemStack> fullInventory = fullPlayer.getInventory().getNonEquipmentItems();
		for (int slot = 0; slot < fullInventory.size(); slot++) fullInventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
		int expensiveQuantity = 129;
		int payment = oak.price() * expensiveQuantity;
		fullInventory.set(0, new ItemStack(Items.EMERALD_BLOCK, payment / 9));
		fullInventory.set(1, new ItemStack(Items.EMERALD, payment % 9));
		helper.assertTrue(TradeTransactionService.purchase(fullPlayer, 8, TradeCatalog.version(), oak.id(), expensiveQuantity) == TradeTransactionService.Result.INVENTORY_FULL,
			"Buy capacity failure must be detected on the simulated post-payment inventory");
		helper.assertTrue(count(fullPlayer, Items.EMERALD_BLOCK) == payment / 9 && count(fullPlayer, Items.EMERALD) == payment % 9 && count(fullPlayer, Items.OAK_LOG) == 0,
			"Failed Buy must preserve exact payment and deliver no partial output");
		helper.succeed();
	}

	@GameTest
	public void configuredCatalogControlsServerPurchaseAndCannotEnableTechnicalItems(GameTestHelper helper) {
		Path directory = null;
		try {
			directory = Files.createTempDirectory("tradeeverything-config-test-");
			Path config = directory.resolve(PriceConfig.FILE_NAME);
			Files.writeString(config, """
				{
				  "catalog_version": 92,
				  "items": {
				    "minecraft:cobblestone": {"enabled": true, "emeralds": 1, "output": 16},
				    "minecraft:diamond": {"enabled": false},
				    "minecraft:barrier": {"enabled": true, "emeralds": 1, "output": 1}
				  }
				}
				""");
			PriceConfig.Status status = PriceConfig.load(config, false);
			TradeCatalog.rebuild();
			helper.assertTrue(status.healthy(), "Valid item configuration must load cleanly");
			helper.assertTrue(TradeCatalog.enabled(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)).isEmpty(), "Configured disabled items must stay disabled");
			helper.assertTrue(TradeCatalog.enabled(BuiltInRegistries.ITEM.getKey(Items.BARRIER)).isEmpty(), "Survival eligibility must override configured enablement");

			var cobblestone = TradeCatalog.enabled(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)).orElseThrow();
			helper.assertTrue(cobblestone.price() == 1 && cobblestone.quantity() == 16, "Catalog must consume configured price and output");
			Villager merchant = createMerchant(helper, new BlockPos(2, 1, 2));
			var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
			player.setPos(merchant.position());
			player.containerMenu = new TradeEverythingMenu(9, player.getInventory(), merchant.getId(), TradeCatalog.version());
			player.getInventory().add(new ItemStack(Items.EMERALD));
			helper.assertTrue(TradeTransactionService.purchase(player, 9, 92, cobblestone.id()) == TradeTransactionService.Result.SUCCESS,
				"Server transaction must use configured catalog values");
			int delivered = player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(delivered == 16, "Configured output must be delivered exactly once");
			var blockPlayer = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
			blockPlayer.setPos(merchant.position()); blockPlayer.containerMenu = new TradeEverythingMenu(10, blockPlayer.getInventory(), merchant.getId(), TradeCatalog.version());
			blockPlayer.getInventory().add(new ItemStack(Items.EMERALD_BLOCK));
			helper.assertTrue(TradeTransactionService.purchase(blockPlayer, 10, 92, cobblestone.id()) == TradeTransactionService.Result.SUCCESS, "One Emerald Block must fund a one-Emerald purchase");
			helper.assertTrue(count(blockPlayer, Items.EMERALD) == 8 && count(blockPlayer, Items.EMERALD_BLOCK) == 0, "Block conversion must return exact Emerald change");
			helper.succeed();
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		} finally {
			PriceConfig.load();
			TradeCatalog.rebuild();
			if (directory != null) {
				try { Files.deleteIfExists(directory.resolve(PriceConfig.FILE_NAME)); Files.deleteIfExists(directory); }
				catch (Exception ignored) { }
			}
		}
	}

	@GameTest
	public void serverSellUsesCatalogPricingAndMutatesInventoryAtomically(GameTestHelper helper) {
		Path directory = null;
		try {
			directory = Files.createTempDirectory("tradeeverything-sell-test-");
			Path config = directory.resolve(PriceConfig.FILE_NAME);
			Files.writeString(config, """
				{
				  "catalog_version": 93,
				  "items": {
				    "minecraft:diamond": {"emeralds": 10},
				    "minecraft:shulker_box": {"emeralds": 10},
				    "minecraft:blue_shulker_box": {"emeralds": 10},
				    "minecraft:cobblestone": {"emeralds": 1},
				    "minecraft:oak_log": {"enabled": false}
				  }
				}
				""");
			PriceConfig.load(config, false); TradeCatalog.rebuild();
			Villager merchant = createMerchant(helper, new BlockPos(2, 1, 2));
			var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
			player.setPos(merchant.position()); player.containerMenu = new TradeEverythingMenu(11, player.getInventory(), merchant.getId(), 93);
			ItemStack normalDiamonds = new ItemStack(Items.DIAMOND, 5);
			ItemStack namedDiamond = new ItemStack(Items.DIAMOND); namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("keep"));
			ItemStack damagedSword = new ItemStack(Items.DIAMOND_SWORD); damagedSword.setDamageValue(1);
			ItemStack filledBundle = new ItemStack(Items.BUNDLE); filledBundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(java.util.List.of(new ItemStackTemplate(Items.DIAMOND))));
			player.getInventory().add(normalDiamonds); player.getInventory().add(namedDiamond); player.getInventory().add(damagedSword); player.getInventory().add(filledBundle); player.getInventory().add(new ItemStack(Items.COBBLESTONE, 16));
			Identifier diamond = BuiltInRegistries.ITEM.getKey(Items.DIAMOND); Identifier cobblestone = BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE);
			Identifier diamondSword = BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD); Identifier bundle = BuiltInRegistries.ITEM.getKey(Items.BUNDLE);
			helper.assertTrue(TradeTransactionService.sell(player, 12, 93, diamond, 5) == TradeTransactionService.Result.INVALID_SESSION, "Forged Sell session rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 94, diamond, 5) == TradeTransactionService.Result.STALE_CATALOG, "Stale Sell request rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, TradeEverything.id("forged"), 1) == TradeTransactionService.Result.INVALID_ITEM, "Unknown Sell item rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, BuiltInRegistries.ITEM.getKey(Items.OAK_LOG), 1) == TradeTransactionService.Result.DISABLED_ITEM, "Disabled Sell item rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamond, 0) == TradeTransactionService.Result.INVALID_SELL_QUANTITY, "Zero Sell quantity rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamond, -1) == TradeTransactionService.Result.INVALID_SELL_QUANTITY, "Negative Sell quantity rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamond, TradeTransactionService.MAX_SELL_QUANTITY + 1) == TradeTransactionService.Result.INVALID_SELL_QUANTITY, "Oversized Sell quantity rejected");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamond, 6) == TradeTransactionService.Result.UNSUPPORTED_ITEM_COMPONENTS, "Named stack must not count toward Sell quantity");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamondSword, 1) == TradeTransactionService.Result.UNSUPPORTED_ITEM_COMPONENTS, "Damaged equipment must not be sellable");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, bundle, 1) == TradeTransactionService.Result.UNSUPPORTED_ITEM_COMPONENTS, "Bundles with contents must not be sellable");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, diamond, 5) == TradeTransactionService.Result.SUCCESS, "Buy 10 must sell five normal items for 25 Emeralds");
			helper.assertTrue(count(player, Items.DIAMOND) == 1 && count(player, Items.EMERALD) == 25, "Sell must remove only qualifying stacks and give the exact Buy 10-derived reward");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, cobblestone, 7) == TradeTransactionService.Result.INVALID_SELL_BUNDLE, "Buy 1 Sell requires eight items");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, cobblestone, 9) == TradeTransactionService.Result.INVALID_SELL_BUNDLE, "Sell bundles must not round quantities");
			helper.assertTrue(TradeNetworking.handleSell(player, new SellRequest(11, 93, cobblestone, 8, -1)) == TradeTransactionService.Result.SUCCESS, "Sell payload dispatch must invoke the authoritative service");
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, cobblestone, 8) == TradeTransactionService.Result.SUCCESS, "Second Sell request is independently exact");
			helper.assertTrue(count(player, Items.COBBLESTONE) == 0 && count(player, Items.EMERALD) == 27, "Two eight-item bundles must give exactly two Emeralds");

			ItemStack unselectedShulker = new ItemStack(Items.SHULKER_BOX);
			unselectedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
			ItemStack filledShulker = new ItemStack(Items.SHULKER_BOX);
			filledShulker.set(DataComponents.CUSTOM_NAME, Component.literal("keep shell"));
			filledShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 2))));
			player.getInventory().add(unselectedShulker); player.getInventory().add(filledShulker);
			Identifier shulker = BuiltInRegistries.ITEM.getKey(Items.SHULKER_BOX);
			var blueShulkerItem = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:blue_shulker_box"));
			int filledSlot = java.util.stream.IntStream.range(0, player.getInventory().getNonEquipmentItems().size())
				.filter(slot -> player.getInventory().getNonEquipmentItems().get(slot).is(Items.SHULKER_BOX)
					&& player.getInventory().getNonEquipmentItems().get(slot).get(DataComponents.CUSTOM_NAME) != null).findFirst().orElseThrow();
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, shulker, 1, filledSlot) == TradeTransactionService.Result.SUCCESS, "Filled Shulker must sell valid contents atomically");
			ItemStack retainedShell = player.getInventory().getNonEquipmentItems().get(filledSlot);
			helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> stack.is(Items.SHULKER_BOX) && stack.get(DataComponents.CONTAINER) != null)
				&& retainedShell.get(DataComponents.CONTAINER) == null && retainedShell.get(DataComponents.CUSTOM_NAME) != null && count(player, Items.EMERALD) == 37,
				"Only the selected filled Shulker must be emptied, preserving its named shell and rewarding its contents only");
			player.getInventory().add(new ItemStack(Items.SHULKER_BOX));
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, shulker, 1) == TradeTransactionService.Result.SUCCESS, "An empty Shulker must sell itself normally");
			helper.assertTrue(count(player, Items.SHULKER_BOX) == 2 && count(player, Items.EMERALD) == 42, "Empty Shulker must grant its normal outer reward without touching filled or named shells");
			ItemStack invalidShulker = new ItemStack(Items.SHULKER_BOX);
			ItemStack namedContent = new ItemStack(Items.DIAMOND); namedContent.set(DataComponents.CUSTOM_NAME, Component.literal("keep"));
			invalidShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(namedContent)));
			player.getInventory().add(invalidShulker);
			int invalidSlot = java.util.stream.IntStream.range(0, player.getInventory().getNonEquipmentItems().size()).filter(slot -> {
				var contents = player.getInventory().getNonEquipmentItems().get(slot).get(DataComponents.CONTAINER);
				return contents != null && contents.nonEmptyItemCopyStream().anyMatch(stack -> stack.get(DataComponents.CUSTOM_NAME) != null);
			}).findFirst().orElseThrow();
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, shulker, 1, invalidSlot) == TradeTransactionService.Result.UNSUPPORTED_CONTAINER_CONTENTS, "Invalid Shulker contents must reject the entire sale");
			helper.assertTrue(count(player, Items.SHULKER_BOX) == 3 && player.getInventory().getNonEquipmentItems().get(invalidSlot).get(DataComponents.CONTAINER) != null && count(player, Items.EMERALD) == 42, "Rejected Shulker sale must not mutate inventory or reward currency");
			ItemStack bundleRemainderShulker = new ItemStack(Items.SHULKER_BOX);
			bundleRemainderShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COBBLESTONE, 7))));
			player.getInventory().add(bundleRemainderShulker);
			int remainderSlot = java.util.stream.IntStream.range(0, player.getInventory().getNonEquipmentItems().size()).filter(slot -> {
				var contents = player.getInventory().getNonEquipmentItems().get(slot).get(DataComponents.CONTAINER);
				return contents != null && contents.nonEmptyItemCopyStream().anyMatch(stack -> stack.is(Items.COBBLESTONE) && stack.getCount() == 7);
			}).findFirst().orElseThrow();
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, shulker, 1, remainderSlot) == TradeTransactionService.Result.UNSUPPORTED_CONTAINER_CONTENTS, "Shulker bundle remainders must reject the whole sale");
			helper.assertTrue(player.getInventory().getNonEquipmentItems().get(remainderSlot).get(DataComponents.CONTAINER) != null && count(player, Items.EMERALD) == 42, "Rejected bundle remainder must leave contents and reward unchanged");
			ItemStack nestedShulker = new ItemStack(Items.SHULKER_BOX);
			nestedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(blueShulkerItem))));
			player.getInventory().add(nestedShulker);
			int nestedSlot = java.util.stream.IntStream.range(0, player.getInventory().getNonEquipmentItems().size()).filter(slot -> {
				var contents = player.getInventory().getNonEquipmentItems().get(slot).get(DataComponents.CONTAINER);
				return contents != null && contents.nonEmptyItemCopyStream().anyMatch(stack -> stack.is(blueShulkerItem));
			}).findFirst().orElseThrow();
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, shulker, 1, nestedSlot) == TradeTransactionService.Result.UNSUPPORTED_CONTAINER_CONTENTS, "Nested Shulkers must not recurse");
			ItemStack blueShulker = new ItemStack(blueShulkerItem);
			blueShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
			player.getInventory().add(blueShulker);
			Identifier blueShulkerId = BuiltInRegistries.ITEM.getKey(blueShulkerItem);
			int blueSlot = java.util.stream.IntStream.range(0, player.getInventory().getNonEquipmentItems().size()).filter(slot -> player.getInventory().getNonEquipmentItems().get(slot).is(blueShulkerItem)).findFirst().orElseThrow();
			helper.assertTrue(TradeTransactionService.sell(player, 11, 93, blueShulkerId, 1, blueSlot) == TradeTransactionService.Result.SUCCESS, "Colored filled Shulkers must sell contents through the same path");
			helper.assertTrue(player.getInventory().getNonEquipmentItems().get(blueSlot).is(blueShulkerItem) && player.getInventory().getNonEquipmentItems().get(blueSlot).get(DataComponents.CONTAINER) == null && count(player, Items.EMERALD) == 47, "Colored Shulker shell must remain and only contents reward it");

			var fullPlayer = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
			fullPlayer.setPos(merchant.position()); fullPlayer.containerMenu = new TradeEverythingMenu(12, fullPlayer.getInventory(), merchant.getId(), 93);
			for (int slot = 0; slot < fullPlayer.getInventory().getNonEquipmentItems().size(); slot++) fullPlayer.getInventory().getNonEquipmentItems().set(slot, new ItemStack(Items.COBBLESTONE, 64));
			helper.assertTrue(TradeTransactionService.sell(fullPlayer, 12, 93, cobblestone, 8) == TradeTransactionService.Result.REWARD_INVENTORY_FULL, "Sell must fail before removal when Emerald reward cannot fit");
			helper.assertTrue(count(fullPlayer, Items.COBBLESTONE) == 64 * fullPlayer.getInventory().getNonEquipmentItems().size(), "Capacity failure must leave all sold items untouched");
			ItemStack capacityShulker = new ItemStack(Items.SHULKER_BOX);
			capacityShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
			fullPlayer.getInventory().getNonEquipmentItems().set(0, capacityShulker);
			helper.assertTrue(TradeTransactionService.sell(fullPlayer, 12, 93, shulker, 1, 0) == TradeTransactionService.Result.REWARD_INVENTORY_FULL, "Filled Shulker reward capacity failure must reject before emptying");
			helper.assertTrue(fullPlayer.getInventory().getNonEquipmentItems().get(0).get(DataComponents.CONTAINER) != null && count(fullPlayer, Items.EMERALD) == 0, "Capacity failure must retain filled Shulker and award nothing");
			helper.succeed();
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		} finally {
			PriceConfig.load(); TradeCatalog.rebuild();
			if (directory != null) {
				try { Files.deleteIfExists(directory.resolve(PriceConfig.FILE_NAME)); Files.deleteIfExists(directory); }
				catch (Exception ignored) { }
			}
		}
	}

	@GameTest
	public void explicitSellBundlesOverrideDerivedPricingAtomically(GameTestHelper helper) {
		Path directory = null;
		try {
			directory = Files.createTempDirectory("tradeeverything-explicit-sell-"); Path config = directory.resolve(PriceConfig.FILE_NAME);
			Files.writeString(config, "{\"catalog_version\":95,\"items\":{\"minecraft:diamond\":{\"enabled\":true,\"buy\":{\"emeralds\":10,\"output\":1},\"sell\":{\"items\":4,\"emeralds\":3}}}}");
			PriceConfig.load(config, false); TradeCatalog.rebuild();
			Villager merchant = createMerchant(helper, new BlockPos(3, 1, 3)); var player = (net.minecraft.server.level.ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
			player.setPos(merchant.position()); player.containerMenu = new TradeEverythingMenu(95, player.getInventory(), merchant.getId(), 95); player.getInventory().add(new ItemStack(Items.DIAMOND, 8));
			Identifier diamond = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
			helper.assertTrue(TradeTransactionService.sell(player, 95, 95, diamond, 5) == TradeTransactionService.Result.INVALID_SELL_BUNDLE && count(player, Items.DIAMOND) == 8 && count(player, Items.EMERALD) == 0, "Explicit 4-item Sell bundles reject non-divisible quantities atomically");
			helper.assertTrue(TradeTransactionService.sell(player, 95, 95, diamond, 4) == TradeTransactionService.Result.SUCCESS && count(player, Items.DIAMOND) == 4 && count(player, Items.EMERALD) == 3, "Explicit Sell must override derived SellPricing with 4 -> 3");
			helper.assertTrue(TradeTransactionService.sell(player, 95, 95, diamond, 4) == TradeTransactionService.Result.SUCCESS && count(player, Items.EMERALD) == 6, "Multiple exact configured bundles scale with checked server arithmetic");
			helper.succeed();
		} catch (Exception exception) { throw new RuntimeException(exception); }
		finally { PriceConfig.load(); TradeCatalog.rebuild(); if (directory != null) try { Files.deleteIfExists(directory.resolve(PriceConfig.FILE_NAME)); Files.deleteIfExists(directory); } catch (Exception ignored) { } }
	}

	@GameTest(maxTicks = 60)
	public void merchantUsesNormalAiAndRetainsAnchor(GameTestHelper helper) {
		TradeCatalog.rebuild(); Villager merchant = createMerchant(helper, new BlockPos(2, 1, 2)); BlockPos anchor = ClerkManager.anchor(merchant).orElseThrow();
		merchant.setDeltaMovement(new Vec3(2, 1, -2)); merchant.snapTo(anchor.getX() + 3.5, anchor.getY(), anchor.getZ() + 3.5, 0, 0);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!merchant.isNoAi() && ClerkManager.anchor(merchant).equals(java.util.Optional.of(anchor)), "Merchant must retain normal AI and its persistent anchor"); helper.succeed();
		});
	}

	@GameTest
	public void standaloneMerchantUsesTheCanonicalInitializer(GameTestHelper helper) {
		BlockPos relative = new BlockPos(5, 1, 5); BlockPos absolute = helper.absolutePos(relative);
		Villager ordinary = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(ordinary != null, "Ordinary villager must be creatable");
		snapCenter(ordinary, helper.absolutePos(new BlockPos(7, 1, 5))); helper.getLevel().addFreshEntity(ordinary);
		Villager summoned = ClerkManager.createMerchant(helper.getLevel(), absolute).orElseThrow();
		helper.assertTrue(summoned.entityTags().contains(ClerkManager.CLERK_TAG) && summoned.entityTags().contains(ClerkManager.PRIMARY_TAG), "Standalone merchant must use canonical tags");
		helper.assertTrue(!summoned.isNoAi() && ClerkManager.anchor(summoned).equals(java.util.Optional.of(absolute)), "Standalone merchant must persist the requested anchor with normal AI");
		helper.assertTrue(summoned.getOffers().isEmpty(), "Standalone merchant must use the searchable catalog rather than giant MerchantOffers");
		helper.assertTrue(ordinary.isAlive() && !ordinary.entityTags().contains(ClerkManager.CLERK_TAG), "Standalone summon must not modify ordinary villagers");
		helper.succeed();
	}

	private static Villager createMerchant(GameTestHelper helper, BlockPos relative) {
		ArmorStand marker = createMarker(helper, relative, 0); ClerkManager.initializeMarker(helper.getLevel(), marker, 0);
		BlockPos absolute = helper.absolutePos(relative);
		return helper.getLevel().getEntities(EntityTypes.VILLAGER, new AABB(absolute).inflate(2), villager -> villager.entityTags().contains(ClerkManager.CLERK_TAG)).getFirst();
	}
	private static ArmorStand createMarker(GameTestHelper helper, BlockPos relative, int index) {
		BlockPos absolute = helper.absolutePos(relative); ArmorStand marker = EntityTypes.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		if (marker == null) throw new IllegalStateException("Could not create marker");
		snapCenter(marker, absolute); marker.addTag(ClerkManager.MARKER_PREFIX + index); helper.getLevel().addFreshEntity(marker); return marker;
	}
	private static int count(net.minecraft.server.level.ServerPlayer player, Item item) {
		return player.getInventory().getNonEquipmentItems().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
	private static void snapCenter(Entity entity, BlockPos pos) { entity.snapTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0); }
}
