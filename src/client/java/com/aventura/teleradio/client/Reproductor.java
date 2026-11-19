package com.aventura.teleradio.client;

import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.Pista;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Decide que bloques suenan en este cliente: los que estan reproduciendo y cerca del jugador
 * (como mucho 4 a la vez, 2 con video). Si cambia la pista, el volumen de alcance o hay pausa, reinicia.
 */
public final class Reproductor {
	private static final int MAX_SESIONES = 4;
	private static final int MAX_VIDEO = 2;
	/** Distancia extra para que el video se vea aunque el sonido llegue menos lejos. */
	private static final int VISTA_VIDEO = 40;

	private static final Map<BlockPos, MediaBlockEntity> CARGADOS = new HashMap<>();
	private static final Map<BlockPos, Sesion> SESIONES = new HashMap<>();
	/** Audifonos y parlantes: por jugador. */
	private static final Map<java.util.UUID, Sesion> PORTATILES = new HashMap<>();
	private static final int MAX_PORTATILES = 3;

	private Reproductor() {
	}

	public static void cargar(MediaBlockEntity be) {
		CARGADOS.put(be.getBlockPos().immutable(), be);
	}

	public static void descargar(MediaBlockEntity be) {
		BlockPos pos = be.getBlockPos();
		if (CARGADOS.get(pos) == be) {
			CARGADOS.remove(pos);
		}
	}

	public static @Nullable Sesion sesion(BlockPos pos) {
		return SESIONES.get(pos);
	}

	public static @Nullable Sesion sesionPortatil(java.util.UUID jugador) {
		return PORTATILES.get(jugador);
	}

	public static void cerrarTodo() {
		SESIONES.values().forEach(Sesion::cerrar);
		SESIONES.clear();
		PORTATILES.values().forEach(Sesion::cerrar);
		PORTATILES.clear();
		CARGADOS.clear();
	}

	private static int limite(MediaBlockEntity be) {
		return be.tipo().video() ? Math.max(be.alcance(), VISTA_VIDEO) + 8 : be.alcance() + 8;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			if (!SESIONES.isEmpty() || !CARGADOS.isEmpty()) {
				cerrarTodo();
			}
			return;
		}
		Vec3 ojo = mc.player.position();
		CARGADOS.values().removeIf(be -> be.isRemoved() || be.getLevel() != mc.level);

		// candidatos ordenados por distancia
		List<MediaBlockEntity> candidatos = new ArrayList<>();
		for (MediaBlockEntity be : CARGADOS.values()) {
			double d = Math.sqrt(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(ojo));
			if (be.sonando() && d <= limite(be)) {
				candidatos.add(be);
			}
		}
		candidatos.sort(Comparator.comparingDouble(be -> net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(ojo)));
		Map<BlockPos, MediaBlockEntity> elegidos = new HashMap<>();
		int videos = 0;
		boolean listas = Herramientas.listas();
		for (MediaBlockEntity be : candidatos) {
			if (!listas || elegidos.size() >= MAX_SESIONES) {
				break;
			}
			boolean video = be.tipo().video() && videos < MAX_VIDEO;
			if (be.tipo().video() && !video) {
				continue;
			}
			if (video) {
				videos++;
			}
			elegidos.put(be.getBlockPos(), be);
		}

