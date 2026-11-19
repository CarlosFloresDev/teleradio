package com.aventura.teleradio;

import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.Portatil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Audifonos (solo tu escuchas; se pueden poner en la cabeza) o Parlante Portatil (escuchan los de alrededor).
 * Funcionan desde cualquier parte del inventario. Click derecho: abrir el control.
 */
public class PortatilItem extends Item {
	private final int salida;

	public PortatilItem(int salida, Properties properties) {
		super(properties);
		this.salida = salida;
	}

	public int salida() {
		return this.salida;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			// lo ultimo que usaste es por donde suena (si llevas los dos)
			Portatil p = server.getAttachedOrCreate(Portatil.TIPO);
			server.setAttached(Portatil.TIPO, p.conSalida(p.salida(), this.salida));
			TeleRadioMod.actualizarSalida(server);
		} else if (level.isClientSide()) {
			MediaBlock.abrirPortatil.run();
		}
		return InteractionResult.SUCCESS;
	}
}
