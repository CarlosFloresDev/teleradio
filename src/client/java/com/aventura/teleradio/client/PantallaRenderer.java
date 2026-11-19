package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import com.aventura.teleradio.media.MediaBlock;
import com.aventura.teleradio.media.MediaBlockEntity;
import com.aventura.teleradio.media.Pista;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dibuja la imagen del televisor y de la pantalla gigante. Coordenadas en el espacio del modelo mirando al norte
 * (frente en z = 0); visto de frente, +x queda a la izquierda. La pantalla gigante crece hacia la derecha y hacia arriba.
 */
public class PantallaRenderer implements BlockEntityRenderer<MediaBlockEntity, PantallaRenderer.Estado> {
	private static final Identifier APAGADA = TeleRadioMod.id("textures/entity/pantalla_apagada.png");
	private static final Identifier MARCO = TeleRadioMod.id("textures/entity/marco.png");

	public PantallaRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static class Estado extends BlockEntityRenderState {
		MediaBlock.Tipo tipo = MediaBlock.Tipo.RADIO;
		Direction facing = Direction.NORTH;
		int ancho = 1;
		int alto = 1;
		@Nullable Identifier video;
		float aspecto = 16 / 9.0F;
		final List<FormattedCharSequence> lineas = new ArrayList<>();
		final List<Integer> anchos = new ArrayList<>();
		final List<Integer> colores = new ArrayList<>();
	}

	@Override
	public Estado createRenderState() {
		return new Estado();
	}

	@Override
	public void extractRenderState(MediaBlockEntity be, Estado state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.tipo = be.tipo();
		state.facing = be.getBlockState().getValue(MediaBlock.FACING);
		state.ancho = be.tipo() == MediaBlock.Tipo.PANTALLA ? be.ancho() : 1;
		state.alto = be.tipo() == MediaBlock.Tipo.PANTALLA ? be.alto() : 1;
		state.video = null;
		state.lineas.clear();
		state.anchos.clear();
		state.colores.clear();
		if (state.tipo == MediaBlock.Tipo.RADIO) {
			return;
		}
		Sesion sesion = Reproductor.sesion(be.getBlockPos());
		if (sesion != null) {
			sesion.actualizarVideo();
			if (sesion.hayCuadro()) {
				state.video = sesion.textura();
				state.aspecto = sesion.aspecto();
				return;
			}
		}
		// sin imagen: mensaje en pantalla
		Pista pista = be.actual();
		if (pista == null) {
			this.linea(state, "TeleRadio", 0xFF4FC3F7);
			this.linea(state, "Click derecho: elegir", 0xFFB0BEC5);
			this.linea(state, "video o música", 0xFFB0BEC5);
		} else if (!Herramientas.listas()) {
			this.linea(state, "Faltan programas", 0xFFFFB74D);
			this.linea(state, "Abre el control", 0xFFB0BEC5);
			this.linea(state, "(click derecho)", 0xFFB0BEC5);
		} else if (be.enPausa()) {
			this.linea(state, "❚❚ En pausa", 0xFFFFFFFF);
			this.linea(state, pista.titulo(), 0xFFB0BEC5);
		} else if (sesion == null) {
			this.linea(state, "Acércate para ver", 0xFFB0BEC5);
		} else {
			switch (sesion.estado()) {
				case BUSCANDO, CARGANDO -> {
					long puntos = System.currentTimeMillis() / 400 % 4;
					this.linea(state, "Cargando" + ".".repeat((int) puntos), 0xFFFFFFFF);
					this.linea(state, pista.titulo(), 0xFFB0BEC5);
				}
				case ERROR -> {
					this.linea(state, "No se pudo reproducir", 0xFFEF5350);
					this.linea(state, sesion.mensaje(), 0xFFB0BEC5);
				}
				case SONANDO -> {
					this.linea(state, "♪ Solo audio ♪", 0xFF81C784);
					this.linea(state, pista.titulo(), 0xFFB0BEC5);
				}
				case TERMINADA -> this.linea(state, "Fin", 0xFFB0BEC5);
			}
		}
	}

	private void linea(Estado state, String texto, int color) {
		Font font = Minecraft.getInstance().font;
		String corto = font.plainSubstrByWidth(texto, 120);
		if (!corto.equals(texto)) {
			corto = font.plainSubstrByWidth(texto, 114) + "…";
		}
		FormattedCharSequence seq = Component.literal(corto).getVisualOrderText();
		state.lineas.add(seq);
		state.anchos.add(font.width(seq));
		state.colores.add(color);
	}

	/** Rectangulo de la imagen en el espacio del modelo: {x0, y0, x1, y1, z}. */
	private static float[] rectangulo(Estado s) {
		if (s.tipo == MediaBlock.Tipo.TELEVISOR) {
			return new float[] {4 / 16F, 2 / 16F, 14 / 16F, 11 / 16F, 2 / 16F - 0.003F};
		}
		return new float[] {1 - s.ancho, 0, 1, s.alto, 14 / 16F - 0.004F};
	}

