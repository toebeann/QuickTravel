package com.live.toadbomb.QuickTravel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;

/**
 * Encapsulates all information about a quicktravel location in the world, and provides mutation methods for
 * utilising the QT point. This replaces the non-stateful config-based code of prior versions and provides
 * and base for making future improvements to the plugin's functionality 
 *
 * @author Mumfrey
 */
public class QuickTravelLocation
{
	/**
	 * Type of location region, either radius or cuboid
	 */
	public enum Type
	{
		Radius,
		Cuboid
	}
	
	/**
	 * Name of this quicktravel point
	 */
	private String name = "";

	/**
	 * Currently defined type of this quicktravel location
	 */
	private Type type = Type.Radius;
	
	/**
	 * Whether this quicktravel location is enabled or not
	 */
	private boolean enabled = true;
	
	/**
	 * True if this quicktravel location requires discovery to be used
	 */
	private boolean requireDiscovery = true;
	
	/**
	 * True if this quicktravel location requires permissions to be used
	 */
	private boolean requirePermissions = false;
	
	/**
	 * True if this quicktravel location is free 
	 */
	private boolean free = false;
	
	/**
	 * True if this quicktravel location is multi-world capable
	 */
	private boolean multiworld = false;
	
	/**
	 * Locations 
	 */
	private Location primary, secondary, destination;
	
	/**
	 * Radius used when in radius mode 
	 */
	private double radius = 5.0;
	
	/**
	 * Radius squared, used for comparisons with entity ranges
	 */
	private double radiusSquared = 25.0;
	
	/**
	 * If using a player-set cuboid, use the global height modifier for the cuboid region, otherwise
	 * use the exact cuboid defined by the primary and secondary coords
	 */
	private boolean useCuboidHeightModifier = true;
	
	/**
	 * Set of player names that have discovered this qt location 
	 */
	private Set<String> discoveredBy = new HashSet<String>();
	
	/**
	 * Specific charges from different QT points. QT's are stored by reference so that we don't have 
	 * to worry about them being renamed! 
	 */
	private Map<QuickTravelLocation, Double> chargeFrom = new HashMap<QuickTravelLocation, Double>();
	
	/**
	 * Currently configured departure effect
	 */
	private QuickTravelFX departFX = new QuickTravelFXDeparture(32);

	/**
	 * Currently configured arrival effect
	 */
	private QuickTravelFX arriveFX = new QuickTravelFXArrival(32);

	/**
	 * Message to display when entering the region
	 */
	private String welcomeMessage = "";
	
	/**
	 * Custom multiplier for this QT 
	 */
	private double multiplier = 1.0;
	
	/**
	 * True if this QT should be hidden from being displayed on dynmap 
	 */
	private boolean hiddenFromDynmap = false;
	
	/**
	 * @param name Name for this quicktravel location
	 * @param config Configuration to read the location data from
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	public QuickTravelLocation(String name, ConfigurationSection config, double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.name = name.toLowerCase();
		
		this.loadFromConfigSection(config, defaultRadius, enabledByDefault, requireDiscoveryByDefault, requirePermissionsByDefault, multiworldByDefault, freeByDefault);
	}
	
	/**
	 * @param name Name for this quicktravel location
	 * @param location Location for the, er. location
	 * @param radius Initial radius to use
	 */
	public QuickTravelLocation(String name, Location location, double radius)
	{
		this.name        = name.toLowerCase();
		this.destination = location.clone();
		this.primary     = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		
		this.setRadius(radius);
	}
	
