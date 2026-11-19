package com.aventura.teleradio.test;

import com.aventura.teleradio.TeleRadioMod;
import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.Pista;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

public class TeleRadioGameTests {
	private static MediaBlockEntity colocar(GameTestHelper helper, MediaBlock bloque, BlockPos rel) {
		helper.setBlock(rel, bloque.defaultBlockState().setValue(MediaBlock.FACING, Direction.NORTH));
		return (MediaBlockEntity) helper.getBlockEntity(rel, MediaBlockEntity.class);
	}

	/** Reproducir, cola, pausa conserva el segundo, saltar, siguiente y detener. */
	@GameTest
	public void controles(GameTestHelper helper) {
		MediaBlockEntity radio = colocar(helper, TeleRadioMod.RADIO, new BlockPos(1, 1, 1));
		Pista a = new Pista("https://www.youtube.com/watch?v=aaa", "Canción A", 200);
		Pista b = new Pista("https://www.youtube.com/watch?v=bbb", "Canción B", 180);
		helper.assertTrue(radio.encolar(a), "Encolar con la radio apagada la pone a sonar");
		helper.assertTrue(radio.actual() == a && radio.sonando(), "Suena A");
		radio.encolar(b);
		helper.assertTrue(radio.cola().size() == 1, "B queda en la cola");
		radio.saltar(60);
		helper.assertTrue(radio.transcurrido() >= 59000, "Saltar +60 s");
		radio.pausa();
		long enPausa = radio.transcurrido();
		helper.assertTrue(radio.enPausa() && !radio.sonando(), "En pausa");
		radio.pausa();
		helper.assertTrue(Math.abs(radio.transcurrido() - enPausa) < 1000, "Al seguir, continua desde el mismo segundo");
		int version = radio.version();
		radio.siguiente();
		helper.assertTrue(radio.actual() == b && radio.cola().isEmpty() && radio.version() > version, "Siguiente pasa a B");
		radio.detener();
		helper.assertTrue(radio.actual() == null, "Detener apaga todo");
		helper.succeed();
	}

	/** Limites: volumen 0-100, alcance 4-64, pantalla hasta 16x9. */
	@GameTest
	public void limites(GameTestHelper helper) {
		MediaBlockEntity pantalla = colocar(helper, TeleRadioMod.PANTALLA, new BlockPos(1, 1, 1));
		pantalla.setTamano(40, 30);
		helper.assertTrue(pantalla.ancho() == 16 && pantalla.alto() == 9, "Pantalla máxima 16x9");
		pantalla.setTamano(0, -3);
		helper.assertTrue(pantalla.ancho() == 1 && pantalla.alto() == 1, "Pantalla mínima 1x1");
		pantalla.setVolumen(500);
		pantalla.setAlcance(1);
		helper.assertTrue(pantalla.volumen() == 100 && pantalla.alcance() == MediaBlockEntity.MIN_ALCANCE, "Volumen y alcance limitados");
		helper.assertTrue(TeleRadioMod.linkValido("https://www.youtube.com/watch?v=x"), "Link de YouTube válido");
		helper.assertTrue(!TeleRadioMod.linkValido("file:///C:/Windows/win.ini"), "No se aceptan archivos locales");
		helper.assertTrue(!TeleRadioMod.linkValido("--exec calc"), "No se aceptan opciones");
		helper.succeed();
	}

	/** Al terminar la pista (segun su duracion) pasa sola a la siguiente. */
	@GameTest
	public void avanzaSolo(GameTestHelper helper) throws InterruptedException {
		MediaBlockEntity tv = colocar(helper, TeleRadioMod.TELEVISOR, new BlockPos(1, 1, 1));
		tv.reproducir(new Pista("https://example.com/corta.mp3", "Corta", 1));
		tv.encolar(new Pista("https://example.com/larga.mp3", "Larga", 300));
		MediaBlockEntity.tickServidor(helper.getLevel(), tv.getBlockPos(), tv.getBlockState(), tv);
		helper.assertTrue(tv.actual().titulo().equals("Corta"), "Todavía suena la corta");
		// el tiempo de las pistas es real (reloj), no ticks del servidor
		Thread.sleep(2700);
		MediaBlockEntity.tickServidor(helper.getLevel(), tv.getBlockPos(), tv.getBlockState(), tv);
		helper.assertTrue(tv.actual() != null && tv.actual().titulo().equals("Larga"), "Debe pasar a la siguiente");
		helper.succeed();
	}

	/** Lo que se manda al cliente conserva la pista, la cola y el segundo en que va. */
	@GameTest
	public void sincronizacion(GameTestHelper helper) {
		MediaBlockEntity tv = colocar(helper, TeleRadioMod.TELEVISOR, new BlockPos(1, 1, 1));
		tv.reproducir(new Pista("https://www.youtube.com/watch?v=aqz-KE-bpKQ", "Big Buck Bunny", 635));
		tv.saltar(120);
		tv.encolar(new Pista("https://example.com/b.mp3", "B", 10));
		CompoundTag tag = tv.getUpdateTag(helper.getLevel().registryAccess());
		MediaBlockEntity copia = new MediaBlockEntity(tv.getBlockPos(), tv.getBlockState());
		copia.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		helper.assertTrue(copia.actual() != null && copia.actual().titulo().equals("Big Buck Bunny"), "Pista sincronizada");
		helper.assertTrue(copia.cola().size() == 1, "Cola sincronizada");
		helper.assertTrue(Math.abs(copia.transcurrido() - tv.transcurrido()) < 1000, "Mismo segundo");
		helper.succeed();
	}

	/** Audifonos / parlante: suena por lo que lleves en el inventario (o puesto) y la cola funciona igual. */
	@GameTest
	public void portatil(GameTestHelper helper) {
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "melomano"), false);
		var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
			cookie.clientInformation());
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		var tipo = com.aventura.teleradio.media.Portatil.TIPO;
		player.setAttached(tipo, player.getAttachedOrCreate(tipo).reproducir(new Pista("https://example.com/a.mp3", "A", 100))
			.encolar(new Pista("https://example.com/b.mp3", "B", 100)));
		TeleRadioMod.actualizarSalida(player);
		helper.assertTrue(player.getAttached(tipo).salida() == com.aventura.teleradio.media.Portatil.NADA, "Sin aparato no suena");
		player.getInventory().add(new net.minecraft.world.item.ItemStack(TeleRadioMod.PARLANTE));
		TeleRadioMod.actualizarSalida(player);
		helper.assertTrue(player.getAttached(tipo).salida() == com.aventura.teleradio.media.Portatil.PARLANTE, "Con parlante en el inventario");
		player.getInventory().clearContent();
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(TeleRadioMod.AUDIFONOS));
		TeleRadioMod.actualizarSalida(player);
		helper.assertTrue(player.getAttached(tipo).salida() == com.aventura.teleradio.media.Portatil.AUDIFONOS, "Con audífonos puestos");
		var p = player.getAttached(tipo).siguiente();
		helper.assertTrue(p.actual().titulo().equals("B") && p.cola().isEmpty(), "Siguiente en lo portátil");
		helper.succeed();
	}
}
