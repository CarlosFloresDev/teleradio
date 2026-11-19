package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;

/** Cliente de TeleRadio: control, reproduccion por cercania y dibujo de las pantallas. */
public class TeleRadioClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MediaBlock.abrirControl = pos -> Minecraft.getInstance().gui.setScreen(new ControlScreen(pos));
		MediaBlock.abrirPortatil = () -> Minecraft.getInstance().gui.setScreen(ControlScreen.portatil());
		BlockEntityRendererRegistry.register(TeleRadioMod.MEDIA, PantallaRenderer::new);
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			if (be instanceof MediaBlockEntity media) {
				Reproductor.cargar(media);
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
			if (be instanceof MediaBlockEntity media) {
				Reproductor.descargar(media);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(Reproductor::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Reproductor.cerrarTodo());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Reproductor.cerrarTodo());
	}
}
