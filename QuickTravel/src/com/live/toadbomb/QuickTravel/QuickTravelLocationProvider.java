package com.live.toadbomb.QuickTravel;

import java.util.Collection;

import org.bukkit.Location;

/**
 * Interface for objects which provide locations
 *
 * @author Mumfrey
 */
public interface QuickTravelLocationProvider
{
	/**
	 * Get the number of stored locations
	 * 
	 * @return
	 */
	public abstract int getLocationCount();

	/**
	 * Get all the locations stored
	 * 
	 * @return
	 */
	public abstract Collection<QuickTravelLocation> getLocations();
	
	/**
	 * Get a location by name, returns null if no matching location was found
	 * 
	 * @param name Name of the location to search for
	 * @return
	 */
	public abstract QuickTravelLocation getLocationByName(String name);

	/**
	 * Gets a location using the specified coords
	 * 
	 * @param coords
	 * @return
	 */
	public abstract QuickTravelLocation getLocationAt(Location coords);
}
