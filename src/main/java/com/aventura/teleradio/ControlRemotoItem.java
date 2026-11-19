package com.aventura.teleradio;

import com.aventura.teleradio.media.MediaBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Control remoto: apunta a una radio, televisor o pantalla (hasta 32 bloques) y abre su control. */
public class ControlRemotoItem extends Item {
	public static final double ALCANCE = 32.0;

	public ControlRemotoItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		Vec3 ojo = player.getEyePosition();
		Vec3 fin = ojo.add(player.getViewVector(1.0F).scale(ALCANCE));
		BlockHitResult hit = level.clip(new ClipContext(ojo, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).getBlock() instanceof MediaBlock) {
			if (level.isClientSide()) {
				MediaBlock.abrirControl.accept(hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			player.sendOverlayMessage(Component.literal("Apunta a una radio, televisor o pantalla").withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.PASS;
	}
}
