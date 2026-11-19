package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Programas externos que usa TeleRadio en el PC de cada jugador:
 * <ul>
 *   <li>yt-dlp: saca el audio y el video de YouTube (y de muchas otras paginas),</li>
 *   <li>Deno: lo necesita yt-dlp para YouTube,</li>
 *   <li>ffmpeg: decodifica el audio y el video.</li>
 * </ul>
 * En Windows se descargan una sola vez (con permiso del jugador) a {@code .minecraft/teleradio/bin}.
 * En Linux/Mac se usan los instalados en el sistema.
 */
public final class Herramientas {
	public enum Estado { LISTAS, FALTAN, DESCARGANDO, ERROR }

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	private static final String URL_YTDLP = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
	private static final String URL_DENO = "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip";
	private static final String URL_FFMPEG = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
	/** Tamano aproximado total de la descarga, para avisar al jugador. */
	public static final int MEGAS = 175;

	private static volatile Estado estado;
	private static volatile String progreso = "";

	private Herramientas() {
	}

	public static Path carpeta() {
		String propia = System.getProperty("teleradio.bin");
		return propia != null ? Path.of(propia) : FabricLoader.getInstance().getGameDir().resolve("teleradio").resolve("bin");
	}

	private static String programa(String nombre) {
		Path local = carpeta().resolve(WINDOWS ? nombre + ".exe" : nombre);
		return Files.isRegularFile(local) || WINDOWS ? local.toString() : nombre;
	}

	public static String ytdlp() {
		return programa("yt-dlp");
	}

	public static String ffmpeg() {
		return programa("ffmpeg");
	}

	public static String deno() {
		return programa("deno");
	}

	public static boolean windows() {
		return WINDOWS;
	}

	public static Estado estado() {
		if (estado == null || estado == Estado.FALTAN || estado == Estado.LISTAS) {
			estado = revisar() ? Estado.LISTAS : Estado.FALTAN;
		}
		return estado;
	}

	public static boolean listas() {
		return estado() == Estado.LISTAS;
	}

	public static String progreso() {
		return progreso;
	}

	private static boolean revisar() {
		if (!WINDOWS) {
			return true; // en Linux/Mac se usan los del sistema; si faltan, el error se ve al reproducir
		}
		Path c = carpeta();
		return Files.isRegularFile(c.resolve("yt-dlp.exe")) && Files.isRegularFile(c.resolve("ffmpeg.exe")) && Files.isRegularFile(c.resolve("deno.exe"));
	}

	/** Descarga lo que falte en segundo plano (solo Windows). */
	public static synchronized void descargar() {
		if (!WINDOWS || estado == Estado.DESCARGANDO) {
			return;
		}
		estado = Estado.DESCARGANDO;
		Thread hilo = new Thread(() -> {
			try {
				Path c = carpeta();
				Files.createDirectories(c);
				HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
				if (!Files.isRegularFile(c.resolve("yt-dlp.exe"))) {
					bajar(http, URL_YTDLP, c.resolve("yt-dlp.exe"), null, "yt-dlp (1/3)");
				}
				if (!Files.isRegularFile(c.resolve("deno.exe"))) {
					bajar(http, URL_DENO, c.resolve("deno.exe"), "deno.exe", "Deno (2/3)");
				}
				if (!Files.isRegularFile(c.resolve("ffmpeg.exe"))) {
					bajar(http, URL_FFMPEG, c.resolve("ffmpeg.exe"), "/bin/ffmpeg.exe", "ffmpeg (3/3)");
				}
				progreso = "¡Listo!";
				estado = revisar() ? Estado.LISTAS : Estado.ERROR;
			} catch (Exception e) {
				TeleRadioMod.LOGGER.error("No se pudieron descargar las herramientas", e);
				progreso = "Error: " + e.getMessage();
				estado = Estado.ERROR;
			}
		}, "TeleRadio-descarga");
		hilo.setDaemon(true);
		hilo.start();
	}

	/**
	 * Descarga {@code url} a {@code destino}. Si {@code dentroDelZip} no es null, la descarga es un zip y se extrae
	 * solo el archivo cuyo nombre termina en ese texto.
	 */
	private static void bajar(HttpClient http, String url, Path destino, String dentroDelZip, String nombre) throws IOException, InterruptedException {
		progreso = "Descargando " + nombre + "...";
		HttpResponse<InputStream> resp = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "TeleRadio-Minecraft").build(),
			HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) {
			throw new IOException("HTTP " + resp.statusCode() + " al bajar " + nombre);
		}
		long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
		Path temporal = destino.resolveSibling(destino.getFileName() + ".descarga");
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(temporal)) {
			byte[] buf = new byte[1 << 16];
			long leidos = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				leidos += n;
				if (total > 0) {
					progreso = "Descargando " + nombre + "... " + (leidos * 100 / total) + "%";
				}
			}
		}
		if (dentroDelZip == null) {
			Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
			return;
		}
		progreso = "Descomprimiendo " + nombre + "...";
		boolean encontrado = false;
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(temporal))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				String nombreEntrada = entrada.getName().replace('\\', '/');
				if (!entrada.isDirectory() && (nombreEntrada.endsWith(dentroDelZip) || nombreEntrada.equals(dentroDelZip))) {
					Files.copy(zip, destino, StandardCopyOption.REPLACE_EXISTING);
					encontrado = true;
					break;
				}
			}
		} finally {
			Files.deleteIfExists(temporal);
		}
		if (!encontrado) {
			throw new IOException("No se encontró " + dentroDelZip + " en la descarga de " + nombre);
		}
	}
}
