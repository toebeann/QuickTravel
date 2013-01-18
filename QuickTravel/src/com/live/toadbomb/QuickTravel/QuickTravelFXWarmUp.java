package com.live.toadbomb.QuickTravel;

import java.util.Random;

import org.bukkit.Effect;
import org.bukkit.Location;

/**
 * FX to play while in the quicktravel warm-up period
 * 
 * @author Mumfrey
 */
public class QuickTravelFXWarmUp extends QuickTravelFX
{
	private static Random rng = new Random();
	
	public QuickTravelFXWarmUp(int radius)
	{
		super(radius);
	}

	@Override
	public void playTeleportEffect(Location location, int ticksRemaining)
	{
		// Play the ender teleport effect for the last second before telporting
		if (ticksRemaining < 20)
		{
			int yOffset = rng.nextInt(3) - 1;
			location.getWorld().playEffect(location.clone().add(0, yOffset, 0), Effect.ENDER_SIGNAL, null, radius);
		}
		else
		{
			int xOffset = rng.nextInt(3) - 1;
			int zOffset = rng.nextInt(3) - 1;
			
			int direction = rng.nextInt(9);
			
			if (direction == 0) location.getWorld().playEffect(location, Effect.SMOKE, 4, radius);
			location.getWorld().playEffect(location.clone().add(xOffset, 0, zOffset), Effect.SMOKE, direction, radius);
		}
	}
}