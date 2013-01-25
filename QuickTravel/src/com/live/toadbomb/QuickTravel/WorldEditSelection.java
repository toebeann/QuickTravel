package com.live.toadbomb.QuickTravel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.LocalPlayer;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldVector;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.EllipsoidRegionSelector;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.SphereRegionSelector;

/**
 * Bridge class to work with the worldedit selection
 * 
 * @author Mumfrey
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
	
	/**
	 * @param player
	 * @param centrePoint
	 * @param radius
	 */
	public static void setSelection(Player player, Location centrePoint, double radius)
	{
		try
		{
			if (player != null && centrePoint != null && radius > 0)
			{
				WorldEditPlugin plugin = (WorldEditPlugin)Bukkit.getPluginManager().getPlugin("WorldEdit");
				LocalPlayer wePlayer = plugin.wrapPlayer(player);
				WorldEdit worldEdit = plugin.getWorldEdit();
				LocalSession session = worldEdit.getSession(wePlayer);
				
				if (session != null && session.hasCUISupport())
				{
					Vector vCentre = BukkitUtil.toVector(centrePoint);
					SphereRegionSelector selector = new SphereRegionSelector(BukkitUtil.getLocalWorld(player.getWorld()), vCentre, (int)radius);
					session.setRegionSelector(BukkitUtil.getLocalWorld(player.getWorld()), selector);
			        session.dispatchCUISelection(wePlayer);
				}
			}
		}
		catch (Throwable th) {}
	}

	/**
	 * Check whether the current worldedit selection is an ellipsoid region
	 * 
	 * @param player
	 * @return
	 */
	public static boolean hasEllipsoidRegion(Player player)
	{
		RegionSelector selector = getRegionSelector(player);
		return (selector != null && selector instanceof EllipsoidRegionSelector);
	}
	
	/**
	 * Return the specifications of the current worldedit region as a SphereDefinition
	 * 
	 * @param player
	 * @return
	 */
	public static SphereDefinition getEllipsoidRegion(Player player)
	{
		try
		{
			RegionSelector selector = getRegionSelector(player);
			if (selector != null && selector instanceof EllipsoidRegionSelector)
			{
				EllipsoidRegion region = ((EllipsoidRegionSelector)selector).getRegion();
				Vector radii = region.getRadius();
				double radius = Math.max(Math.max(radii.getX(), radii.getY()), radii.getZ());
				WorldVector centre = new WorldVector(BukkitUtil.getLocalWorld(player.getWorld()), region.getCenter());
				return new SphereDefinition(BukkitUtil.toLocation(centre), radius);
			}
		}
		catch (Throwable th) {}
		
		return null;
	}

	/**
	 * Internal function used to get the current region selector from worldedit
	 * 
	 * @param player
	 * @return
	 */
	private static RegionSelector getRegionSelector(Player player)
	{
		try
		{
			if (player != null)
			{
				WorldEditPlugin plugin = (WorldEditPlugin)Bukkit.getPluginManager().getPlugin("WorldEdit");
				LocalPlayer wePlayer = plugin.wrapPlayer(player);
				WorldEdit worldEdit = plugin.getWorldEdit();
				LocalSession session = worldEdit.getSession(wePlayer);
				
				if (session != null)
				{
					return session.getRegionSelector(BukkitUtil.getLocalWorld(player.getWorld()));
				}
			}
		}
		catch (Throwable th) {}
		
		return null;
	}
}
