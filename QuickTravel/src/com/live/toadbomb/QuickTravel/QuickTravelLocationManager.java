package com.live.toadbomb.QuickTravel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.live.toadbomb.QuickTravel.QuickTravelLocation.Type;

/**
 * This class contains information about defined QT locations. This replaces the old non-stateful config-based system
 * and keeps all QT's in memory, serialising them to disk when necessary but otherwise keeping everything very dynamic
 * and efficient/
 *
 * @author Mumfrey
 */
public class QuickTravelLocationManager implements QuickTravelLocationProvider, QuickTravelCallback, Runnable
{
	/**
	 * Reference to the parent plugin
	 */
	private QuickTravel plugin;

	/**
	 * File to load/store locations from/to
	 */
	private File locationsFile = null;

	/**
	 * ALl the locations that we know about!
	 */
	private Map<String, QuickTravelLocation> locations = new Hashtable<String, QuickTravelLocation>();
	
	/**
	 * List of players warming up
	 */
	private Map<Player, QuickTravelPassport> warmUpEntries = new Hashtable<Player, QuickTravelPassport>();

	/**
	 * List of players cooling down
	 */
	private Map<Player, Integer> coolDownPlayers = new HashMap<Player, Integer>();
	
	/**
	 * Number of ticks to warm up for
	 */
	private int warmUpTicks = 20;

	/**
	 * Number of ticks to cool down for
	 */
	private int coolDownTicks = 100;

	/**
	 * @param parentPlugin
	 */
	public QuickTravelLocationManager(QuickTravel parentPlugin)
	{
		this.plugin = parentPlugin;
		this.locationsFile = new File(this.plugin.getDataFolder(), "locations.yml");
		
		// Apply options from the plugin options
		this.updateOptions();
	}

	/**
	 * Updates local configuration options from the options store 
	 */
	public void updateOptions()
	{
		this.warmUpTicks = Math.min(Math.max(this.plugin.getOptions().getWarmUpTicks(), 0), 1200);
		this.coolDownTicks = Math.max(this.plugin.getOptions().getCoolDownTicks(), 0);
	}
	
	/* (non-Javadoc)
	 * @see com.live.toadbomb.QuickTravel.QuickTravelLocationProvider#getLocations()
	 */
	@Override
	public Collection<QuickTravelLocation> getLocations()
	{
		return Collections.unmodifiableCollection(locations.values());
	}
	
	/* (non-Javadoc)
	 * @see com.live.toadbomb.QuickTravel.QuickTravelLocationProvider#getQuickTravelByName(java.lang.String)
	 */
	@Override
	public QuickTravelLocation getLocationByName(String name)
	{
		if (name == null || name.length() < 1) return null;
		return this.locations.get(name.toLowerCase());
	}
	
