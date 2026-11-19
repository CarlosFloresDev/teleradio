package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Convierte lo que escribe el jugador (link de YouTube, radio online, mp3/mp4 o texto para buscar en YouTube)
 * en titulo, duracion y las direcciones directas del audio y del video usando yt-dlp.
 */
public final class Resolutor {
	/** Video hasta 480p (H.264) + mejor audio; si no hay separados, el mejor archivo combinado. */
	private static final String FORMATO_VIDEO = "bv*[height<=480][vcodec^=avc1]+ba/bv*[height<=480]+ba/b[height<=480]/b";
	private static final String FORMATO_AUDIO = "ba/b";
	private static final long CACHE_MS = 2 * 60 * 60 * 1000L;
	private static final Pattern DURACION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+)");
	private static final ExecutorService HILOS = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "TeleRadio-yt-dlp");
		t.setDaemon(true);
		return t;
	});
	private static final Map<String, Resultado> CACHE = new ConcurrentHashMap<>();
	/** Busquedas en curso: si dos pantallas piden lo mismo a la vez, yt-dlp corre una sola vez. */
	private static final Map<String, CompletableFuture<Resultado>> EN_CURSO = new ConcurrentHashMap<>();

	/**
	 * @param web link de la pagina (lo que se guarda en el bloque)
	 * @param audio direccion directa del audio (o null)
	 * @param video direccion directa del video (o null si es solo audio)
	 */
	public record Resultado(String titulo, int duracion, String web, @Nullable String audio, @Nullable String video, long creado) {
	}

	private Resolutor() {
	}

	public static boolean esLink(String texto) {
		String t = texto.trim().toLowerCase();
		return t.startsWith("http://") || t.startsWith("https://");
	}

	/** Resuelve en segundo plano. {@code video} pide tambien el video (televisores y pantallas). */
	public static CompletableFuture<Resultado> resolver(String entrada, boolean video) {
		String clave = (video ? "v|" : "a|") + entrada.trim();
		Resultado guardado = CACHE.get(clave);
		if (guardado != null && System.currentTimeMillis() - guardado.creado() < CACHE_MS) {
			return CompletableFuture.completedFuture(guardado);
		}
		return EN_CURSO.computeIfAbsent(clave, k -> CompletableFuture.supplyAsync(() -> {
			Resultado r = ejecutar(entrada.trim(), video);
			CACHE.put(clave, r);
			CACHE.put((video ? "v|" : "a|") + r.web(), r);
			return r;
		}, HILOS).whenComplete((r, e) -> EN_CURSO.remove(k)));
	}

	/** Olvida los links guardados (por ejemplo si caducaron). */
	public static void olvidar(String entrada) {
		String e = entrada.trim();
		CACHE.remove("v|" + e);
		CACHE.remove("a|" + e);
	}

	private static Resultado ejecutar(String entrada, boolean video) {
		boolean link = esLink(entrada);
		List<String> cmd = new ArrayList<>(List.of(Herramientas.ytdlp(), "--no-warnings", "--no-playlist", "--no-progress",
			"--js-runtimes", "deno:" + Herramientas.deno(),
			"-f", video ? FORMATO_VIDEO : FORMATO_AUDIO,
			"--print", "%(title)s", "--print", "%(duration)s", "--print", "%(is_live)s", "--print", "%(webpage_url)s", "--print", "%(urls)s",
			// "--" evita que un texto que empiece con "-" se tome como opcion
			"--", link ? entrada : "ytsearch1:" + entrada));
		try {
			List<String> lineas = correr(cmd, 60);
			if (lineas.size() >= 5) {
				String titulo = lineas.get(0);
				boolean vivo = "True".equalsIgnoreCase(lineas.get(2));
				int duracion = vivo ? 0 : entero(lineas.get(1));
				String web = lineas.get(3);
				List<String> urls = lineas.subList(4, lineas.size()).stream().filter(Resolutor::esLink).toList();
				String urlVideo = null;
				String urlAudio = null;
				if (urls.size() >= 2) {
					urlVideo = urls.get(0);
					urlAudio = urls.get(1);
				} else if (urls.size() == 1) {
					urlAudio = urls.get(0);
					urlVideo = video ? urls.get(0) : null;
				}
				if (duracion == 0 && !vivo && urlAudio != null) {
					duracion = medirDuracion(urlAudio);
				}
				return new Resultado(titulo, duracion, esLink(web) ? web : entrada, urlAudio, urlVideo, System.currentTimeMillis());
			}
		} catch (IOException e) {
			TeleRadioMod.LOGGER.warn("yt-dlp falló con {}: {}", entrada, e.getMessage());
		}
		if (link) {
			// link directo (mp3, mp4, radio online): ffmpeg lo puede abrir sin yt-dlp
			String nombre = entrada.substring(entrada.lastIndexOf('/') + 1);
			return new Resultado(nombre.isBlank() ? entrada : nombre, medirDuracion(entrada), entrada, entrada, video ? entrada : null,
				System.currentTimeMillis());
		}
		throw new IllegalStateException("No se encontró nada para \"" + entrada + "\"");
	}

	/** Duracion de un archivo con ffmpeg (0 si es en vivo o no se sabe). */
	private static int medirDuracion(String url) {
		try {
			List<String> salida = correr(List.of(Herramientas.ffmpeg(), "-hide_banner", "-nostdin",
				"-protocol_whitelist", Sesion.PROTOCOLOS, "-i", url), 20);
			for (String linea : salida) {
				Matcher m = DURACION.matcher(linea);
				if (m.find()) {
					return Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
				}
			}
		} catch (IOException ignored) {
			// sin duracion
		}
		return 0;
	}

	private static int entero(String texto) {
		try {
			return (int) Math.round(Double.parseDouble(texto.trim()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Ejecuta un programa (sin consola) y devuelve su salida (stdout + stderr). */
	static List<String> correr(List<String> cmd, int segundos) throws IOException {
		Process proceso = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		// vigia: si el programa se cuelga sin escribir nada, se cierra igual al pasar el tiempo
		Thread vigia = new Thread(() -> {
			try {
				if (!proceso.waitFor(segundos, TimeUnit.SECONDS)) {
					proceso.destroyForcibly();
				}
			} catch (InterruptedException ignored) {
				// fin
			}
		}, "TeleRadio-vigia");
		vigia.setDaemon(true);
		vigia.start();
		List<String> lineas = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
			String linea;
			while ((linea = in.readLine()) != null) {
				lineas.add(linea);
			}
			if (!proceso.waitFor(segundos, TimeUnit.SECONDS)) {
				proceso.destroyForcibly();
				throw new IOException("tiempo agotado");
			}
		} catch (InterruptedException e) {
			proceso.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new IOException("interrumpido");
		}
		if (proceso.exitValue() != 0 && !cmd.get(0).equals(Herramientas.ffmpeg())) {
			throw new IOException(lineas.isEmpty() ? "código " + proceso.exitValue() : lineas.getLast());
		}
		return lineas;
	}
}