		// cerrar las que sobran o quedaron viejas
		Iterator<Map.Entry<BlockPos, Sesion>> it = SESIONES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Sesion> e = it.next();
			MediaBlockEntity be = elegidos.get(e.getKey());
			Pista pista = be == null ? null : be.actual();
			if (be == null || pista == null || e.getValue().version() != be.version() || !e.getValue().url().equals(pista.url())) {
				e.getValue().cerrar();
				it.remove();
			}
		}
		// abrir las nuevas
		for (MediaBlockEntity be : elegidos.values()) {
			if (!SESIONES.containsKey(be.getBlockPos()) && be.actual() != null) {
				Sesion s = new Sesion(be.getBlockPos(), be.version(), be.actual().url(), be.tipo().video(), be.actual().duracion(),
					be.transcurrido(), be.alcance(), () -> be.volumen() / 100.0F,
					be.tipo() == MediaBlock.Tipo.PANTALLA && be.ancho() >= 6);
				SESIONES.put(be.getBlockPos().immutable(), s);
				s.iniciar();
			}
		}

		tickPortatiles(mc);

		// notas musicales sobre las radios que suenan
		if (mc.level.getGameTime() % 15 == 0) {
			for (Map.Entry<BlockPos, Sesion> e : SESIONES.entrySet()) {
				MediaBlockEntity be = CARGADOS.get(e.getKey());
				if (be != null && be.tipo() == MediaBlock.Tipo.RADIO && e.getValue().estado() == Sesion.Estado.SONANDO) {
					BlockPos p = e.getKey();
					mc.level.addParticle(ParticleTypes.NOTE, p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5,
						mc.level.getRandom().nextInt(24) / 24.0, 0.0, 0.0);
				}
			}
		}
	}

	/** Mis audifonos (solo yo) y los parlantes de cualquiera que este cerca (incluido el mio). */
	private static void tickPortatiles(Minecraft mc) {
		Map<java.util.UUID, net.minecraft.world.entity.player.Player> deseados = new HashMap<>();
		List<net.minecraft.world.entity.player.Player> jugadores = new ArrayList<>(mc.level.players());
		Vec3 yo = mc.player.position();
		jugadores.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(yo)));
		boolean listas = Herramientas.listas();
		for (net.minecraft.world.entity.player.Player p : jugadores) {
			com.aventura.teleradio.media.Portatil st = p.getAttached(com.aventura.teleradio.media.Portatil.TIPO);
			if (!listas || st == null || !st.sonando() || st.salida() == com.aventura.teleradio.media.Portatil.NADA
				|| deseados.size() >= MAX_PORTATILES) {
				continue;
			}
			boolean mio = p == mc.player;
			boolean parlante = st.salida() == com.aventura.teleradio.media.Portatil.PARLANTE;
			if (mio || parlante && p.position().distanceTo(yo) <= st.alcance() + 8) {
				deseados.put(p.getUUID(), p);
			}
		}
		// cerrar las que ya no van o cambiaron
		Iterator<Map.Entry<java.util.UUID, Sesion>> it = PORTATILES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<java.util.UUID, Sesion> e = it.next();
			net.minecraft.world.entity.player.Player p = deseados.get(e.getKey());
			com.aventura.teleradio.media.Portatil st = p == null ? null : p.getAttached(com.aventura.teleradio.media.Portatil.TIPO);
			boolean auricular = st != null && st.salida() == com.aventura.teleradio.media.Portatil.AUDIFONOS;
			if (st == null || st.actual() == null || e.getValue().version() != st.version() || !e.getValue().url().equals(st.actual().url())
				|| e.getValue().auricular() != auricular) {
				e.getValue().cerrar();
				it.remove();
			}
		}
		for (net.minecraft.world.entity.player.Player p : deseados.values()) {
			if (PORTATILES.containsKey(p.getUUID())) {
				continue;
			}
			com.aventura.teleradio.media.Portatil st = p.getAttached(com.aventura.teleradio.media.Portatil.TIPO);
			boolean auricular = st.salida() == com.aventura.teleradio.media.Portatil.AUDIFONOS;
			net.minecraft.world.entity.player.Player dueno = p;
			Sesion s = new Sesion(p.blockPosition(), st.version(), st.actual().url(), false, st.actual().duracion(), st.transcurrido(),
				st.alcance(), () -> {
					com.aventura.teleradio.media.Portatil actual = dueno.getAttached(com.aventura.teleradio.media.Portatil.TIPO);
					return actual == null ? 0.0F : actual.volumen() / 100.0F;
				}, false, () -> dueno.position().add(0, 1.0, 0), auricular);
			PORTATILES.put(p.getUUID(), s);
			s.iniciar();
		}
		// notas alrededor de quien lleva un parlante sonando
		if (mc.level.getGameTime() % 12 == 0) {
			for (Map.Entry<java.util.UUID, Sesion> e : PORTATILES.entrySet()) {
				Sesion s = e.getValue();
				if (!s.auricular() && s.estado() == Sesion.Estado.SONANDO) {
					Vec3 pos = s.posicion();
					mc.level.addParticle(ParticleTypes.NOTE, pos.x + (mc.level.getRandom().nextDouble() - 0.5), pos.y + 1.2,
						pos.z + (mc.level.getRandom().nextDouble() - 0.5), mc.level.getRandom().nextInt(24) / 24.0, 0.0, 0.0);
				}
			}
		}
	}
}
