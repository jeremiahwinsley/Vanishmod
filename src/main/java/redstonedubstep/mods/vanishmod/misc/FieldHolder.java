package redstonedubstep.mods.vanishmod.misc;

import net.minecraft.server.level.ServerPlayer;

public class FieldHolder {
	//The player that is currently in the process of joining the server. Required due to new players not being added to the regular player list when their join message is sent
	public static ServerPlayer joiningPlayer;
	//The player that is currently in the process of leaving the server. Required due to leaving players getting removed from the regular player list too early in some occasions
	public static ServerPlayer leavingPlayer;
}
