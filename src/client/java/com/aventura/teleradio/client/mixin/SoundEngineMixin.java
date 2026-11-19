package com.aventura.teleradio.client.mixin;

import com.aventura.teleradio.client.SonidoStream;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Los sonidos de TeleRadio no vienen de un archivo .ogg: se les entrega el audio que decodifica ffmpeg. */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@WrapOperation(method = "play", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/Identifier;Z)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<AudioStream> teleradio$flujoPropio(SoundBufferLibrary biblioteca, Identifier location, boolean looping,
		Operation<CompletableFuture<AudioStream>> original, @Local(argsOnly = true) SoundInstance instance) {
		if (instance instanceof SonidoStream sonido) {
			return CompletableFuture.completedFuture(sonido.flujo());
		}
		return original.call(biblioteca, location, looping);
	}
}