	@Override
	public void submit(Estado s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (s.tipo == MediaBlock.Tipo.RADIO) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, -((s.facing.toYRot() + 180.0F) % 360.0F));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		float[] r = rectangulo(s);
		float x0 = r[0];
		float y0 = r[1];
		float x1 = r[2];
		float y1 = r[3];
		float z = r[4];
		if (s.tipo == MediaBlock.Tipo.PANTALLA) {
			// marco negro y parte de atras (la pantalla puede ser mas grande que el bloque)
			float b = 1 / 16F;
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(MARCO), (pose, vc) -> {
				quad(vc, pose, x0 - b, y0, x1 + b, y1 + b, z + 0.002F, 0, 0, 1, 1, s.lightCoords, false);
				quad(vc, pose, x0 - b, y0, x1 + b, y1 + b, 1.001F, 0, 0, 1, 1, s.lightCoords, true);
			});
		}
		Identifier video = s.video;
		if (video != null) {
			// encajar la imagen 16:9 sin deformarla (franjas negras si hace falta)
			float w = x1 - x0;
			float h = y1 - y0;
			float u0 = 0;
			float u1 = 1;
			float vx0 = x0;
			float vx1 = x1;
			float vy0 = y0;
			float vy1 = y1;
			if (s.tipo == MediaBlock.Tipo.TELEVISOR) {
				// tele antigua 10:9: se recorta a los costados
				float visible = (w / h) / s.aspecto;
				u0 = 0.5F - visible / 2;
				u1 = 0.5F + visible / 2;
			} else if (w / h > s.aspecto) {
				float nw = h * s.aspecto;
				vx0 = x0 + (w - nw) / 2;
				vx1 = vx0 + nw;
			} else {
				float nh = w / s.aspecto;
				vy0 = y0 + (h - nh) / 2;
				vy1 = vy0 + nh;
			}
			float fu0 = u0;
			float fu1 = u1;
			float fx0 = vx0;
			float fx1 = vx1;
			float fy0 = vy0;
			float fy1 = vy1;
			if (s.tipo == MediaBlock.Tipo.PANTALLA) {
				collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(APAGADA),
					(pose, vc) -> quad(vc, pose, x0, y0, x1, y1, z + 0.001F, 0, 0, 0.01F, 0.01F, LightCoordsUtil.FULL_BRIGHT, false));
			}
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(video),
				(pose, vc) -> quad(vc, pose, fx0, fy0, fx1, fy1, z, fu0, 0, fu1, 1, LightCoordsUtil.FULL_BRIGHT, false));
		} else {
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(APAGADA),
				(pose, vc) -> quad(vc, pose, x0, y0, x1, y1, z, 0, 0, 1, 1, s.lightCoords, false));
			this.texto(s, poseStack, collector, x0, y0, x1, y1, z);
		}
		poseStack.popPose();
	}

	private void texto(Estado s, PoseStack poseStack, SubmitNodeCollector collector, float x0, float y0, float x1, float y1, float z) {
		if (s.lineas.isEmpty()) {
			return;
		}
		float w = x1 - x0;
		float h = y1 - y0;
		// escala para que entren ~130 px de texto a lo ancho y todas las lineas a lo alto
		float escala = Math.min(w / 130.0F, h / (s.lineas.size() * 11.0F + 6));
		poseStack.pushPose();
		poseStack.translate((x0 + x1) / 2, (y0 + y1) / 2, z - 0.002F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(escala, -escala, escala);
		float y = -s.lineas.size() * 11 / 2.0F;
		for (int i = 0; i < s.lineas.size(); i++) {
			collector.submitText(poseStack, -s.anchos.get(i) / 2.0F, y, s.lineas.get(i), false, Font.DisplayMode.POLYGON_OFFSET,
				LightCoordsUtil.FULL_BRIGHT, s.colores.get(i), 0, 0);
			y += 11;
		}
		poseStack.popPose();
	}

	/**
	 * Cuadrilatero en el plano z, visible desde el frente (-z). Visto de frente la izquierda es x1.
	 * {@code atras} lo hace visible desde +z (parte trasera).
	 */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
		float u0, float v0, float u1, float v1, int luz, boolean atras) {
		if (!atras) {
			vertice(vc, pose, x1, y0, z, u0, v1, luz, -1);
			vertice(vc, pose, x0, y0, z, u1, v1, luz, -1);
			vertice(vc, pose, x0, y1, z, u1, v0, luz, -1);
			vertice(vc, pose, x1, y1, z, u0, v0, luz, -1);
		} else {
			vertice(vc, pose, x0, y0, z, u0, v1, luz, 1);
			vertice(vc, pose, x1, y0, z, u1, v1, luz, 1);
			vertice(vc, pose, x1, y1, z, u1, v0, luz, 1);
			vertice(vc, pose, x0, y1, z, u0, v0, luz, 1);
		}
	}

	private static void vertice(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int luz, int normalZ) {
		vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(luz).setNormal(pose, 0, 0, normalZ);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
