package com.aventura.teleradio.client;

import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * Audio PCM (48 kHz, 16 bits, mono) que llega de ffmpeg y se entrega al motor de sonido de Minecraft.
 * Es un anillo de 30 segundos: ffmpeg escribe (y espera si esta lleno) y Minecraft lee de a 1 segundo.
 * Mono para que el sonido salga desde el bloque (OpenAL solo ubica en 3D el audio mono).
 */
public class FlujoAudio implements AudioStream {
	public static final int HZ = 48000;
	public static final int BYTES_POR_SEGUNDO = HZ * 2;
	private static final AudioFormat FORMATO = new AudioFormat(HZ, 16, 1, true, false);

	private final byte[] anillo = new byte[BYTES_POR_SEGUNDO * 30];
	private int lectura;
	private int escritura;
	private int disponibles;
	private boolean fin;
	private boolean cerrado;
	/** Bytes de audio reales que ya se entregaron al motor de sonido. */
	private long entregados;

	@Override
	public AudioFormat getFormat() {
		return FORMATO;
	}

	/** Productor (hilo de ffmpeg): escribe, esperando si el anillo esta lleno. */
	public synchronized void escribir(byte[] datos, int n) throws InterruptedException {
		int hecho = 0;
		while (hecho < n) {
			while (this.disponibles == this.anillo.length && !this.cerrado) {
				this.wait(200);
			}
			if (this.cerrado) {
				return;
			}
			int espacio = Math.min(n - hecho, this.anillo.length - this.disponibles);
			int tramo = Math.min(espacio, this.anillo.length - this.escritura);
			System.arraycopy(datos, hecho, this.anillo, this.escritura, tramo);
			this.escritura = (this.escritura + tramo) % this.anillo.length;
			this.disponibles += tramo;
			hecho += tramo;
			this.notifyAll();
		}
	}

	public synchronized long entregados() {
		return this.entregados;
	}

	public synchronized void terminar() {
		this.fin = true;
		this.notifyAll();
	}

	public synchronized boolean terminado() {
		return this.fin;
	}

	public synchronized int disponibles() {
		return this.disponibles;
	}

	/** Descarta audio del principio (para alcanzar a los demas jugadores si ffmpeg tardo en arrancar). */
	public synchronized void descartar(int bytes) {
		bytes = Math.min(this.disponibles, bytes & ~1);
		this.lectura = (this.lectura + bytes) % this.anillo.length;
		this.disponibles -= bytes;
		this.notifyAll();
	}

	/**
	 * Minecraft pide ~1 segundo cada vez. Si ffmpeg no alcanzo (internet lento) se rellena con silencio para que
	 * el canal no se detenga; cuando el audio termino devuelve null y el sonido acaba solo.
	 */
	@Override
	public synchronized ByteBuffer read(int esperado) {
		if (this.cerrado || this.fin && this.disponibles == 0) {
			return null;
		}
		int n = Math.max(2, esperado & ~1);
		int copiar = Math.min(n, this.disponibles);
		int tamano = this.fin ? copiar : n;
		ByteBuffer out = BufferUtils.createByteBuffer(tamano);
		int restante = copiar;
		while (restante > 0) {
			int tramo = Math.min(restante, this.anillo.length - this.lectura);
			out.put(this.anillo, this.lectura, tramo);
			this.lectura = (this.lectura + tramo) % this.anillo.length;
			restante -= tramo;
		}
		this.disponibles -= copiar;
		this.entregados += copiar;
		while (out.position() < tamano) {
			out.put((byte) 0);
		}
		this.notifyAll();
		out.flip();
		return out;
	}

	@Override
	public synchronized void close() {
		this.cerrado = true;
		this.notifyAll();
	}
}
