package com.aventura.teleradio.net;

import com.aventura.teleradio.TeleRadioMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: control de los audifonos / parlante del jugador (mismas acciones que {@link ControlPayload}). */
public record PortatilPayload(int accion, String url, String titulo, int duracion, int valor) implements CustomPacketPayload {
	public static final Type<PortatilPayload> TYPE = new Type<>(TeleRadioMod.id("portatil"));
	public static final StreamCodec<ByteBuf, PortatilPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, PortatilPayload::accion,
		ByteBufCodecs.stringUtf8(2048), PortatilPayload::url,
		ByteBufCodecs.stringUtf8(256), PortatilPayload::titulo,
		ByteBufCodecs.VAR_INT, PortatilPayload::duracion,
		ByteBufCodecs.VAR_INT, PortatilPayload::valor,
		PortatilPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