	/**
	 * Sets this location's data using the specified configuration section
	 * 
	 * @param config Configuration to read the location data from
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	@SuppressWarnings("unchecked")
	public void loadFromConfigSection(ConfigurationSection config, double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.reset(defaultRadius, enabledByDefault, requireDiscoveryByDefault, requirePermissionsByDefault, multiworldByDefault, freeByDefault);
		
		World world = Bukkit.getWorld(config.getString("world")); 
		
		this.name                    = config.getString("name", this.getName()).toLowerCase();
		this.welcomeMessage          = config.getString("welcome-message", "");
		this.type                    = config.getString("type", "radius").toLowerCase().equals("cuboid") ? Type.Cuboid : Type.Radius;
		this.enabled                 = config.getBoolean("enabled", enabledByDefault);
		this.requireDiscovery        = config.getBoolean("require-discovery", requireDiscoveryByDefault);
		this.requirePermissions      = config.getBoolean("require-permissions", requirePermissionsByDefault);
		this.free                    = config.getBoolean("free", freeByDefault);
		this.multiworld              = config.getBoolean("multiworld", multiworldByDefault);
		this.multiplier              = config.getDouble("multiplier", 1.0);
		this.hiddenFromDynmap        = config.getBoolean("hidden", false);
		this.useCuboidHeightModifier = config.getBoolean("hidden", false);
		
		this.primary                 = this.parseLocation(config.getConfigurationSection("coords.primary"), world, null);
		this.secondary               = this.parseLocation(config.getConfigurationSection("coords.secondary"), world, null);
		this.destination             = this.parseLocation(config.getConfigurationSection("coords.dest"), world, this.primary);
		
		this.setRadius(config.getDouble("radius", defaultRadius));
		
		List<String> discoveryList = (List<String>)config.getList("discovered-by");
		if (discoveryList != null) this.discoveredBy.addAll(discoveryList);
	}
	
	/**
	 * Called after all QT's have been loaded, links up the QT's with each other
	 * 
	 * @param qtProvider
	 * @param config
	 */
	public void linkUsingConfigSection(QuickTravelLocationProvider qtProvider, MemorySection config)
	{
		MemorySection chargeFromConfig = (MemorySection)config.getConfigurationSection("charge-from");
		
		if (chargeFromConfig != null)
		{
			for (Entry<String, Object> chargeFromConfigEntry : chargeFromConfig.getValues(false).entrySet())
			{
				QuickTravelLocation chargeFromEntry = qtProvider.getLocationByName(chargeFromConfigEntry.getKey());
				if (chargeFromEntry != null) this.chargeFrom.put(chargeFromEntry, (Double)chargeFromConfigEntry.getValue());
			}
		}
	}
	
	/**
	 * Write this location to the specified config
	 * 
	 * @param config
	 */
	public void saveToConfigSection(ConfigurationSection config)
	{
		config.set("name",                this.name);
		config.set("welcome-message",     this.welcomeMessage);
		config.set("type",                this.type.name());
		config.set("radius",              this.radius);
		config.set("require-discovery",   this.requireDiscovery);
		config.set("require-permissions", this.requirePermissions );
		config.set("free",                this.free);
		config.set("multiworld",          this.multiworld);
		config.set("multiplier",          this.multiplier);
		config.set("hidden",              this.hiddenFromDynmap);
		
		if (this.primary != null)
		{
			if (this.primary.getWorld() != null)
			{
				config.set("world", this.primary.getWorld().getName());
			}
			
			config.set("coords.primary.x", this.primary.getX()); 
			config.set("coords.primary.y", this.primary.getY()); 
			config.set("coords.primary.z", this.primary.getZ()); 
		}
		
		if (this.secondary != null)
		{
			config.set("coords.secondary.x", this.secondary.getX()); 
			config.set("coords.secondary.y", this.secondary.getY()); 
			config.set("coords.secondary.z", this.secondary.getZ()); 
		}

		if (this.destination != null)
		{
			config.set("coords.dest.x",     this.destination.getX()); 
			config.set("coords.dest.y",     this.destination.getY()); 
			config.set("coords.dest.z",     this.destination.getZ()); 
			config.set("coords.dest.pitch", this.destination.getPitch()); 
			config.set("coords.dest.yaw",   this.destination.getYaw()); 
		}
		
		config.set("discovered-by", this.discoveredBy.toArray(new String[0]));
		
		for (Entry<QuickTravelLocation, Double> chargeFromEntry : this.chargeFrom.entrySet())
		{
			config.set("charge-from." + chargeFromEntry.getKey().name, chargeFromEntry.getValue());
		}
	}

	/**
	 * Reset all location values back to defaults
	 * 
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	protected void reset(double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.welcomeMessage          = "";
		this.type                    = Type.Radius;
		this.enabled                 = enabledByDefault;
		this.requireDiscovery        = requireDiscoveryByDefault;
		this.requirePermissions      = requirePermissionsByDefault;
		this.free                    = freeByDefault;
		this.multiworld              = multiworldByDefault;
		this.primary                 = null;
		this.secondary               = null;
		this.destination             = null;
		this.multiplier              = 1.0;
		this.hiddenFromDynmap        = false;
		this.useCuboidHeightModifier = true;
		
		this.setRadius(defaultRadius);
		
		this.discoveredBy.clear();
		this.chargeFrom.clear();
	}

	/**
	 * Read a location from the specified config
	 * 
	 * @param config Config to read from
	 * @param world World to use if no world can be parsed
	 * @param defaultValue Value to return if the config node does not contain the required data
	 * @return
	 */
	private Location parseLocation(ConfigurationSection config, World world, Location defaultValue)
	{
		if (config == null || !config.isSet("x") || !config.isSet("y") || !config.isSet("z"))
		{
			return defaultValue == null ? null : defaultValue.clone();
		}
		
		double xCoord = config.getDouble("x", 0.0);
		double yCoord = config.getDouble("y", 0.0);
		double zCoord = config.getDouble("z", 0.0);
		float yaw     = (float)config.getDouble("yaw", 0.0);
		float pitch   = (float)config.getDouble("pitch", 0.0);
		
		return new Location(world, xCoord, yCoord, zCoord, yaw, pitch);
	}
	
