package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Reproduccion de una pista en un bloque, en este cliente:
 * <ol>
 *   <li>yt-dlp da las direcciones del audio y del video,</li>
 *   <li>un ffmpeg decodifica el audio (PCM mono) y otro el video (cuadros RGBA a 20 fps),</li>
 *   <li>ambos empiezan en el segundo que indica el servidor, asi todos ven lo mismo a la vez,</li>
 *   <li>el audio suena por el motor de sonido de Minecraft y el video se sube a una textura.</li>
 * </ol>
 */
public class Sesion {
	public enum Estado { BUSCANDO, CARGANDO, SONANDO, ERROR, TERMINADA }

	/** Protocolos que ffmpeg puede abrir: solo internet (nunca archivos del PC). */
	public static final String PROTOCOLOS = "http,https,tls,tcp,crypto,hls,httpproxy";
	private static final int FPS = 20;
	/** Segundos de audio a juntar antes de empezar a sonar (Minecraft lee 4 de golpe al empezar). */
	private static final double PREBUFFER = 4.5;
	private static int contador;

	public record Cuadro(long ms, byte[] pixeles) {
	}

	private final BlockPos pos;
	private final int version;
	private final String url;
	private final boolean conVideo;
	private final int duracion;
	private final int alcance;
	/** Momento (reloj local) en que la pista estaba en el segundo 0. */
	private final long inicioLocal;
	private final Supplier<Float> volumen;
	/** De donde sale el sonido (fijo para bloques; el jugador para el parlante). */
	private final Supplier<net.minecraft.world.phys.Vec3> posicion;
	/** Audifonos: suena "dentro de la cabeza", sin distancia. */
	private final boolean auricular;
	private final int anchoVideo;
	private final int altoVideo;

	private volatile Estado estado = Estado.BUSCANDO;
	private volatile String mensaje = "";
	private volatile String titulo = "";
	private volatile boolean cerrada;
	private volatile boolean videoTerminado;
	private volatile boolean audioTerminado;
	private volatile long inicioVivo = -1;
	private final List<Process> procesos = new ArrayList<>();
	private volatile FlujoAudio flujo = new FlujoAudio();
	/** Cambia en cada reintento: los hilos de un intento anterior se ignoran. */
	private volatile int generacion;
	private int intentos;
	private volatile String errorFfmpeg = "";
	private @Nullable SonidoStream sonido;
	private final ArrayBlockingQueue<Cuadro> cuadros = new ArrayBlockingQueue<>(6);
	private @Nullable DynamicTexture textura;
	private final Identifier texturaId;
	private boolean hayCuadro;

	public Sesion(BlockPos pos, int version, String url, boolean conVideo, int duracion, long transcurrido, int alcance,
		Supplier<Float> volumen, boolean grande) {
		this(pos, version, url, conVideo, duracion, transcurrido, alcance, volumen, grande, null, false);
	}

	/** Para audifonos / parlante: el sonido sigue al jugador ({@code posicionMovil}) o va directo a los oidos. */
	public Sesion(BlockPos pos, int version, String url, boolean conVideo, int duracion, long transcurrido, int alcance,
		Supplier<Float> volumen, boolean grande, @Nullable Supplier<net.minecraft.world.phys.Vec3> posicionMovil, boolean auricular) {
		this.pos = pos.immutable();
		this.posicion = posicionMovil != null ? posicionMovil : () -> net.minecraft.world.phys.Vec3.atCenterOf(this.pos);
		this.auricular = auricular;
		this.version = version;
		this.url = url;
		this.conVideo = conVideo;
		this.duracion = duracion;
		this.alcance = alcance;
		this.inicioLocal = System.currentTimeMillis() - transcurrido;
		this.volumen = volumen;
		this.anchoVideo = grande ? 640 : 480;
		this.altoVideo = grande ? 360 : 270;
		this.texturaId = TeleRadioMod.id("video/" + (contador++));
	}

	// ================================================================== datos para la pantalla y el renderizador

	public int version() {
		return this.version;
	}

	public String url() {
		return this.url;
	}

	public boolean conVideo() {
		return this.conVideo;
	}

	public int alcance() {
		return this.alcance;
	}

	public Estado estado() {
		return this.estado;
	}

	public String mensaje() {
		return this.mensaje;
	}

	public String titulo() {
		return this.titulo;
	}

	public boolean cerrada() {
		return this.cerrada;
	}

	public float volumen() {
		return this.volumen.get();
	}

	public net.minecraft.world.phys.Vec3 posicion() {
		return this.posicion.get();
	}

	public boolean auricular() {
		return this.auricular;
	}

	/** Segundos de audio que ya tomo el motor de sonido de Minecraft. */
	public double segundosEntregados() {
		return this.flujo.entregados() / (double) FlujoAudio.BYTES_POR_SEGUNDO;
	}

	public boolean hayCuadro() {
		return this.hayCuadro;
	}

	public Identifier textura() {
		return this.texturaId;
	}

	public float aspecto() {
		return this.anchoVideo / (float) this.altoVideo;
	}

