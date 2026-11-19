package com.aventura.teleradio.test;

import com.aventura.teleradio.TeleRadioMod;
import com.aventura.teleradio.client.ControlScreen;
import com.aventura.teleradio.client.Reproductor;
import com.aventura.teleradio.client.Sesion;
import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.Pista;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Prueba real: pone una pantalla gigante, un televisor y una radio reproduciendo un video de YouTube
 * y espera a que llegue imagen y sonido (necesita internet y las herramientas en -Dteleradio.bin).
 */
public class TeleRadioClientTests implements FabricClientGameTest {
	private static final String VIDEO = "https://www.youtube.com/watch?v=aqz-KE-bpKQ";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			int y = world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			BlockPos pantalla = new BlockPos(3, y, 7);
			BlockPos tv = new BlockPos(-5, y, 3);
			BlockPos radio = new BlockPos(5, y, 3);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				for (int x = -9; x <= 9; x++) {
					for (int dy = 0; dy < 7; dy++) {
						level.setBlockAndUpdate(new BlockPos(x, y + dy, 8), Blocks.STONE_BRICKS.defaultBlockState());
					}
				}
				level.setBlockAndUpdate(pantalla, TeleRadioMod.PANTALLA.defaultBlockState().setValue(MediaBlock.FACING, Direction.NORTH));
				level.setBlockAndUpdate(tv, TeleRadioMod.TELEVISOR.defaultBlockState().setValue(MediaBlock.FACING, Direction.NORTH));
				level.setBlockAndUpdate(radio, TeleRadioMod.RADIO.defaultBlockState().setValue(MediaBlock.FACING, Direction.NORTH));
				MediaBlockEntity p = (MediaBlockEntity) level.getBlockEntity(pantalla);
				p.setTamano(7, 4);
				p.reproducir(new Pista(VIDEO, "Big Buck Bunny", 635));
				p.saltar(60);
				MediaBlockEntity t = (MediaBlockEntity) level.getBlockEntity(tv);
				t.reproducir(new Pista(VIDEO, "Big Buck Bunny", 635));
				t.saltar(60);
				t.encolar(new Pista("https://www.youtube.com/watch?v=xyz", "Siguiente video de prueba", 212));
			});
			world.getServer().runCommand("tp @a 0.5 " + (y + 0.5) + " -6.5 0 5");
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("teleradio_cargando");

			// esperar imagen en la pantalla y en el televisor (hasta 60 s)
			context.waitFor(mc -> {
				Sesion sp = Reproductor.sesion(pantalla);
				Sesion st = Reproductor.sesion(tv);
				if (sp != null && sp.estado() == Sesion.Estado.ERROR) {
					throw new AssertionError("Error en la pantalla: " + sp.mensaje());
				}
				return sp != null && sp.hayCuadro() && st != null && st.hayCuadro();
			}, 1200);
			context.waitFor(mc -> {
				Sesion sp = Reproductor.sesion(pantalla);
				return sp != null && sp.estado() == Sesion.Estado.SONANDO;
			}, 600);
			context.waitTicks(100);
			// el motor de sonido de Minecraft de verdad esta leyendo el audio (no solo se inicio)
			double segundos = context.computeOnClient(mc -> Reproductor.sesion(pantalla).segundosEntregados());
			if (segundos < 6.0) {
				throw new AssertionError("El motor de sonido solo tomó " + segundos + " s de audio");
			}
			context.takeScreenshot("teleradio_video");

			// control de la pantalla
			context.setScreen(() -> new ControlScreen(tv));
			context.waitTicks(10);
			context.takeScreenshot("teleradio_control");
			context.setScreen(() -> null);

			// aviso de programas faltantes
			String antes = System.getProperty("teleradio.bin");
			System.setProperty("teleradio.bin", "D:/DevTools/no-existe");
			context.setScreen(() -> new ControlScreen(radio));
			context.waitTicks(5);
			context.takeScreenshot("teleradio_faltan_programas");
			context.setScreen(() -> null);
			System.setProperty("teleradio.bin", antes);
			context.waitTicks(5);

			// --- parlante portatil: apagar todo lo demas, llevar el parlante y reproducir
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				((MediaBlockEntity) level.getBlockEntity(pantalla)).detener();
				((MediaBlockEntity) level.getBlockEntity(tv)).detener();
				net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().add(new net.minecraft.world.item.ItemStack(TeleRadioMod.PARLANTE));
				com.aventura.teleradio.media.Portatil p = player.getAttachedOrCreate(com.aventura.teleradio.media.Portatil.TIPO);
				player.setAttached(com.aventura.teleradio.media.Portatil.TIPO, p.reproducir(new Pista(VIDEO, "Big Buck Bunny", 635)).saltar(90));
			});
			context.waitFor(mc -> {
				Sesion s = Reproductor.sesionPortatil(mc.player.getUUID());
				if (s != null && s.estado() == Sesion.Estado.ERROR) {
					throw new AssertionError("Error en el parlante: " + s.mensaje());
				}
				return s != null && s.estado() == Sesion.Estado.SONANDO && !s.auricular();
			}, 1200);
			// caminar: el sonido debe seguir al jugador
			world.getServer().runCommand("tp @a 30.5 " + (y + 0.5) + " -6.5");
			context.waitTicks(100);
			boolean sigue = context.computeOnClient(mc -> {
				Sesion s = Reproductor.sesionPortatil(mc.player.getUUID());
				return s != null && s.posicion().distanceTo(mc.player.position()) < 2.0 && s.segundosEntregados() > 6.0;
			});
			if (!sigue) {
				throw new AssertionError("El parlante no sigue al jugador o no suena");
			}
			context.setScreen(ControlScreen::portatil);
			context.waitTicks(10);
			context.takeScreenshot("teleradio_parlante");
			context.setScreen(() -> null);

			// --- audifonos: cambiar el parlante por audifonos puestos en la cabeza
			world.getServer().runOnServer(server -> {
				net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().clearContent();
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(TeleRadioMod.AUDIFONOS));
			});
			context.waitFor(mc -> {
				Sesion s = Reproductor.sesionPortatil(mc.player.getUUID());
				return s != null && s.auricular() && s.estado() == Sesion.Estado.SONANDO;
			}, 1200);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @a 30.5 " + (y + 0.5) + " -6.5 180 0");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("teleradio_audifonos_puestos");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		}
	}
}
