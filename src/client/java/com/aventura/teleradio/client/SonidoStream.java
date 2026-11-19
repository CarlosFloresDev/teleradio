package com.aventura.teleradio.client;

import com.aventura.teleradio.TeleRadioMod;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Sonido de Minecraft que sale del bloque y lee el audio de {@link FlujoAudio}
 * (el {@code SoundEngineMixin} le entrega el flujo). Usa el volumen de "Tocadiscos/Bloques musicales".
 */
public class SonidoStream extends AbstractTickableSoundInstance {
	private static final SoundEvent EVENTO = SoundEvent.createVariableRangeEvent(TeleRadioMod.id("transmision"));

	private final Sesion sesion;
	private final FlujoAudio flujo;
	private final int alcance;

	public SonidoStream(Sesion sesion, FlujoAudio flujo, int alcance) {
		super(EVENTO, SoundSource.RECORDS, RandomSource.create());
		this.sesion = sesion;
		this.flujo = flujo;
		this.alcance = alcance;
		this.looping = false;
		this.volume = sesion.volumen();
		if (sesion.auricular()) {
			// audifonos: suena dentro de la cabeza, igual en todas partes
			this.relative = true;
			this.attenuation = Attenuation.NONE;
		} else {
			this.attenuation = Attenuation.LINEAR;
			this.mover();
		}
	}

	private void mover() {
		net.minecraft.world.phys.Vec3 p = this.sesion.posicion();
		this.x = p.x;
		this.y = p.y;
		this.z = p.z;
	}

	public FlujoAudio flujo() {
		return this.flujo;
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
		// sonido "en streaming" propio: no viene de un .ogg, el audio lo pone el mixin
		this.sound = new Sound(EVENTO.location(), ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, this.alcance);
		this.soundEvent = new WeighedSoundEvents(EVENTO.location(), null);
		return this.soundEvent;
	}

	@Override
	public void tick() {
		if (this.sesion.cerrada()) {
			this.stop();
		} else {
			this.volume = this.sesion.volumen();
			if (!this.sesion.auricular()) {
				this.mover();
			}
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
