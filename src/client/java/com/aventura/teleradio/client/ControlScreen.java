package com.aventura.teleradio.client;

import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.FuenteMedia;
import com.aventura.teleradio.media.Pista;
import com.aventura.teleradio.media.Portatil;
import com.aventura.teleradio.net.PortatilPayload;
import com.aventura.teleradio.net.ControlPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Control de una radio, televisor o pantalla: buscar o pegar un link, cola de reproduccion, pausa, saltar,
 * volumen, alcance y (pantalla gigante) tamano. Si faltan los programas, ofrece descargarlos.
 */
public class ControlScreen extends Screen {
	private static final int W = 330;
	private static final int H = 236;

	/** Bloque controlado; null = audifonos / parlante del jugador. */
	private final @Nullable BlockPos pos;
	private EditBox entrada;
	private String textoEntrada = "";
	private volatile boolean buscando;
	private volatile String aviso = "";
	private volatile int colorAviso = 0xFF90A4AE;
	private int ultimaVersion = -1;
	private int ultimoTamanoCola = -1;
	private boolean ultimoEnPausa;
	private boolean ultimoRepetir;
	private Herramientas.Estado ultimoEstadoHerr;

	public ControlScreen(@Nullable BlockPos pos) {
		super(Component.literal("TeleRadio"));
		this.pos = pos == null ? null : pos.immutable();
	}

	/** Control de los audifonos / parlante del jugador. */
	public static ControlScreen portatil() {
		return new ControlScreen(null);
	}

	private @Nullable MediaBlockEntity be() {
		return this.pos != null && this.minecraft != null && this.minecraft.level != null
			&& this.minecraft.level.getBlockEntity(this.pos) instanceof MediaBlockEntity m ? m : null;
	}

	/** Lo que se controla: el bloque o lo portatil del jugador. */
	private @Nullable FuenteMedia fuente() {
		if (this.pos != null) {
			return this.be();
		}
		return this.minecraft != null && this.minecraft.player != null
			? this.minecraft.player.getAttachedOrElse(Portatil.TIPO, Portatil.vacio()) : null;
	}

	private MediaBlock.@Nullable Tipo tipo() {
		MediaBlockEntity be = this.be();
		return be == null ? null : be.tipo();
	}

	private int l() {
		return (this.width - W) / 2;
	}

	private int t() {
		return Math.max(2, (this.height - H) / 2);
	}

	private void enviar(int accion, int valor) {
		this.enviar(accion, "", "", 0, valor);
	}

	private void enviar(int accion, String url, String titulo, int duracion, int valor) {
		if (this.pos == null) {
			ClientPlayNetworking.send(new PortatilPayload(accion, url, titulo, duracion, valor));
		} else {
			ClientPlayNetworking.send(new ControlPayload(this.pos, accion, url, titulo, duracion, valor));
		}
	}

	// ================================================================== widgets

