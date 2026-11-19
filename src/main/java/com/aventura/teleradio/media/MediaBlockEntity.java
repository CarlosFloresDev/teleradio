package com.aventura.teleradio.media;

import com.aventura.teleradio.TeleRadioMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Estado de una radio, televisor o pantalla: que suena, desde cuando, la cola, volumen, alcance y tamano.
 * El servidor manda el tiempo transcurrido; cada cliente arranca la reproduccion en ese segundo para que
 * todos los jugadores escuchen y vean lo mismo al mismo tiempo.
 */
public class MediaBlockEntity extends BlockEntity implements FuenteMedia {
	public static final int MAX_COLA = 20;
	public static final int MIN_ALCANCE = 4;
	public static final int MAX_ALCANCE = 64;
	public static final int MAX_ANCHO = 16;
	public static final int MAX_ALTO = 9;

	private @Nullable Pista actual;
	private final List<Pista> cola = new ArrayList<>();
	/** Momento (reloj local de quien tiene este objeto) en que la pista estaria en el segundo 0. */
	private long inicio;
	/** Posicion en ms si esta en pausa; -1 si esta sonando. */
	private long pausa = -1;
	private int volumen = 70;
	private int alcance = 24;
	private int ancho = 4;
	private int alto = 3;
	private boolean repetir;
	/** Cambia cada vez que hay que reiniciar la reproduccion en los clientes (otra pista, salto, pausa...). */
	private int version;

	public MediaBlockEntity(BlockPos pos, BlockState state) {
		super(TeleRadioMod.MEDIA, pos, state);
	}

	public MediaBlock.Tipo tipo() {
		return this.getBlockState().getBlock() instanceof MediaBlock media ? media.tipo() : MediaBlock.Tipo.RADIO;
	}

	public @Nullable Pista actual() {
		return this.actual;
	}

	public List<Pista> cola() {
		return this.cola;
	}

	public boolean enPausa() {
		return this.pausa >= 0;
	}

	public boolean sonando() {
		return this.actual != null && this.pausa < 0;
	}

	/** Milisegundos desde el inicio de la pista. */
	public long transcurrido() {
		if (this.actual == null) {
			return 0;
		}
		return this.pausa >= 0 ? this.pausa : Math.max(0, System.currentTimeMillis() - this.inicio);
	}

	public int volumen() {
		return this.volumen;
	}

	public int alcance() {
		return this.alcance;
	}

	public int ancho() {
		return this.ancho;
	}

	public int alto() {
		return this.alto;
	}

	public boolean repetir() {
		return this.repetir;
	}

	public int version() {
		return this.version;
	}

	// ================================================================== acciones (servidor)

	public void reproducir(Pista pista) {
		this.actual = pista;
		this.inicio = System.currentTimeMillis();
		this.pausa = -1;
		this.version++;
		this.sync();
	}

	public boolean encolar(Pista pista) {
		if (this.actual == null) {
			this.reproducir(pista);
			return true;
		}
		if (this.cola.size() >= MAX_COLA) {
			return false;
		}
		this.cola.add(pista);
		this.sync();
		return true;
	}

	public void pausa() {
		if (this.actual == null) {
			return;
		}
		if (this.pausa >= 0) {
			this.inicio = System.currentTimeMillis() - this.pausa;
			this.pausa = -1;
		} else {
			this.pausa = this.transcurrido();
		}
		this.version++;
		this.sync();
	}

	public void siguiente() {
		if (this.repetir && this.actual != null) {
			this.cola.add(this.actual);
		}
		if (this.cola.isEmpty()) {
			this.actual = null;
			this.pausa = -1;
			this.version++;
			this.sync();
		} else {
			this.reproducir(this.cola.removeFirst());
		}
	}

	public void detener() {
		this.actual = null;
		this.cola.clear();
		this.pausa = -1;
		this.version++;
		this.sync();
	}

	/** Adelanta o atrasa (segundos, puede ser negativo). */
	public void saltar(int segundos) {
		if (this.actual == null) {
			return;
		}
		long nuevo = Math.max(0, this.transcurrido() + segundos * 1000L);
		if (this.actual.duracion() > 0) {
			nuevo = Math.min(nuevo, this.actual.duracion() * 1000L - 1000);
		}
		if (this.pausa >= 0) {
			this.pausa = nuevo;
		} else {
			this.inicio = System.currentTimeMillis() - nuevo;
		}
		this.version++;
		this.sync();
	}

	public void quitarDeCola(int indice) {
		if (indice >= 0 && indice < this.cola.size()) {
			this.cola.remove(indice);
			this.sync();
		}
	}

	public void setVolumen(int volumen) {
		this.volumen = Mth.clamp(volumen, 0, 100);
		this.sync();
	}

	public void setAlcance(int alcance) {
		this.alcance = Mth.clamp(alcance, MIN_ALCANCE, MAX_ALCANCE);
		this.version++;
		this.sync();
	}

	public void setTamano(int ancho, int alto) {
		this.ancho = Mth.clamp(ancho, 1, MAX_ANCHO);
		this.alto = Mth.clamp(alto, 1, MAX_ALTO);
		this.sync();
	}

	public void alternarRepetir() {
		this.repetir = !this.repetir;
		this.sync();
	}

	/** Pasa a la siguiente pista cuando termina la actual. */
	public static void tickServidor(Level level, BlockPos pos, BlockState state, MediaBlockEntity be) {
		if (be.actual != null && be.pausa < 0 && be.actual.duracion() > 0
			&& be.transcurrido() > be.actual.duracion() * 1000L + 1500) {
			be.siguiente();
		}
	}

	private void sync() {
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	// ================================================================== guardado y sincronizacion

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("Actual", Pista.CODEC, this.actual);
		output.store("Cola", Pista.CODEC.listOf(), List.copyOf(this.cola));
		// se guarda el tiempo transcurrido (no la hora): asi funciona al reiniciar el servidor y en cada cliente
		output.putLong("Transcurrido", this.transcurrido());
		output.putBoolean("Pausa", this.pausa >= 0);
		output.putInt("Volumen", this.volumen);
		output.putInt("Alcance", this.alcance);
		output.putInt("Ancho", this.ancho);
		output.putInt("Alto", this.alto);
		output.putBoolean("Repetir", this.repetir);
		output.putInt("Version", this.version);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.actual = input.read("Actual", Pista.CODEC).orElse(null);
		this.cola.clear();
		this.cola.addAll(input.read("Cola", Pista.CODEC.listOf()).orElse(List.of()));
		long transcurrido = input.getLongOr("Transcurrido", 0L);
		boolean pausa = input.getBooleanOr("Pausa", false);
		this.pausa = pausa ? transcurrido : -1;
		this.inicio = System.currentTimeMillis() - transcurrido;
		this.volumen = input.getIntOr("Volumen", 70);
		this.alcance = input.getIntOr("Alcance", 24);
		this.ancho = input.getIntOr("Ancho", 4);
		this.alto = input.getIntOr("Alto", 3);
		this.repetir = input.getBooleanOr("Repetir", false);
		this.version = input.getIntOr("Version", 0);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
