package com.live.toadbomb.QuickTravel;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;

/**
 * FX to play on departure
 * 
 * @author Mumfrey
 */
public class QuickTravelFXDeparture extends QuickTravelFX
{
	public QuickTravelFXDeparture(int radius)
	{
		super(radius);
	}
	
	@Override
	public void playPreTeleportEffect(Location location, int ticksRemaining)
	{
		super.playPreTeleportEffect(location, ticksRemaining);
		location.getWorld().playEffect(location, Effect.ENDER_SIGNAL, null, radius);
		location.getWorld().playSound(location, Sound.ENDERMAN_TELEPORT, 1, 1);
	}
}
