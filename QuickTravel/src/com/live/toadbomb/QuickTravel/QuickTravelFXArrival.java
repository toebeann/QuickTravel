package com.live.toadbomb.QuickTravel;

import org.bukkit.Effect;
import org.bukkit.Location;

/**
 * FX to play on arrival
 * 
 * @author Mumfrey
 */
public class QuickTravelFXArrival extends QuickTravelFX
{
	public QuickTravelFXArrival(int radius)
	{
		super(radius);
	}

	@Override
	public void playTeleportEffect(Location location, int ticksRemaining)
	{
		super.playTeleportEffect(location, ticksRemaining);
		location.getWorld().playEffect(location, Effect.GHAST_SHOOT, null, radius);
	}
}
