package com.aventura.teleradio.net;

import com.aventura.teleradio.TeleRadioMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: un boton del control de una radio, televisor o pantalla. */
public record ControlPayload(BlockPos pos, int accion, String url, String titulo, int duracion, int valor) implements CustomPacketPayload {
	public static final Type<ControlPayload> TYPE = new Type<>(TeleRadioMod.id("control"));
	public static final StreamCodec<ByteBuf, ControlPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ControlPayload::pos,
		ByteBufCodecs.VAR_INT, ControlPayload::accion,
		ByteBufCodecs.stringUtf8(2048), ControlPayload::url,
		ByteBufCodecs.stringUtf8(256), ControlPayload::titulo,
		ByteBufCodecs.VAR_INT, ControlPayload::duracion,
		ByteBufCodecs.VAR_INT, ControlPayload::valor,
		ControlPayload::new);

	public static final int REPRODUCIR = 0;
	public static final int ENCOLAR = 1;
	public static final int PAUSA = 2;
	public static final int SIGUIENTE = 3;
	public static final int DETENER = 4;
	public static final int VOLUMEN = 5;
	public static final int ALCANCE = 6;
	public static final int TAMANO = 7;
	public static final int REPETIR = 8;
	public static final int QUITAR = 9;
	public static final int SALTAR = 10;

	public static ControlPayload simple(BlockPos pos, int accion, int valor) {
		return new ControlPayload(pos, accion, "", "", 0, valor);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