	/** Posicion actual de la reproduccion en ms. */
	private long reloj() {
		if (this.duracion > 0) {
			return System.currentTimeMillis() - this.inicioLocal;
		}
		return this.inicioVivo < 0 ? -1 : System.currentTimeMillis() - this.inicioVivo;
	}

	// ================================================================== arranque

	public void iniciar() {
		Resolutor.resolver(this.url, this.conVideo).whenComplete((r, error) -> {
			if (this.cerrada) {
				return;
			}
			if (error != null) {
				Throwable causa = error.getCause() != null ? error.getCause() : error;
				this.fallar("No se pudo abrir: " + causa.getMessage());
				return;
			}
			this.titulo = r.titulo();
			try {
				this.lanzar(r, this.generacion, this.flujo);
			} catch (IOException e) {
				this.fallar("No se pudo iniciar ffmpeg: " + e.getMessage());
			}
		});
	}

	private void fallar(String texto) {
		this.estado = Estado.ERROR;
		this.mensaje = texto;
		TeleRadioMod.LOGGER.warn("TeleRadio {}: {}", this.pos, texto);
	}

	private List<String> entrada(String direccion, long desdeMs) {
		List<String> cmd = new ArrayList<>(List.of(Herramientas.ffmpeg(), "-nostdin", "-hide_banner", "-loglevel", "error",
			"-protocol_whitelist", PROTOCOLOS));
		if (!direccion.contains(".m3u8") && !direccion.contains("/manifest/")) {
			cmd.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "4"));
		}
		if (desdeMs > 500) {
			cmd.addAll(List.of("-ss", String.format(java.util.Locale.ROOT, "%.3f", desdeMs / 1000.0)));
		}
		cmd.addAll(List.of("-i", direccion));
		return cmd;
	}

	private void lanzar(Resolutor.Resultado r, int gen, FlujoAudio f) throws IOException {
		this.estado = Estado.CARGANDO;
		long desde = this.duracion > 0 ? Math.max(0, System.currentTimeMillis() - this.inicioLocal) : 0;
		if (this.duracion > 0 && desde >= this.duracion * 1000L) {
			this.estado = Estado.TERMINADA;
			return;
		}
		if (r.audio() != null) {
			List<String> cmd = this.entrada(r.audio(), desde);
			cmd.addAll(List.of("-vn", "-ac", "1", "-ar", Integer.toString(FlujoAudio.HZ), "-f", "s16le", "pipe:1"));
			Process audio = this.proceso(cmd);
			this.hilo("audio", () -> this.leerAudio(audio, gen, f));
			this.hilo("inicio", () -> this.esperarYSonar(desde, gen, f));
		} else {
			this.audioTerminado = true;
		}
		if (this.conVideo && r.video() != null) {
			List<String> cmd = this.entrada(r.video(), desde);
			cmd.addAll(List.of("-an", "-vf",
				"scale=" + this.anchoVideo + ":" + this.altoVideo + ":force_original_aspect_ratio=decrease,"
					+ "pad=" + this.anchoVideo + ":" + this.altoVideo + ":(ow-iw)/2:(oh-ih)/2:color=black,fps=" + FPS,
				"-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1"));
			Process video = this.proceso(cmd);
			this.hilo("video", () -> this.leerVideo(video, desde, gen));
		} else {
			this.videoTerminado = true;
		}
	}

	private Process proceso(List<String> cmd) throws IOException {
		Process p = new ProcessBuilder(cmd).start();
		// guardar el ultimo error de ffmpeg para mostrarlo si falla
		this.hilo("errores", () -> {
			try (java.io.BufferedReader err = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream()))) {
				String linea;
				while ((linea = err.readLine()) != null) {
					if (!linea.isBlank()) {
						this.errorFfmpeg = linea.trim();
					}
				}
			} catch (IOException ignored) {
				// proceso cerrado
			}
		});
		synchronized (this.procesos) {
			if (this.cerrada) {
				p.destroyForcibly();
			}
			this.procesos.add(p);
		}
		return p;
	}

	private void hilo(String nombre, Runnable tarea) {
		Thread t = new Thread(tarea, "TeleRadio-" + nombre);
		t.setDaemon(true);
		t.start();
	}

	private void leerAudio(Process p, int gen, FlujoAudio f) {
		byte[] buf = new byte[16384];
		try (InputStream in = p.getInputStream()) {
			int n;
			while (!this.cerrada && gen == this.generacion && (n = in.read(buf)) > 0) {
				f.escribir(buf, n);
			}
		} catch (IOException | InterruptedException e) {
			// cerrado
		}
		f.terminar();
		if (gen == this.generacion) {
			this.audioTerminado = true;
			this.revisarFin();
		}
	}

	/** Junta unos segundos de audio, se pone al dia con el reloj del servidor y empieza a sonar. */
	private void esperarYSonar(long desde, int gen, FlujoAudio f) {
		long limite = System.currentTimeMillis() + 20000;
		int necesario = (int) (PREBUFFER * FlujoAudio.BYTES_POR_SEGUNDO);
		while (!this.cerrada && gen == this.generacion && f.disponibles() < necesario && !f.terminado()
			&& System.currentTimeMillis() < limite) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				return;
			}
		}
		if (this.cerrada || gen != this.generacion) {
			return;
		}
		if (f.disponibles() == 0) {
			if (this.intentos < 2) {
				// los links de YouTube a veces fallan o caducan: pedir otros y volver a intentar
				TeleRadioMod.LOGGER.info("TeleRadio {}: sin audio ({}), reintentando", this.pos, this.errorFfmpeg);
				this.reintentar();
			} else {
				this.fallar("No llegó audio" + (this.errorFfmpeg.isEmpty() ? " (¿sin internet?)" : ": " + this.errorFfmpeg));
			}
			return;
		}
		if (this.duracion > 0) {
			// ffmpeg tardo en arrancar: saltar lo que ya paso para ir a la par con los demas
			long atraso = System.currentTimeMillis() - this.inicioLocal - desde;
			if (atraso > 0) {
				f.descartar((int) Math.min(Integer.MAX_VALUE, atraso * FlujoAudio.BYTES_POR_SEGUNDO / 1000));
			}
		} else {
			this.inicioVivo = System.currentTimeMillis();
		}
		Minecraft.getInstance().execute(() -> {
			if (!this.cerrada && gen == this.generacion) {
				this.sonido = new SonidoStream(this, f, this.alcance);
				Minecraft.getInstance().getSoundManager().play(this.sonido);
				this.estado = Estado.SONANDO;
			}
		});
	}

	private void leerVideo(Process p, long desde, int gen) {
		int tamano = this.anchoVideo * this.altoVideo * 4;
		long cuadro = 0;
		try (InputStream in = p.getInputStream()) {
			while (!this.cerrada && gen == this.generacion) {
				byte[] pixeles = in.readNBytes(tamano);
				if (pixeles.length < tamano) {
					break;
				}
				long ms = (this.duracion > 0 ? desde : 0) + cuadro * 1000 / FPS;
				cuadro++;
				while (!this.cerrada && !this.cuadros.offer(new Cuadro(ms, pixeles), 200, TimeUnit.MILLISECONDS)) {
					// esperar a que el renderizador consuma
				}
				if (this.audioTerminado && this.inicioVivo < 0 && this.duracion <= 0) {
					this.inicioVivo = System.currentTimeMillis(); // video sin audio
				}
			}
		} catch (IOException | InterruptedException e) {
			// cerrado
		}
		if (gen == this.generacion) {
			this.videoTerminado = true;
			this.revisarFin();
		}
	}

	/** Cierra los ffmpeg de este intento y empieza de nuevo con links frescos. */
	private void reintentar() {
		this.intentos++;
		this.generacion++;
		synchronized (this.procesos) {
			for (Process p : this.procesos) {
				p.destroyForcibly();
			}
			this.procesos.clear();
		}
		this.flujo.close();
		this.flujo = new FlujoAudio();
		this.cuadros.clear();
		this.audioTerminado = false;
		this.videoTerminado = false;
		this.estado = Estado.BUSCANDO;
		Resolutor.olvidar(this.url);
		this.iniciar();
	}

	private void revisarFin() {
		if (this.audioTerminado && this.videoTerminado && this.estado != Estado.ERROR) {
			this.estado = Estado.TERMINADA;
		}
	}

	// ================================================================== video (hilo de render)

	/** Sube a la textura el cuadro que toca segun el reloj. Se llama en el hilo de render. */
	public void actualizarVideo() {
		if (this.cerrada || !this.conVideo) {
			return;
		}
		long t = this.reloj();
		if (t < 0) {
			return;
		}
		Cuadro elegido = null;
		Cuadro siguiente;
		while ((siguiente = this.cuadros.peek()) != null && siguiente.ms() <= t) {
			elegido = this.cuadros.poll();
		}
		if (elegido == null) {
			return;
		}
		if (this.textura == null) {
			this.textura = new DynamicTexture(() -> "teleradio video", this.anchoVideo, this.altoVideo, true);
			Minecraft.getInstance().getTextureManager().register(this.texturaId, this.textura);
		}
		NativeImage imagen = this.textura.getPixels();
		if (imagen != null) {
			MemoryUtil.memByteBuffer(imagen.getPointer(), elegido.pixeles().length).put(elegido.pixeles());
			this.textura.upload();
			this.hayCuadro = true;
		}
	}

	// ================================================================== cierre

	/** Detiene todo: procesos, sonido y textura. Se puede llamar desde cualquier hilo. */
	public void cerrar() {
		if (this.cerrada) {
			return;
		}
		this.cerrada = true;
		synchronized (this.procesos) {
			for (Process p : this.procesos) {
				p.destroyForcibly();
			}
		}
		this.flujo.close();
		this.cuadros.clear();
		Minecraft.getInstance().execute(() -> {
			if (this.sonido != null) {
				Minecraft.getInstance().getSoundManager().stop(this.sonido);
			}
			if (this.textura != null) {
				Minecraft.getInstance().getTextureManager().release(this.texturaId);
				this.textura = null;
			}
		});
	}
}
