package com.live.toadbomb.QuickTravel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

/**
 * Bridge class to work with the worldedit selection
 * 
 * @author Adam Mummery-Smith
 */
public class WorldEditSelection
{
	/**
	 * Checks whether WorldEdit is available
	 * @return
	 */
	public static boolean haveWorldEdit()
	{
		return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
	}
	
	/**
	 * Gets the first point of the selection cuboid for the selected player or null if no selection is currently active
	 * 
	 * @param player Player to get the selection cuboid for
	 * @return
	 */
	public static Location getMinimumPoint(Player player)
	{
		Selection selection = WorldEditSelection.getSelection(player);
		return (selection != null) ? selection.getMinimumPoint() : null;
	}

	/**
	 * Gets the second point of the selection cuboid for the selected player or null if no selection is currently active
	 * 
	 * @param player Player to get the selection cuboid for
	 * @return
	 */
	public static Location getMaximumPoint(Player player)
	{
		Selection selection = WorldEditSelection.getSelection(player);
		return (selection != null) ? selection.getMaximumPoint() : null;
	}

	/**
	 * Get the WorldEdit selection
	 * 
	 * @param player Player to get the selection cuboid for
	 * @return
	 */
	private static Selection getSelection(Player player)
	{
		try
		{
			WorldEditPlugin plugin = (WorldEditPlugin)Bukkit.getPluginManager().getPlugin("WorldEdit");
			return plugin.getSelection(player);
		}
		catch (Throwable th) {}
		
		return null;
	}
	
	/**
	 * Set the WorldEdit selection to a cuboid with the specified corners
	 * 
	 * @param player
	 * @param minimumPoint
	 * @param maximumPoint
	 */
	public static void setSelection(Player player, Location minimumPoint, Location maximumPoint)
	{
		try
		{
			if (player != null && minimumPoint != null && maximumPoint != null)
			{
				WorldEditPlugin plugin = (WorldEditPlugin)Bukkit.getPluginManager().getPlugin("WorldEdit");
				CuboidSelection cuboid = new CuboidSelection(minimumPoint.getWorld(), minimumPoint, maximumPoint);
				plugin.setSelection(player, cuboid);
			}
		}
		catch (Throwable th) {}
	}
}
