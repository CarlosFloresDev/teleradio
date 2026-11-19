package com.aventura.teleradio.media;

import com.aventura.teleradio.TeleRadioMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Reproduccion portatil de un jugador (audifonos o parlante). Se guarda en el jugador y se sincroniza con todos
 * los clientes: el parlante lo escuchan los que estan cerca; los audifonos solo el dueno.
 * Es inmutable: cada cambio crea una copia (asi Fabric la vuelve a mandar).
 */
public final class Portatil implements FuenteMedia {
	public static final int NADA = 0;
	public static final int AUDIFONOS = 1;
	public static final int PARLANTE = 2;

	private final @Nullable Pista actual;
	private final List<Pista> cola;
	/** Reloj local en que la pista estaba en 0. */
	private final long inicio;
	private final long pausa;
	private final int volumen;
	private final int alcance;
	private final boolean repetir;
	private final int version;
	/** Por donde suena ahora (segun lo que lleva el jugador). */
	private final int salida;
	/** Lo ultimo que uso el jugador (si lleva los dos). */
	private final int preferida;

	public Portatil(@Nullable Pista actual, List<Pista> cola, long inicio, long pausa, int volumen, int alcance, boolean repetir, int version,
		int salida, int preferida) {
		this.actual = actual;
		this.cola = List.copyOf(cola);
		this.inicio = inicio;
		this.pausa = pausa;
		this.volumen = volumen;
		this.alcance = alcance;
		this.repetir = repetir;
		this.version = version;
		this.salida = salida;
		this.preferida = preferida;
	}

	public static Portatil vacio() {
		return new Portatil(null, List.of(), 0, -1, 70, 16, false, 0, NADA, PARLANTE);
	}

	/** Guardado/sincronizacion: se manda el tiempo transcurrido (cada lado reconstruye su reloj). */
	private record Datos(Optional<Pista> actual, List<Pista> cola, long transcurrido, boolean pausa, int volumen, int alcance, boolean repetir,
		int version, int salida, int preferida) {
		static final Codec<Datos> CODEC = RecordCodecBuilder.create(i -> i.group(
			Pista.CODEC.optionalFieldOf("actual").forGetter(Datos::actual),
			Pista.CODEC.listOf().fieldOf("cola").forGetter(Datos::cola),
			Codec.LONG.fieldOf("transcurrido").forGetter(Datos::transcurrido),
			Codec.BOOL.fieldOf("pausa").forGetter(Datos::pausa),
			Codec.INT.fieldOf("volumen").forGetter(Datos::volumen),
			Codec.INT.fieldOf("alcance").forGetter(Datos::alcance),
			Codec.BOOL.fieldOf("repetir").forGetter(Datos::repetir),
			Codec.INT.fieldOf("version").forGetter(Datos::version),
			Codec.INT.fieldOf("salida").forGetter(Datos::salida),
			Codec.INT.fieldOf("preferida").forGetter(Datos::preferida)
		).apply(i, Datos::new));
	}

	private Datos datos() {
		return new Datos(Optional.ofNullable(this.actual), this.cola, this.transcurrido(), this.pausa >= 0, this.volumen, this.alcance, this.repetir,
			this.version, this.salida, this.preferida);
	}

	private static Portatil desde(Datos d) {
		return new Portatil(d.actual().orElse(null), d.cola(), System.currentTimeMillis() - d.transcurrido(), d.pausa() ? d.transcurrido() : -1,
			d.volumen(), d.alcance(), d.repetir(), d.version(), d.salida(), d.preferida());
	}

	public static final Codec<Portatil> CODEC = Datos.CODEC.xmap(Portatil::desde, Portatil::datos);
	public static final StreamCodec<RegistryFriendlyByteBuf, Portatil> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(Datos.CODEC)
		.map(Portatil::desde, Portatil::datos);