	/**
	 * Get whether this location's region contains the specified location
	 * 
	 * @param coords Location to test
	 * @param heightModifier Height modifier used for cuboid regions
	 * @return True if the specified location is inside this location's region
	 */
	public boolean regionContains(Location coords, int heightModifier)
	{
		if (!this.enabled || (coords != null && coords.getWorld() != this.primary.getWorld())) return false;
		
		// Use this behaviour if set to radius, or if the second point has not been set yet
		if ((this.type == Type.Radius || this.secondary == null) && this.primary != null && coords != null)
		{
			return coords.distanceSquared(this.primary) < this.radiusSquared; 
		}
		
		// Check a cuboid region
		if (this.type == Type.Cuboid && this.primary != null && this.secondary != null && coords != null)
		{
			boolean useHeightModifier = this.useCuboidHeightModifier || (this.primary.getBlockY() == this.secondary.getBlockY());
			
			int minX = Math.min(this.primary.getBlockX(), this.secondary.getBlockX());
			int minY = Math.min(this.primary.getBlockY(), this.secondary.getBlockY());
			int minZ = Math.min(this.primary.getBlockZ(), this.secondary.getBlockZ());
			int maxX = Math.max(this.primary.getBlockX(), this.secondary.getBlockX()) + 1;
			int maxY = Math.max(this.primary.getBlockY(), this.secondary.getBlockY()) + (useHeightModifier ? heightModifier : 0);
			int maxZ = Math.max(this.primary.getBlockZ(), this.secondary.getBlockZ()) + 1;
			
			return (coords.getX() >= minX && coords.getX() < maxX && coords.getY() >= minY && coords.getY() < maxY && coords.getZ() >= minZ && coords.getZ() < maxZ);
		}
		
		return false;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return this.getName();
	}
	
	/**
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name)
	{
		this.name = name.toLowerCase();
	}

	/**
	 * Return the message to display when entering this location's region
	 * 
	 * @return welcome message
	 */
	public String getWelcomeMessage()
	{
		return this.welcomeMessage.length() > 0 ? this.welcomeMessage.replaceAll("(?<!\046)\046([0-9a-fklmnor])", "\247$1").replaceAll("\046\046", "\046") : ChatColor.BLUE + "You have arrived at " + ChatColor.AQUA + this.getName() + ChatColor.BLUE + ".";
	}
	
	/**
	 * Set the welcome message for this qt
	 * 
	 * @param welcomeMessage
	 */
	public void setWelcomeMessage(String welcomeMessage)
	{
		this.welcomeMessage = welcomeMessage != null ? welcomeMessage : "";
	}

	/**
	 * @return the hiddenFromDynmap
	 */
	public boolean isHiddenFromDynmap()
	{
		return hiddenFromDynmap;
	}

	/**
	 * @param hiddenFromDynmap the hiddenFromDynmap to set
	 */
	public void setHiddenFromDynmap(boolean hiddenFromDynmap)
	{
		this.hiddenFromDynmap = hiddenFromDynmap;
	}

	/**
	 * @return the world
	 */
	public World getWorld()
	{
		return this.primary != null ? this.primary.getWorld() : null;
	}
	
	/**
	 * Checks whether this region is in the specified world, returns true if world is null (meaning any world)
	 * 
	 * @param other World to test or null to test all worlds
	 * @return
	 */
	public boolean isInWorld(World other)
	{
		if (other == null) return true;
		World thisWorld = this.getWorld();
		return other.equals(thisWorld);
	}
	
	/**
	 * @param player
	 * @return
	 */
	public boolean checkWorld(Player player)
	{
		if (this.multiworld) return true;
		
		if (player != null && this.primary != null)
		{
			return this.primary.getWorld() == player.getWorld();
		}
		
		return false;
	}
	
	/**
	 * @param player
	 * @return
	 */
	public boolean checkPermission(Player player)
	{
		if (this.requirePermissions)
		{
			return hasPermission(player);
		}
		
		return true;
	}

