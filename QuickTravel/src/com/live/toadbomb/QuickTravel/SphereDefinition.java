package com.live.toadbomb.QuickTravel;

import org.bukkit.Location;

/**
 * Just contains the vital statistics of a sphere region
 *
 * @author Mumfrey
 */
public class SphereDefinition
{
	private final Location location;

	private final double radius;

	public SphereDefinition(Location location, double radius)
	{
		this.location = location;
		this.radius = radius;
	}
	
	/**
	 * @return the location
	 */
	public Location getLocation()
	{
		return location;
	}

	/**
	 * @return the radius
	 */
	public double getRadius()
	{
		return radius;
	}
}