	public static final AttachmentType<Portatil> TIPO = AttachmentRegistry.<Portatil>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(Portatil::vacio)
		.syncWith(STREAM_CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(TeleRadioMod.id("portatil"));

	// ================================================================== lectura

	@Override
	public @Nullable Pista actual() {
		return this.actual;
	}

	@Override
	public List<Pista> cola() {
		return this.cola;
	}

	@Override
	public boolean enPausa() {
		return this.pausa >= 0;
	}

	@Override
	public long transcurrido() {
		if (this.actual == null) {
			return 0;
		}
		return this.pausa >= 0 ? this.pausa : Math.max(0, System.currentTimeMillis() - this.inicio);
	}

	@Override
	public int volumen() {
		return this.volumen;
	}

	@Override
	public int alcance() {
		return this.alcance;
	}

	@Override
	public boolean repetir() {
		return this.repetir;
	}

	@Override
	public int version() {
		return this.version;
	}

	public int salida() {
		return this.salida;
	}

	public int preferida() {
		return this.preferida;
	}

	// ================================================================== cambios (devuelven una copia)

	private Portatil con(@Nullable Pista actual, List<Pista> cola, long inicio, long pausa, int volumen, int alcance, boolean repetir,
		int version, int salida, int preferida) {
		return new Portatil(actual, cola, inicio, pausa, volumen, alcance, repetir, version, salida, preferida);
	}

	public Portatil reproducir(Pista pista) {
		return this.con(pista, this.cola, System.currentTimeMillis(), -1, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida,
			this.preferida);
	}

	public Portatil encolar(Pista pista) {
		if (this.actual == null) {
			return this.reproducir(pista);
		}
		if (this.cola.size() >= MediaBlockEntity.MAX_COLA) {
			return this;
		}
		List<Pista> nueva = new ArrayList<>(this.cola);
		nueva.add(pista);
		return this.con(this.actual, nueva, this.inicio, this.pausa, this.volumen, this.alcance, this.repetir, this.version, this.salida,
			this.preferida);
	}

	public Portatil pausa() {
		if (this.actual == null) {
			return this;
		}
		long t = this.transcurrido();
		return this.pausa >= 0
			? this.con(this.actual, this.cola, System.currentTimeMillis() - this.pausa, -1, this.volumen, this.alcance, this.repetir, this.version + 1,
				this.salida, this.preferida)
			: this.con(this.actual, this.cola, this.inicio, t, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida, this.preferida);
	}

	public Portatil siguiente() {
		List<Pista> nueva = new ArrayList<>(this.cola);
		if (this.repetir && this.actual != null) {
			nueva.add(this.actual);
		}
		if (nueva.isEmpty()) {
			return this.con(null, nueva, 0, -1, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida, this.preferida);
		}
		Pista siguiente = nueva.removeFirst();
		return this.con(siguiente, nueva, System.currentTimeMillis(), -1, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida,
			this.preferida);
	}

	public Portatil detener() {
		return this.con(null, List.of(), 0, -1, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida, this.preferida);
	}

	public Portatil saltar(int segundos) {
		if (this.actual == null) {
			return this;
		}
		long nuevo = Math.max(0, this.transcurrido() + segundos * 1000L);
		if (this.actual.duracion() > 0) {
			nuevo = Math.min(nuevo, this.actual.duracion() * 1000L - 1000);
		}
		return this.pausa >= 0
			? this.con(this.actual, this.cola, this.inicio, nuevo, this.volumen, this.alcance, this.repetir, this.version + 1, this.salida, this.preferida)
			: this.con(this.actual, this.cola, System.currentTimeMillis() - nuevo, -1, this.volumen, this.alcance, this.repetir, this.version + 1,
				this.salida, this.preferida);
	}

	public Portatil quitar(int indice) {
		if (indice < 0 || indice >= this.cola.size()) {
			return this;
		}
		List<Pista> nueva = new ArrayList<>(this.cola);
		nueva.remove(indice);
		return this.con(this.actual, nueva, this.inicio, this.pausa, this.volumen, this.alcance, this.repetir, this.version, this.salida,
			this.preferida);
	}

	public Portatil conVolumen(int v) {
		return this.con(this.actual, this.cola, this.inicio, this.pausa, Mth.clamp(v, 0, 100), this.alcance, this.repetir, this.version, this.salida,
			this.preferida);
	}

	public Portatil conAlcance(int a) {
		return this.con(this.actual, this.cola, this.inicio, this.pausa, this.volumen, Mth.clamp(a, MediaBlockEntity.MIN_ALCANCE, 32), this.repetir,
			this.version + 1, this.salida, this.preferida);
	}

	public Portatil alternarRepetir() {
		return this.con(this.actual, this.cola, this.inicio, this.pausa, this.volumen, this.alcance, !this.repetir, this.version, this.salida,
			this.preferida);
	}

	public Portatil conSalida(int salida, int preferida) {
		return this.con(this.actual, this.cola, this.inicio, this.pausa, this.volumen, this.alcance, this.repetir, this.version, salida, preferida);
	}

	/** Si la pista termino (segun su duracion), pasa a la siguiente. */
	public Portatil avanzar() {
		if (this.actual != null && this.pausa < 0 && this.actual.duracion() > 0 && this.transcurrido() > this.actual.duracion() * 1000L + 1500) {
			return this.siguiente();
		}
		return this;
	}
}