	/**
	 * @param player
	 * @return
	 */
	public boolean hasPermission(Player player)
	{
		return player.hasPermission("qt.use" + this.name);
	}
	
	/**
	 * @return
	 */
	public Type getType()
	{
		return this.type;
	}
	
	/**
	 * Set the type by string, currently supports "cuboid", "radius" and "toggle"
	 * 
	 * @param type
	 */
	public void setType(String type)
	{
		if (type.equalsIgnoreCase(Type.Cuboid.name()))
		{
			this.setType(Type.Cuboid);
		}
		else if (type.equalsIgnoreCase("toggle"))
		{
			this.toggleType();
		}
		else
		{
			this.setType(Type.Radius);
		}
	}
	
	/**
	 * Set the type
	 * 
	 * @param type
	 */
	public void setType(Type type)
	{
		this.type = type;
	}
	
	/**
	 * Toggle the type
	 */
	public void toggleType()
	{
		this.type = (this.type == Type.Cuboid) ? Type.Radius : Type.Cuboid;
	}
	
	/**
	 * @return the primary location
	 */
	public Location getPrimary()
	{
		return this.primary;
	}
	
	/**
	 * Set the primary location
	 * 
	 * @param location location to set
	 * @param moveDestination set this to true to move the destination point as well as the primary
	 */
	public void setPrimary(Location location, boolean moveDestination)
	{
		this.primary = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		this.useCuboidHeightModifier = false;
		
		if (this.secondary != null && !this.secondary.getWorld().equals(location.getWorld()))
		{
			this.secondary = null;
		}
		
		if (moveDestination)
		{
			this.destination = location.clone();
		}
	}
	
	/**
	 * @return the secondary location
	 */
	public Location getSecondary()
	{
		return this.secondary;
	}
	
	/**
	 * Set the secondary location
	 * 
	 * @param location
	 * @param usePreciseCuboid
	 */
	public void setSecondary(Location location, boolean usePreciseCuboid)
	{
		this.secondary = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		this.useCuboidHeightModifier = !usePreciseCuboid;
	}
	
	/**
	 * @return
	 */
	public Location getDestination()
	{
		return this.destination;
	}
	
	/**
	 * @param location
	 */
	public void setDestination(Location location)
	{
		this.destination = location.clone();
	}
	
	/**
	 * @return
	 */
	public double getRadius()
	{
		return this.radius;
	}
	
	/**
	 * @return
	 */
	public double getRadiusSquared()
	{
		return this.radiusSquared;
	}
	
	/**
	 * @param radius
	 */
	public void setRadius(double radius)
	{
		this.radius = Math.max(1, Math.abs(radius));
		this.radiusSquared = this.radius * this.radius;
	}
	
	/**
	 * Check whether this QT has been discovered by the specified player
	 * 
	 * @param player
	 * @return
	 */
	public boolean isDiscoveredBy(Player player)
	{
		return this.discoveredBy.contains(player.getName());
	}
	
	/**
	 * Notify this QT that it has been discovered by the specified player
	 * 
	 * @param player
	 */
	public void setDiscovered(Player player)
	{
		this.discoveredBy.add(player.getName());
	}

	/**
	 * @return
	 */
	public boolean isEnabled()
	{
		return this.enabled;
	}
	
	/**
	 * @param enabled
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}
	
	/**
	 * @return
	 */
	public boolean isFree()
	{
		return this.free;
	}
	
	/**
	 * @param free
	 */
	public void setFree(boolean free)
	{
		this.free = free;
		if (free) this.multiplier = 1.0;
	}
	
	/**
	 * @return
	 */
	public double getMultiplier()
	{
		return multiplier;
	}
	
	/**
	 * @param multiplier
	 */
	public void setMultiplier(double multiplier)
	{
		this.multiplier = Math.max(multiplier, 0.0);
		this.free = false;
	}
	
	/**
	 * @return
	 */
	public boolean isMultiworld()
	{
		return this.multiworld;
	}
	
	/**
	 * @param multiWorld
	 */
	public void setMultiWorld(boolean multiWorld)
	{
		this.multiworld = multiWorld;
	}
	
	/**
	 * @return
	 */
	public boolean requiresDiscovery()
	{
		return this.requireDiscovery;
	}
	
	/**
	 * @param requireDiscovery
	 */
	public void setRequiresDiscovery(boolean requireDiscovery)
	{
		this.requireDiscovery = requireDiscovery;
	}
	
	/**
	 * @return
	 */
	public boolean requiresPermission()
	{
		return this.requirePermissions;
	}
	
