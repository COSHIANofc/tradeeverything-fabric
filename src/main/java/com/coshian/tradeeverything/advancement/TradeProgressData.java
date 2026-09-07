package com.coshian.tradeeverything.advancement;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World SavedData keeps all-items progress across logout and server restarts. */
final class TradeProgressData extends SavedData {
	static final SavedDataType<TradeProgressData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("tradeeverything", "trade_progress"), TradeProgressData::new,
		Codec.unboundedMap(Codec.STRING, Identifier.CODEC.listOf()).xmap(TradeProgressData::from, TradeProgressData::serialize), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	private final Map<String, Set<Identifier>> traded = new HashMap<>();
	private TradeProgressData() {}
	private static TradeProgressData from(Map<String, List<Identifier>> value) { TradeProgressData data = new TradeProgressData(); value.forEach((uuid, ids) -> data.traded.put(uuid, new HashSet<>(ids))); return data; }
	private Map<String, List<Identifier>> serialize() { Map<String, List<Identifier>> value = new HashMap<>(); traded.forEach((uuid, ids) -> value.put(uuid, ids.stream().sorted().toList())); return value; }
	boolean add(java.util.UUID player, Identifier id) { boolean changed = traded.computeIfAbsent(player.toString(), ignored -> new HashSet<>()).add(id); if (changed) setDirty(); return changed; }
	Set<Identifier> get(java.util.UUID player) { return traded.getOrDefault(player.toString(), Set.of()); }
}
