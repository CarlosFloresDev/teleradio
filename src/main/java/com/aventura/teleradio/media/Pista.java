package com.aventura.teleradio.media;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Una cancion o video: link (YouTube, radio online, mp3/mp4...), titulo y duracion en segundos (0 = en vivo o desconocida).
 * El servidor solo guarda el link: cada jugador lo resuelve y reproduce en su propio cliente.
 */
public record Pista(String url, String titulo, int duracion) {
	public static final Codec<Pista> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("url").forGetter(Pista::url),
		Codec.STRING.fieldOf("titulo").forGetter(Pista::titulo),
		Codec.INT.fieldOf("duracion").forGetter(Pista::duracion)
	).apply(i, Pista::new));

	public static String tiempo(long segundos) {
		long h = segundos / 3600;
		long m = segundos % 3600 / 60;
		long s = segundos % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