	/* (non-Javadoc)
	 * @see com.live.toadbomb.QuickTravel.QuickTravelLocationProvider#getQuickTravelAtLocation(org.bukkit.Location)
	 */
	@Override
	public QuickTravelLocation getLocationAt(Location coords)
	{
		if (coords != null)
		{
			for (QuickTravelLocation entry : this.locations.values())
			{
				if (entry.regionContains(coords, this.plugin.getOptions().getHeightModifier()))
					return entry;
			}
		}
		
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.live.toadbomb.QuickTravel.QuickTravelLocationProvider#getLocationCount()
	 */
	@Override
	public int getLocationCount()
	{
		return this.locations.size();
	}

	/**
	 * Schedule a quicktravel
	 * 
	 * @param passport Quicktravel details
	 * @return
	 */
	public void beginQuickTravel(QuickTravelPassport passport)
	{
		if (passport == null) return;
	
		if (this.warmUpTicks < 1 || passport.getPlayer().hasPermission("qt.instant"))
		{
			passport.preTeleport();
			passport.doTeleport();
			return;
		}
		else 
		{
			passport.setWarmUpTicks(this.warmUpTicks);
			this.warmUpEntries.put(passport.getPlayer(), passport);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
		int heightModifier = this.plugin.getOptions().getHeightModifier();
		
		// Process active passports
		Iterator<Entry<Player, QuickTravelPassport>> passports = this.warmUpEntries.entrySet().iterator();
		while (passports.hasNext())
		{
			Entry<Player, QuickTravelPassport> passportEntry = passports.next();
			
			if (passportEntry.getValue().onTick(heightModifier))
				passports.remove();
		}

		// Tick active cooldowns
		Iterator<Entry<Player, Integer>> coolDowns = this.coolDownPlayers.entrySet().iterator();
		while (coolDowns.hasNext())
		{
			Entry<Player, Integer> coolDownEntry = coolDowns.next();
			int ticks = coolDownEntry.getValue() - 1;
			if (ticks < 0) coolDowns.remove();
			coolDownEntry.setValue(ticks);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.live.toadbomb.QuickTravel.QuickTravelCallback#notifyPlayerTeleported(org.bukkit.entity.Player, double)
	 */
	@Override
	public void notifyPlayerTeleported(Player player, double cost)
	{
		if (this.coolDownTicks > 0 && !player.hasPermission("qt.instant"))
		{
			this.coolDownPlayers.put(player, this.coolDownTicks);
		}
	}
	
	/**
	 * Get the number of cooldown ticks remaining for this player
	 * 
	 * @param player
	 * @return
	 */
	public int getPlayerTeleportCooldown(Player player)
	{
		if (this.coolDownPlayers.containsKey(player))
		{
			return this.coolDownPlayers.get(player);
		}
		
		return 0;
	}
	
	/**
	 * Create a quicktravel point, throws and exception if the QT already exists
	 * 
	 * @param qtName
	 * @param location
	 * @param defaultRadius
	 * @return
	 */
	public QuickTravelLocation createQT(String qtName, Location location, double defaultRadius) throws IllegalArgumentException
	{
		QuickTravelLocation newQT = new QuickTravelLocation(qtName, location, defaultRadius);

		if (this.locations.containsKey(newQT.getName()))
		{
			throw new IllegalArgumentException("Attempted to create a QT with a duplicate name \"" + newQT.getName() + "\"");
		}
		
		this.locations.put(newQT.getName(), newQT);
		return newQT;		
	}

	/**
	 * Renames a QT
	 * 
	 * @param qtToRename
	 * @param newQTName
	 */
	public void renameQT(QuickTravelLocation qtToRename, String newQTName)
	{
		this.locations.remove(qtToRename.getName());
		qtToRename.setName(newQTName);
		this.locations.put(qtToRename.getName(), qtToRename);
	}
	
	/**
	 * Deletes a QT
	 * 
	 * @param qtToDelete
	 */
	public void deleteQT(QuickTravelLocation qtToDelete)
	{
		QuickTravelLocation oldLocation = this.locations.remove(qtToDelete.getName());
		
		if (oldLocation != qtToDelete)
		{
			throw new IllegalStateException("Corrupted location table detected! QT with name \"" + qtToDelete.getName() + "\" was stored with a different key. Maybe somebody called setName() directly!");
		}
		
		// Notify the location that it was deleted
		oldLocation.notifyDeleted();
		
		// notify all remaining QT's that this QT was deleted, so that they can un-map the QT in their charge maps
		for (QuickTravelLocation qt : this.locations.values())
			qt.notifyQuickTravelDeleted(oldLocation);
	}

	/**
	 * Sets a boolean property on all QT's in a world
	 * 
	 * @param sender player requesting the property change
	 * @param world world to set properties in
	 * @param propertyName name of the property to set, valid property names are "free", "discovery", "perms" and "multiworld"
	 * @param toggle toggle the propery values
	 * @param newValue value to set if toggle is false
	 */
	public void setQTProperty(CommandSender sender, World world, String propertyName, boolean toggle, boolean newValue)
	{
		     if (propertyName.equals("free"))       this.setQTFree               (sender, world, toggle, newValue);
		else if (propertyName.equals("discovery"))  this.setQTRequiresDiscovery  (sender, world, toggle, newValue);
		else if (propertyName.equals("perms"))      this.setQTRequiresPermissions(sender, world, toggle, newValue);
		else if (propertyName.equals("multiworld")) this.setQTMultiWorld         (sender, world, toggle, newValue);
	}
	
	/**
	 * Sets a boolean property on a specified QT
	 * 
	 * @param sender player requesting the property change
	 * @param qt QT to set the property on
	 * @param propertyName name of the property to set, valid property names are "free", "discovery", "perms" and "multiworld"
	 * @param toggle toggle the propery values
	 * @param newValue value to set if toggle is false
	 * @param world world to set properties in
	 */
	public void setQTProperty(CommandSender sender, QuickTravelLocation qt, String propertyName, boolean toggle, boolean newValue)
	{
	         if (propertyName.equals("free"))       this.setQTFree               (sender, qt, toggle, newValue);
		else if (propertyName.equals("discovery"))  this.setQTRequiresDiscovery  (sender, qt, toggle, newValue);
		else if (propertyName.equals("perms"))      this.setQTRequiresPermissions(sender, qt, toggle, newValue);
		else if (propertyName.equals("multiworld")) this.setQTMultiWorld         (sender, qt, toggle, newValue);
	}
	
	/**
	 * Sets the type of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param type New type as a string (eg. "cuboid")
	 */
	public void setQTType(CommandSender sender, World world, String type)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTType(sender, qt, type);
	}
	
	/**
	 * Sets the type of a specific QT
	 * 
	 * @param sender
	 * @param qt
	 * @param type New type as a string (eg. "cuboid")
	 */
	public void setQTType(CommandSender sender, QuickTravelLocation qt, String type)
	{
		qt.setType(type);
		sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.WHITE + qt.getType() + ChatColor.WHITE + ".");
		
		if (qt.getType().equals(Type.Cuboid) && qt.getSecondary() == null)
		{
			sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
		}
	}

	/**
	 * Sets the radius of all QTs in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param setRadius true to set the radius
	 * @param radius new radius
	 */
	public void setQTRadius(CommandSender sender, World world, boolean setRadius, double radius)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTRadius(sender, qt, setRadius, radius);
	}
	
	/**
	 * Sets the type of the specified QT to radius
	 * 
	 * @param sender
	 * @param qt
	 * @param setRadius true to set the radius
	 * @param radius new radius
	 */
	public void setQTRadius(CommandSender sender, QuickTravelLocation qt, boolean setRadius, double radius)
	{
		qt.setType(Type.Radius);
		
		if (setRadius == true)
		{
			qt.setRadius(radius);
			sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ", size: " + ChatColor.GOLD + radius + ChatColor.WHITE + ".");
		}
		else
		{
			sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ".");
		}
	}
	
	/**
	 * Sets the enabled state of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param toggle
	 * @param enabled
	 */
	public void setQTEnabled(CommandSender sender, World world, boolean toggle, boolean enabled)
	{	
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTEnabled(sender, qt, toggle, enabled);
	}
	
	/**
	 * Sets the enabled state of the specified QT
	 * 
	 * @param sender
	 * @param qt
	 * @param toggle
	 * @param enabled
	 */
	public void setQTEnabled(CommandSender sender, QuickTravelLocation qt, boolean toggle, boolean enabled)
	{
		qt.setEnabled(toggle ? !qt.isEnabled() : enabled);
		sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been " + ChatColor.GOLD + (qt.isEnabled() ? "enabled" : "disabled") + ChatColor.WHITE + ".");
	}
	
	/**
	 * Sets the "free" state of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param toggle
	 * @param free
	 */
	public void setQTFree(CommandSender sender, World world, boolean toggle, boolean free)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTFree(sender, qt, toggle, free);
	}
	
	/**
	 * Sets the "free" state of the specified QT
	 * 
	 * @param sender
	 * @param qt
	 * @param toggle
	 * @param free
	 */
	public void setQTFree(CommandSender sender, QuickTravelLocation qt, boolean toggle, boolean free)
	{
		qt.setFree(toggle ? !qt.isFree() : free);
		sender.sendMessage("Free travel to/from " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + ": " + ChatColor.GOLD + (qt.isFree() ? "enabled" : "disabled") + ChatColor.WHITE + ".");
	}
	
	/**
	 * Sets the requireDiscovery flag of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param toggle
	 * @param requireDiscovery
	 */
	public void setQTRequiresDiscovery(CommandSender sender, World world, boolean toggle, boolean requireDiscovery)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTRequiresDiscovery(sender, qt, toggle, requireDiscovery);
	}
	
	/**
	 * Sets the requireDiscovery flag of the specified QT
	 * 
	 * @param sender
	 * @param qt
	 * @param toggle
	 * @param requireDiscovery
	 */
	public void setQTRequiresDiscovery(CommandSender sender, QuickTravelLocation qt, boolean toggle, boolean requireDiscovery)
	{
		qt.setRequiresDiscovery(toggle ? !qt.requiresDiscovery() : requireDiscovery);
		sender.sendMessage("Require discovery for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + ": " + ChatColor.GOLD + (qt.requiresDiscovery() ? "enabled" : "disabled") + ChatColor.WHITE + ".");
	}
	
	/**
	 * Sets the permissions requirement of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param toggle
	 * @param requirePermission
	 */
	public void setQTRequiresPermissions(CommandSender sender, World world, boolean toggle, boolean requirePermission)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTRequiresPermissions(sender, qt, toggle, requirePermission);
	}
	
	/**
	 * Sets the permissions requirement of the specified QT
	 * 
	 * @param sender
	 * @param qt
	 * @param toggle
	 * @param requirePermission
	 */
	public void setQTRequiresPermissions(CommandSender sender, QuickTravelLocation qt, boolean toggle, boolean requirePermission)
	{
		qt.setRequiresPermission(toggle ? !qt.requiresPermission() : requirePermission);
		sender.sendMessage("Require permissions for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + ": " + ChatColor.GOLD + (qt.requiresPermission() ? "enabled" : "disabled") + ChatColor.WHITE + ".");
	}
	
	/**
	 * Sets the multiworld flag of all QT's in the specified world
	 * 
	 * @param sender
	 * @param world World to set or null to set all
	 * @param toggle
	 * @param multiWorld
	 */
	public void setQTMultiWorld(CommandSender sender, World world, boolean toggle, boolean multiWorld)
	{
		for (QuickTravelLocation qt : this.locations.values())
			if (qt.isInWorld(world)) this.setQTMultiWorld(sender, qt, toggle, multiWorld);
	}
	
	/**
	 * Sets the multiworld flag of the specified QT
	 * 
	 * @param sender
	 * @param qt
	 * @param toggle
	 * @param multiWorld
	 */
	public void setQTMultiWorld(CommandSender sender, QuickTravelLocation qt, boolean toggle, boolean multiWorld)
	{
		qt.setMultiWorld(toggle ? !qt.isMultiworld() : multiWorld);
		sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " is multiworld: " + ChatColor.GOLD + (qt.isMultiworld() ? "enabled" : "disabled") + ChatColor.WHITE + ".");
	}

	/**
	 * Loads the locations from the config file
	 */
	public void load()
	{
		// Get an object wrapper on the config file
		FileConfiguration locationsConfig = YamlConfiguration.loadConfiguration(this.locationsFile);
		
		// Look for defaults in the jar
		InputStream defLocationsStream = this.plugin.getResource("locations.yml");
		if (defLocationsStream != null)
		{
			YamlConfiguration defLocations = YamlConfiguration.loadConfiguration(defLocationsStream);
			locationsConfig.setDefaults(defLocations);
		}
		
		// Get the "locations" section which is the root node in the locations config 
		MemorySection locationSections = (MemorySection)locationsConfig.getConfigurationSection("locations");
	
		if (locationSections != null)
		{
			// Loop through the keys and add each new location to the map
			for (Entry<String, Object> location : locationSections.getValues(false).entrySet())
			{
				if (location.getValue() instanceof MemorySection)
				{
					QuickTravelLocation newLocation = null;
					
					try
					{
						newLocation = new QuickTravelLocation(location.getKey(), (MemorySection)location.getValue(), this.plugin.getOptions().getDefaultRadius(), this.plugin.getOptions().enabledByDefault(), this.plugin.getOptions().requireDiscoveryByDefault(), this.plugin.getOptions().requirePermissionsByDefault(), this.plugin.getOptions().isMultiworldByDefault(), this.plugin.getOptions().isFreeByDefault());
					}
					catch (Exception ex)
					{
						this.plugin.warning("Error loading QT \"" + location.getKey() + "\" from the locations file. Invalid or corrupted data found");
					}
					
					// If something went wrong loading the entry, the pointer will be null
					if (newLocation != null)
					{
						this.locations.put(newLocation.getName(), newLocation);
					}
				}
			}

			// Now that all the locations are loaded, loop through the config again and link up all the locations with their respective costs
			for (Entry<String, Object> location : locationSections.getValues(false).entrySet())
			{
				QuickTravelLocation newLocation = this.getLocationByName(location.getKey());
				
				if (location.getValue() instanceof MemorySection && newLocation != null)
				{
					try
					{
						newLocation.linkUsingConfigSection(this, (MemorySection)location.getValue());
					}
					catch (Exception ex) {}
				}
			}
		}

		this.plugin.onLocationsUpdated();
	}

	/**
	 * Save all the stored locations to the disk
	 */
	public void save()
	{
		FileConfiguration locationsConfig = new YamlConfiguration();
		
		ConfigurationSection locationsSection = locationsConfig.createSection("locations");
		
		for (QuickTravelLocation location : this.locations.values())
		{
			ConfigurationSection qtSection = locationsSection.createSection(location.getName());
			location.saveToConfigSection(qtSection);
		}
		
		try
		{
			locationsConfig.save(this.locationsFile);
			this.plugin.onLocationsUpdated();
		}
		catch (IOException ex)
		{
			this.plugin.warning("Error saving QuickTravel configuration: (" + ex.getClass().getSimpleName() + ") " + ex.getMessage());
		}
	}
}