	/**
	 * @param requirePermission
	 */
	public void setRequiresPermission(boolean requirePermission)
	{
		this.requirePermissions = requirePermission;
	}
	
	/**
	 * @param origin
	 * @return
	 */
	public boolean shouldChargeFrom(QuickTravelLocation origin)
	{
		return this.chargeFrom.containsKey(origin);
	}
	
	/**
	 * @param origin
	 * @return
	 */
	public double getChargeFrom(QuickTravelLocation origin)
	{
		return (this.chargeFrom.containsKey(origin)) ? this.chargeFrom.get(origin) : 0.0;
	}

	/**
	 * Calculates the change from another QT
	 * 
	 * @param player
	 * @param origin
	 * @param priceMultiplier
	 * @param multiWorldMultiplier
	 * @return
	 */
	public int calculateChargeFrom(Player player, QuickTravelLocation origin, double priceMultiplier, double multiWorldMultiplier)
	{
		Location fromLocation = origin != null ? origin.getTargetLocation() : (player != null ? player.getLocation() : null);
		Location toLocation = this.getTargetLocation();
		
		World currentWorld = player != null ? player.getWorld() : Bukkit.getServer().getWorlds().get(0);
		
		if (fromLocation == null) fromLocation = new Location(currentWorld, 0, 0, 0);
		if (toLocation == null) toLocation = new Location(currentWorld, 0, 0, 0);
		
		double xDiff = Math.abs(fromLocation.getBlockX() - toLocation.getBlockX());
		double yDiff = Math.abs(fromLocation.getBlockY() - toLocation.getBlockY());
		double zDiff = Math.abs(fromLocation.getBlockZ() - toLocation.getBlockZ());
		
		double multiplier = (!fromLocation.getWorld().equals(toLocation.getWorld())) ? multiWorldMultiplier : priceMultiplier;

		return (int)Math.ceil((xDiff + yDiff + zDiff) * multiplier * this.multiplier);
	}

	/**
	 * @param origin
	 * @param newCharge
	 */
	public void setChargeFrom(QuickTravelLocation origin, double newCharge)
	{
		this.chargeFrom.put(origin, newCharge);
	}
	
	/**
	 * @param origin
	 */
	public void resetChargeFrom(QuickTravelLocation origin)
	{
		this.chargeFrom.remove(origin);
	}

	/**
	 * Gets the target location (the location to teleport to if teleporting to this QT
	 * 
	 * @return
	 */
	public Location getTargetLocation()
	{
		return this.destination != null ? this.destination : (this.primary != null ? this.primary : null);
	}
	
	/**
	 * @return the departFX
	 */
	public QuickTravelFX getDepartureEffect()
	{
		return departFX;
	}

	/**
	 * @param departFX the departFX to set
	 */
	public void setDepartureEffect(QuickTravelFX departFX)
	{
		this.departFX = departFX;
	}

	/**
	 * @return the arriveFX
	 */
	public QuickTravelFX getArrivalEffect()
	{
		return this.arriveFX;
	}

	/**
	 * @param arriveFX the arriveFX to set
	 */
	public void setArrivalEffect(QuickTravelFX arriveFX)
	{
		this.arriveFX = arriveFX;
	}
	
	/**
	 * Notifies this QT that it has been deleted, so that it can release any relevant resources
	 */
	public void notifyDeleted()
	{
		this.chargeFrom.clear();
		this.enabled = false;
	}
	
	/**
	 * Called when another QT is deleted, allows this location to remove the deleted QT from its local stors
	 * 
	 * @param other
	 */
	public void notifyQuickTravelDeleted(QuickTravelLocation other)
	{
		this.chargeFrom.remove(other);
	}

	/**
	 * Gets information about this QT as a string, for display in the admin's QT list
	 * 
	 * @param sender
	 * @return
	 */
	public String getInfo(CommandSender sender)
	{
		StringBuilder info = new StringBuilder();
		
		if (this.getWorld() != null)
			info.append("[").append(this.getWorld().getName()).append("] ");
		
		info.append(ChatColor.AQUA).append(this.name).append(ChatColor.WHITE).append(" | ");
		
		if (this.isEnabled())
			info.append(ChatColor.GREEN).append("Enabled");
		else
			info.append(ChatColor.RED).append("Disabled");

		if (sender instanceof Player)
		{
			if (this.isDiscoveredBy((Player)sender))
				info.append(ChatColor.WHITE).append(" | ").append(ChatColor.GRAY).append("Discovered");
			else
				info.append(ChatColor.WHITE).append(" | ").append(ChatColor.DARK_GRAY).append("Undiscovered");
		}
		
		return info.toString();
	}
}
