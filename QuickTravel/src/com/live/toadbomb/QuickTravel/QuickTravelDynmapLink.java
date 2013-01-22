package com.live.toadbomb.QuickTravel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

/**
 * Bridging class to display QT locations on dynmap
 *
 * @author Adam Mummery-Smith
 */
public class QuickTravelDynmapLink
{
	/**
	 * True if dynmap was detected
	 */
	private boolean enabled;
	
	/**
	 * Marker API which we will call to add the markers 
	 */
	private MarkerAPI markerAPI;
	
	/**
	 * Current marker set we are maintaining
	 */
	private MarkerSet markers;
	
	/**
	 * Initialises the Dynmap link
	 * 
	 * @param plugin
	 */
	public void init(QuickTravel plugin)
	{
		try
		{
			Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
			if (dynmapPlugin != null)
			{
				DynmapAPI api = (DynmapAPI)dynmapPlugin;
				this.markerAPI = api.getMarkerAPI();
				this.enabled = true;

				plugin.info("Dynmap detected, enabling QT layer");
			}
			else
			{
				plugin.info("Dynmap was not detected");
			}
		}
		catch (Throwable th) {}
	}
	
	/**
	 * Called when the parent plugin is shutdown
	 */
	public void disable()
	{
		if (this.markers != null)
		{
			this.markers.deleteMarkerSet();
		}
		
		this.enabled   = false;
		this.markerAPI = null;
		this.markers   = null;
	}
	
	/**
	 * Called when any QT's are modified 
	 * 
	 * @param provider
	 */
	public void update(QuickTravelLocationProvider provider)
	{
		if (!this.enabled) return;
		
		// Delete the old markers if we have them
		if (this.markers != null)
		{
			this.markers.deleteMarkerSet();
			this.markers = null;
		}

		// Create a new marker set
		this.markers = this.markerAPI.createMarkerSet("quicktravel", "QuickTravel", null, false);
		this.markers.setLayerPriority(10);
		
		// Use pin icon for the QT marker
		MarkerIcon icon = this.markerAPI.getMarkerIcon("pin");
		
		// Add all QT locations to the marker set
		for (QuickTravelLocation location : provider.getLocations())
		{
			Location loc = location.getPrimary();
			Marker marker = this.markers.createMarker(location.getName(), location.getName(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), icon, false);
			marker.setDescription("<b>Name:</b> " + location.getName() + "</b><br /><b>Type:</b> " + location.getType());
		}
	}
}
