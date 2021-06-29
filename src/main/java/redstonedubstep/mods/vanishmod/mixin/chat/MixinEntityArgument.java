package redstonedubstep.mods.vanishmod.mixin.chat;

import java.util.List;
import java.util.stream.Collectors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import redstonedubstep.mods.vanishmod.VanishUtil;

@Mixin(EntityArgument.class)
public abstract class MixinEntityArgument {

	//Make non-admins not able to target vanished players through their name or a selector, admins shouldn't be affected
	@Redirect(method = "getPlayers", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
	private static boolean redirectIsEmpty(List<ServerPlayerEntity> list, CommandContext<CommandSource> context) {
		CommandSource source = context.getSource();
		List<ServerPlayerEntity> filteredList = list.stream().filter(p -> !VanishUtil.isVanished(p)).collect(Collectors.toList());

		return source.hasPermissionLevel(2) ? list.isEmpty() : filteredList.isEmpty();
	}
}