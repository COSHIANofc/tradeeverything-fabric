package com.coshian.tradeeverything.entity;

import com.coshian.tradeeverything.catalog.TradeCatalog;
import com.coshian.tradeeverything.network.TradeNetworking;
import com.coshian.tradeeverything.menu.TradeEverythingMenu;
import com.coshian.tradeeverything.price.PriceConfig;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class ClerkManager {
	public static final String CLERK_TAG = "tradeeverything.clerk";
	public static final String PRIMARY_TAG = "tradeeverything.primary";
	public static final String PAGE_PREFIX = "tradeeverything.page.";
	public static final String CATEGORY_PREFIX = "tradeeverything.category.";
	public static final String MARKER_PREFIX = "tradeeverything.marker.";
	public static final String VERSION_PREFIX = "tradeeverything.version.";
	public static final String ANCHOR_PREFIX = "tradeeverything.anchor.";
	private static final WeakHashMap<ServerLevel, Set<Villager>> TRACKED = new WeakHashMap<>();

	private ClerkManager() {}

	public static void registerEvents() {
		ServerEntityEvents.ENTITY_LOAD.register(ClerkManager::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> { if (entity instanceof Villager villager) tracked(level).remove(villager); });
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager) || !villager.entityTags().contains(CLERK_TAG)) return InteractionResult.PASS;
			if (player instanceof ServerPlayer serverPlayer && TradeNetworking.open(serverPlayer, villager)) return InteractionResult.SUCCESS_SERVER;
			return level.isClientSide() ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) if (player.containerMenu instanceof TradeEverythingMenu menu) {
				Entity entity = player.level().getEntity(menu.merchantId());
				if (entity instanceof Villager villager && villager.isAlive() && villager.entityTags().contains(CLERK_TAG)) {
					villager.getNavigation().stop();
					villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0);
				}
			}
		});
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof ArmorStand marker) markerIndex(marker).ifPresent(index -> initializeMarker(level, marker, index));
		else if (entity instanceof Villager villager && villager.entityTags().contains(CLERK_TAG)) {
			Optional<Integer> legacyPage = pageIndex(villager);
			if (!villager.entityTags().contains(PRIMARY_TAG) && legacyPage.isPresent() && legacyPage.get() != 0) { villager.discard(); return; }
			tracked(level).add(villager); configure(villager);
		}
	}

	private static Set<Villager> tracked(ServerLevel level) {
		return TRACKED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	public static boolean initializeMarker(ServerLevel level, ArmorStand marker, int markerIndex) {
		if (markerIndex != 0) { marker.discard(); return false; }
		BlockPos pos = marker.blockPosition();
		var existing = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(4.0), villager -> villager.entityTags().contains(CLERK_TAG));
		Villager villager = existing.isEmpty() ? null : existing.getFirst();
		boolean created = false;
		if (villager == null) {
			created = createMerchant(level, pos).isPresent();
		} else configure(villager);
		for (int i = 1; i < existing.size(); i++) existing.get(i).discard();
		marker.discard(); return created;
	}

	/** Creates a standalone or structure-backed canonical merchant using the shared persistent identity. */
	public static Optional<Villager> createMerchant(ServerLevel level, BlockPos pos) { return createMerchant(level, pos, EntitySpawnReason.COMMAND); }

	/** The sole merchant factory, shared by commands, legacy markers, and Swamp Hut worldgen. */
	public static Optional<Villager> createMerchant(ServerLevel level, BlockPos pos, EntitySpawnReason reason) {
		Villager villager = EntityTypes.VILLAGER.create(level, reason);
		if (villager == null) return Optional.empty();
		villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		villager.addTag(CLERK_TAG); setAnchor(villager, pos);
		villager.setPersistenceRequired(); villager.setCanPickUpLoot(false); villager.setAge(0);
		villager.setVillagerData(villager.getVillagerData().withType(level.registryAccess(), VillagerType.SWAMP).withProfession(level.registryAccess(), VillagerProfession.CLERIC).withLevel(99));
		configure(villager);
		level.addFreshEntityWithPassengers(villager);
		tracked(level).add(villager);
		return Optional.of(villager);
	}

	public static void markAllDirty(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) for (Villager villager : Set.copyOf(tracked(level))) configure(villager);
	}

	public static void configure(Villager villager) {
		villager.getOffers().clear();
		villager.entityTags().removeIf(tag -> tag.startsWith(PAGE_PREFIX) || tag.startsWith(CATEGORY_PREFIX) || tag.startsWith(VERSION_PREFIX));
		villager.addTag(CLERK_TAG); villager.addTag(PRIMARY_TAG); villager.addTag(VERSION_PREFIX + TradeCatalog.version());
		// Identity is carried by persistent tags; do not expose a nameplate to players.
		villager.setCustomName(null); villager.setCustomNameVisible(false);
		villager.setInvulnerable(PriceConfig.snapshot().protectNpcs()); villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
		villager.setNoAi(false);
	}

	public static Optional<Integer> pageIndex(Entity entity) { return parseIntTag(entity, PAGE_PREFIX); }
	public static Optional<BlockPos> anchor(Entity entity) {
		return entity.entityTags().stream().filter(tag -> tag.startsWith(ANCHOR_PREFIX)).findFirst().flatMap(tag -> {
			try { String[] values = tag.substring(ANCHOR_PREFIX.length()).split(",", 3); return Optional.of(new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]))); }
			catch (RuntimeException exception) { return Optional.empty(); }
		});
	}
	private static void setAnchor(Entity entity, BlockPos pos) {
		entity.entityTags().removeIf(tag -> tag.startsWith(ANCHOR_PREFIX)); entity.addTag(ANCHOR_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ());
	}
	private static Optional<Integer> markerIndex(Entity entity) { return parseIntTag(entity, MARKER_PREFIX); }
	private static Optional<Integer> parseIntTag(Entity entity, String prefix) {
		return entity.entityTags().stream().filter(tag -> tag.startsWith(prefix)).findFirst().flatMap(tag -> { try { return Optional.of(Integer.parseInt(tag.substring(prefix.length()))); } catch (NumberFormatException exception) { return Optional.empty(); } });
	}
}