	@Override
	protected void init() {
		FuenteMedia be = this.fuente();
		if (be == null) {
			return;
		}
		int l = this.l();
		int t = this.t();
		this.ultimaVersion = be.version();
		this.ultimoTamanoCola = be.cola().size();
		this.ultimoEnPausa = be.enPausa();
		this.ultimoRepetir = be.repetir();
		this.ultimoEstadoHerr = Herramientas.estado();
		this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> this.onClose()).bounds(l + W - 18, t + 2, 16, 16).build());

		if (Herramientas.estado() != Herramientas.Estado.LISTAS) {
			this.initHerramientas();
			return;
		}

		this.entrada = new EditBox(this.font, l + 8, t + 66, 206, 18, Component.literal("Link o búsqueda"));
		this.entrada.setMaxLength(2048);
		this.entrada.setHint(Component.literal("Link de YouTube / radio, o escribe para buscar").withStyle(ChatFormatting.DARK_GRAY));
		this.entrada.setValue(this.textoEntrada);
		this.addRenderableWidget(this.entrada);
		this.setInitialFocus(this.entrada);
		Button ya = Button.builder(Component.literal("▶ Poner"), b -> this.buscarY(ControlPayload.REPRODUCIR)).bounds(l + 218, t + 65, 52, 20)
			.tooltip(Tooltip.create(Component.literal("Reproducir ahora (reemplaza lo que suena)"))).build();
		Button cola = Button.builder(Component.literal("+ Cola"), b -> this.buscarY(ControlPayload.ENCOLAR)).bounds(l + 272, t + 65, 50, 20)
			.tooltip(Tooltip.create(Component.literal("Agregar a la lista (suena después)"))).build();
		ya.active = !this.buscando;
		cola.active = !this.buscando;
		this.addRenderableWidget(ya);
		this.addRenderableWidget(cola);

		boolean hay = be.actual() != null;
		int y = t + 90;
		int bw = 48;
		int x = l + 8;
		// fila de controles: anchos fijos que entran en el panel
		int[] anchos = {44, 48, 44, 58, 52, 64};
		int[] xs = new int[anchos.length];
		for (int i = 0, cx = x; i < anchos.length; cx += anchos[i] + 1, i++) {
			xs[i] = cx;
		}
		Button[] controles = {
			Button.builder(Component.literal("-10 s"), b -> this.enviar(ControlPayload.SALTAR, -10)).bounds(xs[0], y, anchos[0], 20).build(),
			Button.builder(Component.literal(be.enPausa() ? "Seguir" : "Pausa"), b -> this.enviar(ControlPayload.PAUSA, 0)).bounds(xs[1], y, anchos[1], 20).build(),
			Button.builder(Component.literal("+10 s"), b -> this.enviar(ControlPayload.SALTAR, 10)).bounds(xs[2], y, anchos[2], 20).build(),
			Button.builder(Component.literal("Siguiente"), b -> this.enviar(ControlPayload.SIGUIENTE, 0)).bounds(xs[3], y, anchos[3], 20).build(),
			Button.builder(Component.literal("Detener"), b -> this.enviar(ControlPayload.DETENER, 0)).bounds(xs[4], y, anchos[4], 20)
				.tooltip(Tooltip.create(Component.literal("Para todo y vacía la lista"))).build(),
		};
		for (Button b : controles) {
			b.active = hay;
			this.addRenderableWidget(b);
		}
		this.addRenderableWidget(Button.builder(Component.literal("Repetir: " + (be.repetir() ? "Sí" : "No")),
				b -> this.enviar(ControlPayload.REPETIR, 0)).bounds(xs[5], y, anchos[5], 20)
			.tooltip(Tooltip.create(Component.literal("Al terminar, la canción vuelve a la lista"))).build());

		y += 26;
		this.addRenderableWidget(new Deslizador(l + 8, y, 155, "Volumen", 0, 100, be.volumen(), "%",
			v -> this.enviar(ControlPayload.VOLUMEN, v)));
		this.addRenderableWidget(new Deslizador(l + 167, y, 155, "Alcance", MediaBlockEntity.MIN_ALCANCE, this.pos == null ? 32 : MediaBlockEntity.MAX_ALCANCE,
			be.alcance(), " bloques", v -> this.enviar(ControlPayload.ALCANCE, v)));
		if (be instanceof MediaBlockEntity pantalla && pantalla.tipo() == MediaBlock.Tipo.PANTALLA) {
			y += 24;
			int[] tam = {pantalla.ancho(), pantalla.alto()};
			this.addRenderableWidget(new Deslizador(l + 8, y, 155, "Ancho", 1, MediaBlockEntity.MAX_ANCHO, pantalla.ancho(), " bloques", v -> {
				tam[0] = v;
				this.enviar(ControlPayload.TAMANO, tam[0] * 100 + tam[1]);
			}));
			this.addRenderableWidget(new Deslizador(l + 167, y, 155, "Alto", 1, MediaBlockEntity.MAX_ALTO, pantalla.alto(), " bloques", v -> {
				tam[1] = v;
				this.enviar(ControlPayload.TAMANO, tam[0] * 100 + tam[1]);
			}));
		}

		// lista
		y += 34;
		List<Pista> lista = be.cola();
		for (int i = 0; i < Math.min(3, lista.size()); i++) {
			final int indice = i;
			this.addRenderableWidget(Button.builder(Component.literal("✖"), b -> this.enviar(ControlPayload.QUITAR, indice))
				.bounds(l + W - 24, y + i * 14 - 2, 14, 12).tooltip(Tooltip.create(Component.literal("Quitar de la lista"))).build());
		}
	}

	private void initHerramientas() {
		int l = this.l();
		int t = this.t();
		if (Herramientas.windows()) {
			Button bajar = Button.builder(Component.literal("Descargar programas (" + Herramientas.MEGAS + " MB)"),
				b -> {
					Herramientas.descargar();
					this.rebuildWidgets();
				}).bounds(l + W / 2 - 100, t + 150, 200, 20).build();
			bajar.active = Herramientas.estado() != Herramientas.Estado.DESCARGANDO;
			this.addRenderableWidget(bajar);
		}
	}

	/** Busca/resuelve lo escrito (en segundo plano) y manda al servidor el link, titulo y duracion. */
	private void buscarY(int accion) {
		String texto = this.entrada.getValue().trim();
		if (texto.isEmpty() || this.buscando) {
			return;
		}
		boolean video = this.tipo() != null && this.tipo().video();
		this.buscando = true;
		this.aviso = Resolutor.esLink(texto) ? "Abriendo link..." : "Buscando \"" + texto + "\" en YouTube...";
		this.colorAviso = 0xFF90A4AE;
		this.rebuildWidgets();
		Resolutor.resolver(texto, video).whenComplete((r, error) -> this.minecraft.execute(() -> {
			this.buscando = false;
			if (error != null || r == null) {
				Throwable causa = error != null && error.getCause() != null ? error.getCause() : error;
				this.aviso = "No se encontró: " + (causa != null ? causa.getMessage() : "?");
				this.colorAviso = 0xFFEF5350;
			} else {
				this.enviar(accion, r.web(), r.titulo(), r.duracion(), 0);
				this.aviso = (accion == ControlPayload.ENCOLAR ? "Agregado: " : "Poniendo: ") + r.titulo();
				this.colorAviso = 0xFF81C784;
				this.textoEntrada = "";
				if (this.entrada != null) {
					this.entrada.setValue("");
				}
			}
			this.rebuildWidgets();
		}));
	}

	@Override
	public void tick() {
		FuenteMedia be = this.fuente();
		if (be == null) {
			this.onClose();
			return;
		}
		// reconstruir si cambio algo desde otro jugador o termino la descarga
		if (be.version() != this.ultimaVersion || be.cola().size() != this.ultimoTamanoCola || be.enPausa() != this.ultimoEnPausa
			|| be.repetir() != this.ultimoRepetir || Herramientas.estado() != this.ultimoEstadoHerr) {
			if (this.entrada != null) {
				this.textoEntrada = this.entrada.getValue();
			}
			this.rebuildWidgets();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.entrada != null && this.entrada.isFocused()
			&& (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			this.buscarY(ControlPayload.REPRODUCIR);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== dibujo

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		FuenteMedia be = this.fuente();
		int l = this.l();
		int t = this.t();
		g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFF0D1117);
		g.fill(l, t, l + W, t + H, 0xF0161B22);
		g.fill(l, t, l + W, t + 20, 0xFF1565C0);
		if (be == null) {
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}
		String nombre;
		if (be instanceof MediaBlockEntity bloque) {
			nombre = switch (bloque.tipo()) {
				case RADIO -> "Radio";
				case TELEVISOR -> "Televisor";
				case PANTALLA -> "Pantalla gigante (" + bloque.ancho() + "x" + bloque.alto() + ")";
			};
		} else {
			Portatil p = (Portatil) be;
			nombre = switch (p.salida()) {
				case Portatil.AUDIFONOS -> "Audífonos (solo tú escuchas)";
				case Portatil.PARLANTE -> "Parlante portátil (escuchan los de cerca)";
				default -> "Audífonos / Parlante (llévalos en el inventario)";
			};
		}
		g.text(this.font, Component.literal("♪ " + nombre).withStyle(ChatFormatting.BOLD), l + 8, t + 6, 0xFFFFFFFF);

		if (Herramientas.estado() != Herramientas.Estado.LISTAS) {
			this.dibujarHerramientas(g);
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}

		// que suena
		Pista pista = be.actual();
		if (pista == null) {
			g.text(this.font, "No está sonando nada", l + 8, t + 28, 0xFF90A4AE, false);
		} else {
			String estado = be.enPausa() ? "❚❚ " : "▶ ";
			g.text(this.font, estado + this.font.plainSubstrByWidth(pista.titulo(), W - 30), l + 8, t + 26, 0xFFFFFFFF, false);
			long seg = be.transcurrido() / 1000;
			String tiempo = pista.duracion() > 0 ? Pista.tiempo(seg) + " / " + Pista.tiempo(pista.duracion()) : "EN VIVO · " + Pista.tiempo(seg);
			int bx = l + 8;
			int bw = W - 16 - this.font.width(tiempo) - 8;
			g.fill(bx, t + 40, bx + bw, t + 44, 0xFF37474F);
			if (pista.duracion() > 0) {
				g.fill(bx, t + 40, bx + (int) (bw * Mth.clamp(seg / (double) pista.duracion(), 0, 1)), t + 44, 0xFF42A5F5);
			} else {
				g.fill(bx, t + 40, bx + bw, t + 44, 0xFFE53935);
			}
			g.text(this.font, tiempo, bx + bw + 6, t + 38, 0xFFCFD8DC, false);
			Sesion sesion = this.pos == null ? Reproductor.sesionPortatil(this.minecraft.player.getUUID()) : Reproductor.sesion(this.pos);
			String local = sesion == null ? "Acércate al bloque para escucharlo" : switch (sesion.estado()) {
				case BUSCANDO -> "Preparando...";
				case CARGANDO -> "Cargando audio y video...";
				case SONANDO -> "Sonando en tu PC";
				case ERROR -> "Error: " + sesion.mensaje();
				case TERMINADA -> "Terminó";
			};
			int color = sesion != null && sesion.estado() == Sesion.Estado.ERROR ? 0xFFEF5350 : 0xFF78909C;
			g.text(this.font, this.font.plainSubstrByWidth(local, W - 16), l + 8, t + 50, color, false);
		}

		// lista
		int y = t + (this.tipo() == MediaBlock.Tipo.PANTALLA ? 174 : 150);
		g.text(this.font, Component.literal("Lista (" + be.cola().size() + ")").withStyle(ChatFormatting.BOLD), l + 8, y - 12, 0xFFB0BEC5);
		if (be.cola().isEmpty()) {
			g.text(this.font, "Vacía · usa \"+ Cola\" para agregar", l + 8, y, 0xFF607D8B, false);
		}
		for (int i = 0; i < Math.min(3, be.cola().size()); i++) {
			Pista p = be.cola().get(i);
			String linea = (i + 1) + ". " + p.titulo();
			g.text(this.font, this.font.plainSubstrByWidth(linea, W - 70), l + 8, y + i * 14, 0xFFE0E0E0, false);
			if (p.duracion() > 0) {
				String d = Pista.tiempo(p.duracion());
				g.text(this.font, d, l + W - 30 - this.font.width(d), y + i * 14, 0xFF78909C, false);
			}
		}
		if (be.cola().size() > 3) {
			g.text(this.font, "... y " + (be.cola().size() - 3) + " más", l + 8, y + 42, 0xFF607D8B, false);
		}
		// aviso de busqueda (debajo del cuadro de texto)
		if (!this.aviso.isEmpty()) {
			g.text(this.font, this.font.plainSubstrByWidth(this.aviso, W - 16), l + 8, t + H - 12, this.colorAviso, false);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void dibujarHerramientas(GuiGraphicsExtractor g) {
		int l = this.l();
		int t = this.t();
		String[] lineas = Herramientas.windows() ? new String[] {
			"Para reproducir YouTube, radios y videos, TeleRadio",
			"necesita 3 programas gratuitos en tu PC:",
			"  • yt-dlp (saca el audio/video de YouTube)",
			"  • ffmpeg (decodifica el audio y el video)",
			"  • Deno (lo necesita yt-dlp para YouTube)",
			"Se descargan una sola vez a .minecraft/teleradio.",
			"Cada jugador que quiera escuchar debe tenerlos."
		} : new String[] {
			"TeleRadio necesita yt-dlp, ffmpeg y deno.",
			"Instálalos con el gestor de paquetes de tu sistema",
			"(ej. sudo apt install ffmpeg yt-dlp) y reinicia."
		};
		int y = t + 34;
		for (String linea : lineas) {
			g.text(this.font, linea, l + 14, y, 0xFFE0E0E0, false);
			y += 12;
		}
		if (Herramientas.estado() == Herramientas.Estado.DESCARGANDO || Herramientas.estado() == Herramientas.Estado.ERROR) {
			int color = Herramientas.estado() == Herramientas.Estado.ERROR ? 0xFFEF5350 : 0xFF81C784;
			g.centeredText(this.font, this.font.plainSubstrByWidth(Herramientas.progreso(), W - 20), l + W / 2, t + 180, color);
		}
	}

	/** Deslizador con minimo, maximo y unidad; manda el valor al soltarlo. */
	private static class Deslizador extends AbstractSliderButton {
		private final String nombre;
		private final int min;
		private final int max;
		private final String unidad;
		private final IntConsumer alSoltar;

		Deslizador(int x, int y, int w, String nombre, int min, int max, int valor, String unidad, IntConsumer alSoltar) {
			super(x, y, w, 20, Component.empty(), (valor - min) / (double) (max - min));
			this.nombre = nombre;
			this.min = min;
			this.max = max;
			this.unidad = unidad;
			this.alSoltar = alSoltar;
			this.updateMessage();
		}

		int entero() {
			return this.min + (int) Math.round(this.value * (this.max - this.min));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(this.nombre + ": " + this.entero() + this.unidad));
		}

		@Override
		protected void applyValue() {
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			this.alSoltar.accept(this.entero());
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			boolean r = super.keyPressed(event);
			if (r) {
				this.alSoltar.accept(this.entero());
			}
			return r;
		}
	}
}
