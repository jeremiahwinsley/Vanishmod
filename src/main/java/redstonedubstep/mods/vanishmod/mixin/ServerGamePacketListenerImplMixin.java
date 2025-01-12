package redstonedubstep.mods.vanishmod.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import redstonedubstep.mods.vanishmod.VanishConfig;
import redstonedubstep.mods.vanishmod.VanishUtil;
import redstonedubstep.mods.vanishmod.misc.FieldHolder;
import redstonedubstep.mods.vanishmod.misc.SoundSuppressionHelper;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1200) //Lower priority to ensure that the injectors of this mixin are run after other mixins in this class; particularly important for vanishmod$onFinishDisconnect, which needs to be the last method called
public class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;
	@Shadow
	@Final
	private MinecraftServer server;

	//Filter any packets that we wish to not send to players that cannot see vanished players, mainly consisting of player info and sound packets.
	//We don't filter player info removal packets, because this mod uses them to remove players after their status has changed to be vanished,
	//and it can be done safely because not suppressing these packets does not break this mod (in: a player removal packet sent too much wouldn't break this mod as much as a player addition packet)
	//We need to filter the item entity packets because otherwise all other clients think that they picked up an item (and thus show a pickup animation for the local player), while in reality a vanished player did
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void vanishmod$onSendPacket(Packet<?> packet, CallbackInfo callbackInfo) {
		if (packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket) {
			List<ClientboundPlayerInfoUpdatePacket.Entry> filteredPacketEntries = infoPacket.entries().stream().filter(e -> !VanishUtil.isVanished(server.getPlayerList().getPlayer(e.profileId()), player)).toList();

			if (filteredPacketEntries.isEmpty())
				callbackInfo.cancel();
			else if (!filteredPacketEntries.equals(infoPacket.entries()))
				infoPacket.entries = filteredPacketEntries;
		}
		else if (packet instanceof ClientboundTakeItemEntityPacket pickupPacket && VanishUtil.isVanished(player.level().getEntity(pickupPacket.getPlayerId()), player))
			callbackInfo.cancel();
		else if (VanishConfig.CONFIG.hidePlayersFromWorld.get()) {
			if (packet instanceof ClientboundSoundPacket soundPacket && SoundSuppressionHelper.shouldSuppressSoundEventFor(SoundSuppressionHelper.getPlayerForPacket(soundPacket), player.level(), soundPacket.getX(), soundPacket.getY(), soundPacket.getZ(), player))
				callbackInfo.cancel();
			else if (packet instanceof ClientboundSoundEntityPacket soundPacket && SoundSuppressionHelper.shouldSuppressSoundEventFor(SoundSuppressionHelper.getPlayerForPacket(soundPacket), player.level(), player.level().getEntity(soundPacket.getId()), player))
				callbackInfo.cancel();
			else if (packet instanceof ClientboundLevelEventPacket soundPacket && SoundSuppressionHelper.shouldSuppressSoundEventFor(SoundSuppressionHelper.getPlayerForPacket(soundPacket), player.level(), Vec3.atCenterOf(soundPacket.getPos()), player))
				callbackInfo.cancel();
			else if (packet instanceof ClientboundBlockEventPacket eventPacket && SoundSuppressionHelper.shouldSuppressSoundEventFor(null, player.level(), Vec3.atCenterOf(eventPacket.getPos()), player))
				callbackInfo.cancel();
			else if (packet instanceof ClientboundLevelParticlesPacket particlesPacket && SoundSuppressionHelper.shouldSuppressParticlesFor(null, player.level(), particlesPacket.getX(), particlesPacket.getY(), particlesPacket.getZ(), player))
				callbackInfo.cancel();
		}
	}

	//Prevents vanilla join, leave, death and advancement messages of vanished players from being broadcast. Also removes all translation component messages (except for /msg messages) with vanished player references when relevant config is enabled
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
	private void vanishmod$onSendPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo callbackInfo) {
		if (packet instanceof ClientboundSystemChatPacket chatPacket && chatPacket.content() instanceof MutableComponent component && component.getContents() instanceof TranslatableContents content) {
			List<ServerPlayer> vanishedPlayers = new ArrayList<>(ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream().filter(p -> VanishUtil.isVanished(p, player)).toList());
			boolean joiningPlayerVanished = VanishUtil.isVanished(FieldHolder.joiningPlayer, player);

			if (joiningPlayerVanished)
				vanishedPlayers.add(FieldHolder.joiningPlayer);

			if (VanishUtil.isVanished(FieldHolder.leavingPlayer, player))
				vanishedPlayers.add(FieldHolder.leavingPlayer);

			if (content.getKey().startsWith("multiplayer.player.joined") && joiningPlayerVanished)
				callbackInfo.cancel();
			else if (content.getKey().startsWith("multiplayer.player.left") || content.getKey().startsWith("death.") || content.getKey().startsWith("chat.type.advancement")) {
				if (content.getArgs()[0] instanceof Component playerName) {
					for (ServerPlayer sender : vanishedPlayers) {
						if (sender.getDisplayName().getString().equals(playerName.getString()))
							callbackInfo.cancel();
					}
				}
			}
			else if (!content.getKey().startsWith("commands.message.display.incoming") && VanishConfig.CONFIG.removeModdedSystemMessageReferences.get()) {
				for (Object arg : content.getArgs()) {
					if (arg instanceof Component componentArg) {
						String potentialPlayerName = componentArg.getString();

						for (ServerPlayer vanishedPlayer : vanishedPlayers) {
							if (vanishedPlayer.getDisplayName().getString().equals(potentialPlayerName)) {
								callbackInfo.cancel();
								return;
							}
						}
					}
				}
			}
		}
	}

	//Stores the player that is about to leave the server and get removed from the regular player list
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void vanishmod$onStartDisconnect(Component reason, CallbackInfo callbackInfo) {
		FieldHolder.leavingPlayer = player;
	}

	//Removes the stored player after it has fully left the server
	@Inject(method = "onDisconnect", at = @At("TAIL"))
	private void vanishmod$onFinishDisconnect(Component reason, CallbackInfo callbackInfo) {
		FieldHolder.leavingPlayer = null;
	}
}
