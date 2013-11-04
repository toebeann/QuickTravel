package com.live.toadbomb.QuickTravel;

import org.bukkit.Effect;
import org.bukkit.Location;

/**
 * Abstract base class for teleport effects 
 * 
 */
public abstract class QuickTravelFX
{
	/**
	 * Effect visibility radius
	 */
	protected int radius;
	
	/**
	 * @param radius Radius the effects will be visible from
	 */
	public QuickTravelFX(int radius)
	{
		this.radius = radius;
	}
	
	/**
	 * @param location
	 * @param ticksRemaining TODO
	 */
	public void playPreTeleportEffect(Location location, int ticksRemaining)
	{
		// stub for subclasses
	}
	
	/**
	 * @param location
	 * @param ticksRemaining TODO
	 * @param radius
	 */
	public void playTeleportEffect(Location location, int ticksRemaining)
	{
		location.getWorld().playEffect(location, Effect.ENDER_SIGNAL, null, radius);
		location.getWorld().playEffect(location.clone().add( 1, 0,  0), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add( 1, 0,  1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add( 0, 0,  1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add(-1, 0,  1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add(-1, 0,  0), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add(-1, 0, -1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add( 0, 0, -1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location.clone().add( 1, 0, -1), Effect.SMOKE, 4, radius);
		location.getWorld().playEffect(location, Effect.EXTINGUISH, null, radius);
	}
}
