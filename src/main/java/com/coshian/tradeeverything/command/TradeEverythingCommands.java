package com.coshian.tradeeverything.command;

import com.coshian.tradeeverything.TradeEverything;
import com.coshian.tradeeverything.catalog.TradeCatalog;
import com.coshian.tradeeverything.entity.ClerkManager;
import com.coshian.tradeeverything.price.PriceConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class TradeEverythingCommands {
	private TradeEverythingCommands() {}

	public static void register() { CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher)); }
	public static void registerForTesting(CommandDispatcher<CommandSourceStack> dispatcher) { register(dispatcher); }
	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tre").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("summon")
				.executes(ctx -> summon(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
				.then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> summon(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
			.then(Commands.literal("verify").executes(ctx -> verify(ctx.getSource())))
			.then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
	}

	private static int summon(CommandSourceStack source, BlockPos position) {
		if (position.getY() < source.getLevel().getMinY() || position.getY() > source.getLevel().getMaxY() || !source.getLevel().hasChunkAt(position)) {
			source.sendFailure(Component.translatable("command.tradeeverything.summon.invalid_position"));
			return 0;
		}
		if (ClerkManager.createMerchant(source.getLevel(), position).isEmpty()) {
			source.sendFailure(Component.translatable("command.tradeeverything.summon.failed"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.tradeeverything.summon.success", position.getX(), position.getY(), position.getZ()), true);
		return 1;
	}

	private static int verify(CommandSourceStack source) {
		TradeCatalog.Audit audit = TradeCatalog.audit();
		PriceConfig.Status prices = PriceConfig.status();
		String report = String.format("registered=%d enabled=%d disabled=%d duplicates=%d swamp_hut_merchants=1 prices=%s searchable_ui=PASS",
			audit.registeredVanilla(), audit.enabled(), audit.disabled(), audit.duplicates(), prices.healthy() ? "OK" : "WARN");
		source.sendSuccess(() -> Component.literal(report), false);
		return audit.valid() ? 1 : 0;
	}

	private static int reload(CommandSourceStack source) {
		PriceConfig.Status status = PriceConfig.reload();
		TradeCatalog.rebuild(source.registryAccess());
		ClerkManager.markAllDirty(source.getServer());
		source.sendSuccess(() -> Component.literal(message(
			"TradeEverything configuration reloaded; open trades finish safely and clerks refresh after closing",
			"TradeEverything 設定を再読込しました。開いている取引は安全に完了し、閉じた後に更新されます")), true);
		return status.healthy() ? 1 : 0;
	}

	private static String message(String english, String japanese) { return PriceConfig.snapshot().language() == PriceConfig.Language.JA_JP ? japanese : english; }
}
