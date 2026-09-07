package com.coshian.tradeeverything.mixin;

import com.coshian.tradeeverything.entity.ClerkManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.2's SwampHutPiece marks its persisted Witch flag before adding the first
 * generated entity. Redirecting that exact first add preserves the flag and
 * leaves the second add (the vanilla Cat) completely untouched.
 */
@Mixin(SwampHutPiece.class)
public abstract class SwampHutPieceMixin {
	@Redirect(
		method = "postProcess",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V", ordinal = 0)
	)
	private void tradeeverything$replaceStructureWitch(WorldGenLevel level, Entity entity) {
		if (entity instanceof Witch) ClerkManager.createMerchant(level.getLevel(), entity.blockPosition(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
		else level.addFreshEntityWithPassengers(entity);
	}
}
