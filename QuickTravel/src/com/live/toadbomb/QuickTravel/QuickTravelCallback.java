package com.live.toadbomb.QuickTravel;

import org.bukkit.entity.Player;

/**
 * Callback delegate interface for objects which need a callback when a qt teleport takes place
 * @author Mumfrey
 */
public interface QuickTravelCallback
{
	/**
	 * @param player
	 * @param cost
	 */
	public abstract void notifyPlayerTeleported(Player player, double cost);
}
