package com.aventura.teleradio.media;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Lo que muestra el control: lo comparten los bloques (radio, tele, pantalla) y los portatiles (audifonos, parlante). */
public interface FuenteMedia {
	@Nullable Pista actual();

	List<Pista> cola();

	boolean enPausa();

	long transcurrido();

	int volumen();

	int alcance();

	boolean repetir();

	int version();

	default boolean sonando() {
		return this.actual() != null && !this.enPausa();
	}
}
