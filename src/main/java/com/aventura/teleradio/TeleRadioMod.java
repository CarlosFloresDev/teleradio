package com.aventura.teleradio;

import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.Pista;
import com.aventura.teleradio.media.Portatil;
import com.aventura.teleradio.net.ControlPayload;
import com.aventura.teleradio.net.PortatilPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TeleRadio: radios, televisores y pantallas gigantes que reproducen YouTube, radios online y archivos de audio/video,
 * sincronizados para todos los jugadores cercanos.
 */
public class TeleRadioMod implements ModInitializer {
	public static final String MOD_ID = "teleradio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final MediaBlock RADIO = bloque("radio", MediaBlock.Tipo.RADIO, SoundType.WOOD, MapColor.WOOD);
	public static final MediaBlock TELEVISOR = bloque("televisor", MediaBlock.Tipo.TELEVISOR, SoundType.WOOD, MapColor.COLOR_BROWN);
	public static final MediaBlock PANTALLA = bloque("pantalla", MediaBlock.Tipo.PANTALLA, SoundType.METAL, MapColor.COLOR_BLACK);
	public static final BlockEntityType<MediaBlockEntity> MEDIA = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("media"),
		FabricBlockEntityTypeBuilder.create(MediaBlockEntity::new, RADIO, TELEVISOR, PANTALLA).build());

	public static final Item RADIO_ITEM = itemDeBloque(RADIO);
	public static final Item TELEVISOR_ITEM = itemDeBloque(TELEVISOR);
	public static final Item PANTALLA_ITEM = itemDeBloque(PANTALLA);
	public static final Item CONTROL_REMOTO = item("control_remoto", ControlRemotoItem::new, new Item.Properties().stacksTo(1));
	public static final Item AUDIFONOS = item("audifonos", p -> new PortatilItem(Portatil.AUDIFONOS, p), new Item.Properties().stacksTo(1)
		.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable
			.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
			.setAsset(ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, id("audifonos")))
			.setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER).build()));
	public static final Item PARLANTE = item("parlante_portatil", p -> new PortatilItem(Portatil.PARLANTE, p), new Item.Properties().stacksTo(1));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static MediaBlock bloque(String nombre, MediaBlock.Tipo tipo, SoundType sonido, MapColor color) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(nombre));
		MediaBlock block = new MediaBlock(tipo, BlockBehaviour.Properties.of().setId(key).mapColor(color).sound(sonido)
			.strength(1.5F).noOcclusion().lightLevel(s -> tipo == MediaBlock.Tipo.RADIO ? 0 : 3));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static Item itemDeBloque(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static Item item(String nombre, Function<Item.Properties, Item> fabrica, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(nombre));
		return Registry.register(BuiltInRegistries.ITEM, key, fabrica.apply(props.setId(key)));
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("teleradio"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.teleradio"))
			.icon(() -> new ItemStack(TELEVISOR_ITEM))
			.displayItems((params, out) -> {
				out.accept(RADIO_ITEM);
				out.accept(TELEVISOR_ITEM);
				out.accept(PANTALLA_ITEM);
				out.accept(CONTROL_REMOTO);
				out.accept(AUDIFONOS);
				out.accept(PARLANTE);
			}).build());

		PayloadTypeRegistry.serverboundPlay().register(ControlPayload.TYPE, ControlPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE, (payload, context) -> control(context.player(), payload));
		// audifonos y parlante portatil
		Portatil.TIPO.toString();
		PayloadTypeRegistry.serverboundPlay().register(PortatilPayload.TYPE, PortatilPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PortatilPayload.TYPE, (payload, context) -> portatil(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					actualizarSalida(player);
				}
			}
		});
		LOGGER.info("TeleRadio cargado");
	}

	private static void control(ServerPlayer player, ControlPayload p) {
		BlockPos pos = p.pos();
		// con el control remoto se puede manejar desde lejos, pero no desde cualquier parte del mundo
		if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 48 * 48 || !(player.level().getBlockEntity(pos) instanceof MediaBlockEntity media)) {
			return;
		}
		boolean conPista = p.accion() == ControlPayload.REPRODUCIR || p.accion() == ControlPayload.ENCOLAR;
		if (conPista && !linkValido(p.url())) {
			return;
		}
		switch (p.accion()) {
			case ControlPayload.REPRODUCIR -> media.reproducir(pista(p));
			case ControlPayload.ENCOLAR -> media.encolar(pista(p));
			case ControlPayload.PAUSA -> media.pausa();
			case ControlPayload.SIGUIENTE -> media.siguiente();
			case ControlPayload.DETENER -> media.detener();
			case ControlPayload.VOLUMEN -> media.setVolumen(p.valor());
			case ControlPayload.ALCANCE -> media.setAlcance(p.valor());
			case ControlPayload.TAMANO -> {
				if (media.tipo() == MediaBlock.Tipo.PANTALLA) {
					media.setTamano(p.valor() / 100, p.valor() % 100);
				}
			}
			case ControlPayload.REPETIR -> media.alternarRepetir();
			case ControlPayload.QUITAR -> media.quitarDeCola(p.valor());
			case ControlPayload.SALTAR -> media.saltar(Math.max(-600, Math.min(600, p.valor())));
			default -> {
			}
		}
	}

	/** Decide por donde suena lo portatil (parlante, audifonos o nada) segun lo que lleva el jugador; y avanza la cola. */
	public static void actualizarSalida(ServerPlayer player) {
		Portatil p = player.getAttached(Portatil.TIPO);
		if (p == null) {
			return;
		}
		boolean parlante = lleva(player, PARLANTE);
		boolean audifonos = lleva(player, AUDIFONOS);
		int salida = parlante && audifonos ? p.preferida() : parlante ? Portatil.PARLANTE : audifonos ? Portatil.AUDIFONOS : Portatil.NADA;
		Portatil nuevo = p.avanzar();
		if (salida != nuevo.salida()) {
			nuevo = nuevo.conSalida(salida, nuevo.preferida());
		}
		if (nuevo != p) {
			player.setAttached(Portatil.TIPO, nuevo);
		}
	}

	private static boolean lleva(ServerPlayer player, Item item) {
		if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(item)
			|| player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).is(item)) {
			return true;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	private static void portatil(ServerPlayer player, PortatilPayload p) {
		boolean conPista = p.accion() == ControlPayload.REPRODUCIR || p.accion() == ControlPayload.ENCOLAR;
		if (conPista && !linkValido(p.url())) {
			return;
		}
		Portatil actual = player.getAttachedOrCreate(Portatil.TIPO);
		Pista pista = null;
		if (conPista) {
			String titulo = p.titulo().isBlank() ? p.url() : p.titulo();
			pista = new Pista(p.url(), titulo.length() > 120 ? titulo.substring(0, 120) : titulo, Math.max(0, p.duracion()));
		}
		Portatil nuevo = switch (p.accion()) {
			case ControlPayload.REPRODUCIR -> actual.reproducir(pista);
			case ControlPayload.ENCOLAR -> actual.encolar(pista);
			case ControlPayload.PAUSA -> actual.pausa();
			case ControlPayload.SIGUIENTE -> actual.siguiente();
			case ControlPayload.DETENER -> actual.detener();
			case ControlPayload.VOLUMEN -> actual.conVolumen(p.valor());
			case ControlPayload.ALCANCE -> actual.conAlcance(p.valor());
			case ControlPayload.REPETIR -> actual.alternarRepetir();
			case ControlPayload.QUITAR -> actual.quitar(p.valor());
			case ControlPayload.SALTAR -> actual.saltar(Math.max(-600, Math.min(600, p.valor())));
			default -> actual;
		};
		if (nuevo != actual) {
			player.setAttached(Portatil.TIPO, nuevo);
			actualizarSalida(player);
		}
	}

	/**
	 * Solo links web (http/https) sin espacios: los demas jugadores los abren con yt-dlp/ffmpeg en su PC,
	 * asi que nunca se aceptan archivos locales ni otros protocolos.
	 */
	public static boolean linkValido(String url) {
		String u = url.trim().toLowerCase(java.util.Locale.ROOT);
		return (u.startsWith("https://") || u.startsWith("http://")) && !u.contains(" ") && url.length() <= 2048;
	}

	private static Pista pista(ControlPayload p) {
		String titulo = p.titulo().isBlank() ? p.url() : p.titulo();
		return new Pista(p.url(), titulo.length() > 120 ? titulo.substring(0, 120) : titulo, Math.max(0, p.duracion()));
	}
}
