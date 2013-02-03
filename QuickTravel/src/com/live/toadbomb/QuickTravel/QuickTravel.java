package com.live.toadbomb.QuickTravel;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.live.toadbomb.QuickTravel.QuickTravelLocation.Type;

/**
 * Main plugin class for QuickTravel
 * 
 * This class has been demoted somewhat from the "God Class" that it was before, but there's still too much functionality here
 * and far too much repetition in the command handler functions.
 *
 * @author toadbomb
 */
public class QuickTravel extends JavaPlugin implements Listener
{
	/**
	 * Prefix to apply to log messages
	 */
	public static final String LOG_PREFIX = "QuickTravel";
	
	/**
	 * Logger object
	 */
	private static final Logger log = Logger.getLogger("QuickTravel");
	
	/**
	 * Reserved words (command names) to block when creating/renaming QT's
	 */
	private static final List<String> reservedWords = new ArrayList<String>();
	
	/**
	 * True if economy functions are active 
	 */
	private static boolean economyEnabled = false;
	
	/**
	 * Location manager, stores locations and provides functions for manipulating them
	 */
	private QuickTravelLocationManager locationManager;
	
	/**
	 * Configuration/options from the config file
	 */
	private QuickTravelOptions options;
	
	/**
	 * Delegate class that parses commands and hands them back to the relevant members of this class 
	 */
	@SuppressWarnings("unused")
	private QuickTravelCommandHandler commandHandler;
	
	/**
	 * Map of players to QT's, used so that we can keep track of when a player walks onto a QT and display a helpful message
	 */
	private Map<String, QuickTravelLocation> playerAt = new Hashtable<String, QuickTravelLocation>();
	
	/**
	 * FX to display when teleporting in wilderness (not from a QT) 
	 */
	private QuickTravelFX wildernessDepartureFX = new QuickTravelFXDeparture(32);
	
	/**
	 * Task which handles warm-up and cool-down process for players
	 */
	private BukkitTask warmUpCoolDownTask;
	
	/**
	 * Object which manages the dynmap display of QT's
	 */
	private QuickTravelDynmapLink dynmapLink;

	/**
	 * Get the configured options
	 * 
	 * @return options
	 */
	public QuickTravelOptions getOptions()
	{
		return this.options;
	}
	
	/* (non-Javadoc)
	 * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
	 */
	@Override
	public void onEnable()
	{
		info("Initializing");
		
		/* Initialize config.yml */
		this.options = new QuickTravelOptions();
		this.options.init(this);
		
		this.locationManager = new QuickTravelLocationManager(this);
		
		PluginManager pluginManager = this.getServer().getPluginManager();
		/* Check if Vault is required */
		if (this.getOptions().enableEconomy())
		{
			/* Get Vault */
			Plugin vaultPlugin = pluginManager.getPlugin("Vault");
			if (vaultPlugin == null)
			{
				severe("Cannot find Vault!");
				severe("Disabling economy!");
				economyEnabled = false;
			}
			else
			{
				info("Vault has been detected");
				EcoSetup eco = new EcoSetup();
				if (!eco.setupEconomy())
				{
					warning("Could not set up economy!");
					economyEnabled = false;
				}
				else
				{
					info("Using " + EcoSetup.economy.getName() + " for economy.");
					economyEnabled = true;
				}
			}
		}
		else
		{
			info("Economy is disabled.");
		}
		
		this.getServer().getPluginManager().registerEvents(this, this);
		
		// New, load the locations from the config
		this.locationManager.load();
		
		// Task to call every tick, which will handle the warm up and cool down for us
		this.warmUpCoolDownTask = this.getServer().getScheduler().runTaskTimer(this, this.locationManager, 1, 1);
		
		if (this.getOptions().isDynmapEnabled())
		{
			try
			{
				this.dynmapLink = new QuickTravelDynmapLink();
				this.dynmapLink.init(this);
				this.dynmapLink.update(this.locationManager);
			}
			catch (Throwable th) {}
		}

		this.commandHandler = new QuickTravelCommandHandler(this);
	}
	
	/* (non-Javadoc)
	 * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
	 */
	@Override
	public void onDisable()
	{
		// Shut down the dynmap module if it was previously initialised
		if (this.dynmapLink != null)
		{
			this.dynmapLink.disable();
			this.dynmapLink = null;
		}
		
		// Shut down the warmup/cooldown task. This probably isn't necessary for normal onDisable
		// but is here to support /qt reload
		if (this.warmUpCoolDownTask != null)
		{
			this.warmUpCoolDownTask.cancel();
			this.warmUpCoolDownTask = null;
		}
		
		// Shut down and remove references to helpers
		this.locationManager.save();
		this.locationManager = null;
		this.options = null;
		this.commandHandler = null;
		
		// Clear the player location cache
		this.playerAt.clear();
	}

	/**
	 * Player move event callback. Here we handle QT discovery and notifying players that they are in range of a QT
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		String playerName = player.getName();
		
		// Basic permissions check, don't bother doing anything if the player doesn't have permissions to use QT's
		if (!player.hasPermission("qt.user")) return;

		// Get QT at the player's location
		QuickTravelLocation qt = this.locationManager.getLocationAt(player.getLocation());
		
		// If the player is standing on a QT 
		if (qt != null && qt.checkPermission(player))
		{
			// If the player hasn't previously discovered this QT, discover it now
			if (!qt.isDiscoveredBy(player))
			{
				// Set the discovered state of the QT
				qt.setDiscovered(player);
				this.locationManager.save();
				
				// Notify the player
				player.sendMessage(ChatColor.BLUE + "You have discovered " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
				player.sendMessage(ChatColor.WHITE + "Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
			}
			else
			{
				// If the *current* qt is different to the last qt the player was at, then notify the player they have arrived
				if (!qt.equals(this.playerAt.get(playerName)))
				{
					player.sendMessage(qt.getWelcomeMessage(playerName));
					player.sendMessage(ChatColor.WHITE + "Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
				}
				
				// Store the player's current location
				this.playerAt.put(playerName, qt);
			}
		}
		else
		{
			// Player is not at a QT, remove the QT presence entry
			this.playerAt.remove(playerName);
		}
	}
	
	/**
	 * Called when a player disconnects/kicked, remove the player from the presence map
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) 
	{
		if (event.getPlayer() != null)
			this.playerAt.remove(event.getPlayer().getName());
	}
	
	/**
	 * Callback from the location manager to notify us that something changed (locations were saved)
	 */
	public void onLocationsUpdated()
	{
		// Update dynmap if it's active
		if (this.dynmapLink != null)
		{
			this.dynmapLink.update(this.locationManager);
		}
	}
	
	/**
	 * Attempts to QT the player to the QT specified
	 * 
	 * @param player Player to QT
	 * @param targetQtName Name of a target QT to attempt to travel to 
	 */
	@SuppressWarnings("cast")
	protected void quickTravel(Player player, String targetQtName)
	{
		try
		{
			/* Argument is a number, throw list at player */
			int page = Math.max(1, Integer.parseInt(targetQtName));
			this.listQuickTravels(player, page, false);
			return;
		}
		catch (NumberFormatException e) {}
		
		// Check player's cooldown state, if they are cooling down then refuse the teleport
		int coolDownTicksRemaining = this.locationManager.getPlayerTeleportCooldown(player);
		if (coolDownTicksRemaining > 0)
		{
			float secondsRemaining = (float)coolDownTicksRemaining / 20.0F;
			player.sendMessage(ChatColor.BLUE + "You must wait another " + ChatColor.GOLD + String.format("%.1f", secondsRemaining) + ChatColor.BLUE + " seconds to QuickTravel again!");
			return;
		}
		
		// Get the originating QT (may be null if player is in the wilderness) and the target qt from the args 
		QuickTravelLocation origin = this.locationManager.getLocationAt(player.getLocation());
		QuickTravelLocation target = this.locationManager.getLocationByName(targetQtName); 
		
		/* Argument presumed to be a request to QT
		 * Check QT is valid */
		if (this.checkPlayerCanTravelFromTo(player, origin, target, targetQtName, true))
		{
			double travelCost = 0.0;
			
			if (origin != null)
			{
				/* Player is at a QT location */
				if (origin == target)
				{
					/* Player is already at the requested QT, do not send */
					player.sendMessage(ChatColor.BLUE + "You are already at " + ChatColor.AQUA + target.getName() + ChatColor.BLUE + "!");
					return;
				}
				
				travelCost = this.calculateChargeFromTo(player, origin, target);
			}
			else if (this.getOptions().canQtFromAnywhere(player))
			{
				/* Player is not at a QT location, however QTs are enabled from anywhere */
				travelCost = this.calculateChargeFromAnywhere(player, target);
			}
			else
			{
				/* Player is not at a valid location to QT */
				player.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
				return;
			}
			
			/* Check player has required funds and then send QT */
			if (this.checkPlayerHasFunds(player, travelCost))
			{
				this.locationManager.scheduleQuickTravel(player, travelCost, origin, target, this.wildernessDepartureFX);
			}
		}
		else
		{
			/*
			 * It has been determined that this is an invalid QT.
			 * Display list
			 */
			this.listQuickTravels(player, 1, false);
		}
	}

	/**
	 * Calculate the charge to the specified target qt from the wilderness
	 * 
	 * @param player Player to calculate charge for
	 * @param target Target qt to calculate charge to
	 * @return
	 */
	private double calculateChargeFromAnywhere(Player player, QuickTravelLocation target)
	{
		// No charge if economy is disabled or if player has permissions to teleport for free
		if (!economyEnabled || player.hasPermission("qt.free")) return 0.0;
		
		double travelCost = 0.0;

		if (!this.getOptions().isFreeByDefault())
		{
			/* Economy is enabled QTs are not free by default
			 * Check whether destination is free and calculate if not */
			if (this.getOptions().useGlobalPrice() && this.getOptions().getGlobalPrice() > 0)
			{
				travelCost = this.getOptions().getGlobalPrice();
			}
			else if (!target.isFree())
			{
				travelCost = this.calculateCharge(player, null, target);
			}
		}
		
		if (!target.getWorld().equals(player.getWorld()))
		{
			/* Charge multiworld tax */
			travelCost += this.getOptions().getMultiworldTax();
		}
		
		return travelCost;
	}

	/**
	 * Calculate the charge to the specified target qt from another specified QT
	 * 
	 * @param player Player to calculate charge for
	 * @param origin Origin qt to calculate charge from
	 * @param target Target qt to calculate charge to
	 * @return
	 */
	private double calculateChargeFromTo(Player player, QuickTravelLocation origin, QuickTravelLocation target)
	{
		if (!economyEnabled || player.hasPermission("qt.free")) return 0.0;
		
		double travelCost = 0.0;
		
		/* Economy is enabled and one or other QT is not free */
		if (!(origin.isFree() || target.isFree()))
		{
			// shouldChargeFrom returns true if the QT has an entry in its charge map for the specified origin
			if (target.shouldChargeFrom(origin))
			{
				/* Price has been manually set for QT */
				travelCost = target.getChargeFrom(origin);
			}
			else if (this.getOptions().useGlobalPrice() && this.getOptions().getGlobalPrice() > 0)
			{
				travelCost = this.getOptions().getGlobalPrice();
			}
			/* No custom price set, check whether it should be free or if we should set the price */
			else if ((this.getOptions().canQtFromAnywhere(player) && !this.getOptions().isFreeFromQts()) || (!this.getOptions().canQtFromAnywhere(player) && !this.getOptions().isFreeByDefault()))
			{
				/* QT should not be free, calculate price */
				travelCost = this.calculateCharge(player, origin, target);
			}
		}
		
		/* One or both of these QTs are free */
		if (!origin.getWorld().equals(target.getWorld()))
		{
			/* Charge multiworld tax */
			travelCost += this.getOptions().getMultiworldTax();
		}
		
		return travelCost;
	}

	/**
	 * Verify that the player has sufficient funds
	 * 
	 * @param player Player to check
	 * @param travelCost Charge to check for
	 */
	private boolean checkPlayerHasFunds(Player player, double travelCost)
	{
		try
		{
			/* Check player has enough money */
			if (travelCost > 0 && this.getOptions().enableEconomy() && EcoSetup.economy != null && !EcoSetup.economy.has(player.getName(), travelCost))
			{
				/* Player does not have enough money */
				player.sendMessage("You do not have enough money to go there.");
				return false;
			}
		}
		catch (Exception ex)
		{
			// Likely because economy plugin is missing/broken/not properly configured
			warning("Error checking player funds, check the enconomy plugin is correctly registered!");
		}
		
		return true;
	}
	
	/**
	 * Create a new QuickTravel location using the player's location and the specified arguments
	 * 
	 * @param player Player creating the QT, player's location will be used for the initial position of the QT
	 * @param args Command-line arguments
	 */
	protected void createQuickTravel(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 1)
		{
			String qtName = args[1].toLowerCase();

			// Check for a valid identifier
			if (!this.checkQTNameIsValid(player, qtName, "create"))
				return;
			
			if (this.locationManager.getLocationByName(qtName) != null)
			{
				/* QT with name chosen already exists */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create" + ": " + ChatColor.AQUA + qtName + ChatColor.GOLD + " already exists!");
				return;
			}
			
			/* Checks passed, create QT */
			QuickTravelLocation newQT = this.locationManager.createQT(qtName, player.getLocation(), this.getOptions().getDefaultRadius());
			newQT.setDiscovered(player);
			
			player.sendMessage("QT " + ChatColor.AQUA + qtName + ChatColor.WHITE + " created.");
			
			/* QT created, check optional parameters and set options. */
			if (args.length > 2)
			{
				this.configureNewQuickTravel(player, args, newQT);
			}
			
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Creates a new QT at your current location.");
			player.sendMessage("/qt create <name>");
		}
	}

	/**
	 * Called from createQuickTravel() to apply additional settings to the QT based on the supplied arguments
	 * 
	 * @param player Player creating the QT, player's location will be used for the initial position of the QT
	 * @param args Command-line arguments
	 * @param qt Newly-created QT
	 */
	private void configureNewQuickTravel(Player player, String[] args, QuickTravelLocation qt)
	{
		for (int i = 2; i < args.length; i++)
		{
			if (args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("-radius"))
			{
				/* Set radius size */
				if (args.length > i + 1)
				{
					try
					{
						double radius = Double.parseDouble(args[i + 1]);
						qt.setRadius(radius);
						player.sendMessage("Radius: " + ChatColor.GOLD + radius);
						i++;
					}
					catch (NumberFormatException e2)
					{
						/* Invalid radius */
						player.sendMessage(ChatColor.GOLD + args[i + 1] + ChatColor.WHITE + " is not a valid radius, ignoring.");
					}
				}
				else
				{
					/* No radius given */
					player.sendMessage("No radius provided, ignoring " + ChatColor.GOLD + args[i] + ChatColor.WHITE + ".");
				}
			}
			else if (args[i].equalsIgnoreCase("-e") || args[i].equalsIgnoreCase("-enable") || args[i].equalsIgnoreCase("-enabled"))
			{
				/* Set enabled status */
				if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
				{
					qt.setEnabled(Boolean.valueOf(args[i + 1]));
					player.sendMessage("Enabled: " + ChatColor.GOLD + qt.isEnabled());
					i++;
				}
				else
				{
					/* Player has not given true/false, use the default setting */
					qt.setEnabled(this.getOptions().enabledByDefault());
					player.sendMessage("Enabled: " + ChatColor.GOLD + this.getOptions().enabledByDefault());
				}
			}
			else if (args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("-free"))
			{
				/* Set free status */
				if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
				{
					qt.setFree(Boolean.valueOf(args[i + 1]));
					player.sendMessage("Free: " + ChatColor.GOLD + qt.isFree());
					i++;
				}
				else
				{
					qt.setFree(!((this.getOptions().canQtFromAnywhere(null) && this.getOptions().isFreeFromQts()) || this.getOptions().isFreeByDefault()));
					player.sendMessage("Free: " + ChatColor.GOLD + qt.isFree());
				}
			}
			else if (args[i].equalsIgnoreCase("-d") || args[i].equalsIgnoreCase("-disc") || args[i].equalsIgnoreCase("-discover") || args[i].equalsIgnoreCase("-discovery"))
			{
				/* Set discovery status */
				if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
				{
					qt.setRequiresDiscovery(Boolean.valueOf(args[i + 1]));
					player.sendMessage("Require discovery: " + ChatColor.GOLD + qt.requiresDiscovery());
					i++;
				}
				else
				{
					/* Player has not given true/false, figure out what they want. */
					qt.setRequiresDiscovery(!this.getOptions().requireDiscoveryByDefault());
					player.sendMessage("Require discovery: " + ChatColor.GOLD + qt.requiresDiscovery());
				}
			}
			else if (args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("-perm") || args[i].equalsIgnoreCase("-perms"))
			{
				/* Set permissions status */
				if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
				{
					qt.setRequiresPermission(Boolean.valueOf(args[i + 1]));
					player.sendMessage("Require permissions: " + ChatColor.GOLD + qt.requiresPermission());
					i++;
				}
				else
				{
					/* Player has not given true/false, figure out what they want. */
					qt.setRequiresPermission(!this.getOptions().requirePermissionsByDefault());
					player.sendMessage("Require permissions: " + ChatColor.GOLD + qt.requiresPermission());
				}
			}
			else if (args[i].equalsIgnoreCase("-m") || args[i].equalsIgnoreCase("-multi") || args[i].equalsIgnoreCase("-multiworld"))
			{
				/* Set multiworld status */
				if (args.length > i + 1)
				{
					if (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false"))
					{
						qt.setMultiWorld(Boolean.valueOf(args[i + 1]));
						player.sendMessage("Multiworld: " + ChatColor.GOLD + qt.isMultiworld());
						i++;
					}
				}
				else
				{
					/* Player has not given true/false, figure out what they want. */
					qt.setMultiWorld(!this.getOptions().isMultiworldByDefault());
					player.sendMessage("Multiworld: " + ChatColor.GOLD + qt.isMultiworld());
				}
			}
			else
			{
				/* Invalid parameter */
				player.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
			}
		}
	}
	
	/**
	 * Renames a QT point based on the supplied arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void renameQuickTravel(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 2)
		{
			String qtName = args[1].toLowerCase();
			String newQTName = args[2].toLowerCase();
			
			QuickTravelLocation qtToRename = this.locationManager.getLocationByName(qtName);
			if (qtToRename == null)
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}

			/* Validate the new QT name */
			if (!this.checkQTNameIsValid(sender, newQTName, "rename"))
				return;
			
			QuickTravelLocation existingQT = this.locationManager.getLocationByName(newQTName);
			if (existingQT != null)
			{
				/* QT with name chosen already exists */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + existingQT.getName() + ChatColor.GOLD + " already exists!");
				return;
			}
			
			/* Checks passed, rename QT */
			this.locationManager.renameQT(qtToRename, newQTName);
			this.locationManager.save();
			
			sender.sendMessage(ChatColor.AQUA + qtName + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + newQTName + ChatColor.WHITE + ".");
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Renames the QT <name>.");
			sender.sendMessage("/qt rename <name> <new name>");
		}
	}
	
	/**
	 * Sets the price multiplier for a specific QT based on the specified command-line arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelMultiplier(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 1)
		{
			String qtName = args[1].toLowerCase();
			double multiplier = 1.0;
			
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			if (qt == null)
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set greeting: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
			
			// Not enough arguments for set, just display the multiplier instead
			if (args.length < 3)
			{
				sender.sendMessage(ChatColor.AQUA + qtName + ChatColor.GOLD + " price multiplier is " + ChatColor.WHITE + String.format("%.2f", qt.getMultiplier()));
				return;
			}
			
			try
			{
				// Check for a number first
				multiplier = Double.parseDouble(args[2]);
			}
			catch (NumberFormatException ex)
			{
				if (!"reset".equalsIgnoreCase(args[2]))
				{
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.WHITE + args[2] + ChatColor.GOLD + " is not a valid value for " + ChatColor.WHITE + "multiplier");
					return;
				}
				
				multiplier = 1.0;
			}
		
			// Apply the new multiplier to the QT
			qt.setMultiplier(multiplier);
			this.locationManager.save();
			
			sender.sendMessage(ChatColor.AQUA + qtName + ChatColor.GOLD + " price multiplier set to " + ChatColor.WHITE + String.format("%.2f", qt.getMultiplier()));
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the price multiplier for the QT named <name>.");
			sender.sendMessage("/qt multiplier <name> <welcome message>");
		}
	}

	/**
	 * Set the name of a QT based on the supplied command-line arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelWelcome(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 1)
		{
			String qtName = args[1].toLowerCase();
			String welcomeMessage = "";
			
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			if (qt == null)
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set greeting: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}

			// Welcome message specified, so glue the remaining args together
			if (args.length > 2)
			{
				StringBuilder welcomeBuilder = new StringBuilder();
				
				for (int i = 2; i < args.length; i++)
					welcomeBuilder.append(args[i]).append(" ");
				
				welcomeMessage = welcomeBuilder.toString().trim();
			}
			
			// Apply the new welcome message to the QT
			qt.setWelcomeMessage(welcomeMessage);
			this.locationManager.save();
			
			sender.sendMessage(ChatColor.AQUA + qtName + ChatColor.GOLD + " greeting set to " + ChatColor.WHITE + qt.getWelcomeMessage("<PlayerName>"));
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the QT welcome message for <name>.");
			sender.sendMessage("/qt welcome <name> <welcome message>");
		}
	}

	/**
	 * Set the type of QT based on the specified arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelType(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length == 3 || args.length == 5)
		{
			Pattern allowedCommands = Pattern.compile("^(cuboid|radius|toggle)$", Pattern.CASE_INSENSITIVE);
			Matcher allowedCheck = allowedCommands.matcher(args[2]);
			
			if (!allowedCheck.matches())
			{
				/* Invalid type */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid type!");
				return;
			}
			
			String type = allowedCheck.group(1).toLowerCase();
			
			if (args[1].equalsIgnoreCase("*"))
			{
				World world = null;
				if (args.length == 5)
				{
					if (args[3].equalsIgnoreCase("-w"))
					{
						world = this.getServer().getWorld(args[4]);

						if (world == null)
						{
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: Specified world " + ChatColor.WHITE + args[4] + ChatColor.GOLD + " was not found");
							return;
						}
					}
					else
					{
						/* Invalid arguments, throw info message. */
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " is not a valid parameter!");
						sender.sendMessage("Sets the type of the QT.");
						sender.sendMessage("/qt type <name | *> <radius | cuboid | toggle>");
						return;
					}
				}
				
				if (this.locationManager.getLocationCount() < 1)
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: You have not made any QTs yet!");
					return;
				}

				this.locationManager.setQTType(sender, world, type);
				this.locationManager.save();
				
				sender.sendMessage("Done.");
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
				
				if (qt == null)
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}

				this.locationManager.setQTType(sender, qt, type);
				this.locationManager.save();
			}
		}
		else if (args.length == 2) // just display the QT type, don't change it
		{
			QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
			
			if (qt != null)
			{
				sender.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " is set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ".");
				if (qt.getType().equals(Type.Cuboid) && qt.getSecondary() == null)
				{
					sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
				}
			}
			else
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the type of the QT.");
			sender.sendMessage("/qt type <name | *> <radius | cuboid | toggle>");
			return;
		}
	}
	
	/**
	 * Set the radius of the QT, or set the QT to type "radius" based on the supplied command-line arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelRadius(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean setRadius = false;      // The radius value was specified 
		boolean setWorldEdit = false;   // The radius should be read from the current worldedit selection (also moves the QT)
		boolean viewWorldEdit = false;  // The worldedit selection should be set to the current QT radius
		
		double radius = this.getOptions().getDefaultRadius();
		
		if (args.length >= 2)
		{
			String qtName = args[1].toLowerCase();
			
			if (args.length > 2)
			{
				for (int i = 2; i < args.length; i++)
				{
					if (args[i].equalsIgnoreCase("-w"))
					{
						/* -w parameter given */
						if (args.length > i + 1)
						{
							world = this.getServer().getWorld(args[i + 1]);
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
							
							i++;
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: No world given for -w!");
								return;
							}
						}
					}
					else if (args[i].equalsIgnoreCase("-s"))
					{
						/* -s parameter given */
						if (args.length > i + 1)
						{
							try
							{
								radius = Double.parseDouble(args[i + 1]);
								setRadius = true;
								setWorldEdit = false;
								i++;
							}
							catch (NumberFormatException e2)
							{
								if (args[i + 1].equalsIgnoreCase("reset"))
								{
									radius = this.getOptions().getDefaultRadius();
									setRadius = true;
									setWorldEdit = false;
									i++;
								}
								else
								{
									/* Invalid size */
									sender.sendMessage(ChatColor.GOLD + args[i + 1] + ChatColor.WHITE + " is not a valid size, ignoring.");
								}
							}
						}
						else
						{
							/* No size given */
							sender.sendMessage("No radius size, ignoring " + ChatColor.GOLD + args[i] + ChatColor.WHITE + ".");
						}
					}
					else if (args[i].equalsIgnoreCase("-v") && !qtName.equals("*"))
					{
						// -v was specified so (attempt to) set the current worldedit selection to the QT radius
						viewWorldEdit = true;
					}
					else if (args[i].equalsIgnoreCase("-r") && !qtName.equals("*"))
					{
						setWorldEdit = true;
					}
					else
					{
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			if (qtName.equals("*"))
			{
				if (this.locationManager.getLocationCount() > 0)
				{
					this.locationManager.setQTRadius(sender, world, setRadius, radius);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
				
				if (qt != null)
				{
					if (setWorldEdit && sender instanceof Player)
					{
						if (!WorldEditSelection.haveWorldEdit())
						{
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius for " + ChatColor.AQUA + qtName + ChatColor.GOLD + "using WorldEdit. WorldEdit was not detected!");
							return;
						}
						else if (!WorldEditSelection.hasEllipsoidRegion((Player)sender))
						{
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius for " + ChatColor.AQUA + qtName + ChatColor.GOLD + "using WorldEdit. Selection is not a sphereoid.");
							return;
						}
						else
						{
							SphereDefinition sphereSelection = WorldEditSelection.getEllipsoidRegion((Player)sender);
							
							if (sphereSelection != null)
							{
								if (!sphereSelection.getLocation().getWorld().equals(qt.getWorld()))
								{
									sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius for " + ChatColor.AQUA + qtName + ChatColor.GOLD + "using WorldEdit. Selection is not in the correct world.");
									return;
								}

								sender.sendMessage(ChatColor.WHITE + "Using WorldEdit selection for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " radius and location");
								
								// Move the QT to the location specified by the worldedit selection
								qt.setPrimary(sphereSelection.getLocation(), true);
								
								setRadius = true;
								viewWorldEdit = true;
								radius = sphereSelection.getRadius();
							}
							else
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius for " + ChatColor.AQUA + qtName + ChatColor.GOLD + "using WorldEdit. Selection is not a valid sphereoid.");
								return;
							}
						}
					}
					
					this.locationManager.setQTRadius(sender, qt, setRadius, radius);

					if (viewWorldEdit && WorldEditSelection.haveWorldEdit() && sender instanceof Player)
					{
						WorldEditSelection.setSelection((Player)sender, qt.getPrimary(), qt.getRadius());
						sender.sendMessage(ChatColor.GOLD + "WorldEdit selection set to spheroid region for " + ChatColor.AQUA + qt.getName() + ChatColor.GOLD + ".");
					}
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
					return;
				}

			}
			
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Changes the type of QT <name> to radius.");
			sender.sendMessage("/qt radius <name> <-s radius | -r> <-v>");
			return;
		}
	}
	
	/**
	 * @param player Player issuing the command
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelCuboid(Player player, String[] args)
	{
		if (args.length >= 2)
		{
			/* Get arguments and deal with appropriately */
			World world = null;
			
			boolean setPrimary = false;
			boolean setSecondary = false;
			boolean setWorldEdit = false;
			boolean viewWorldEdit = false;
			
			String qtName = args[1].toLowerCase();
			Location location = player.getLocation();
			
			if (args.length > 2)
			{
				for (int i = 2; i < args.length; i++)
				{
					if (args[i].equalsIgnoreCase("-w"))
					{
						/* -w parameter given */
						if (args.length > i + 1)
						{
							world = this.getServer().getWorld(args[i + 1]);
							
							if (world == null)
							{
								player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
							
							i++;
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: No world given for -w!");
								return;
							}
						}
					}
					else if (args[i].equalsIgnoreCase("-a") && !qtName.equals("*"))
					{
						/* -a parameter given */
						setPrimary = true;
						setSecondary = false;
						setWorldEdit = false;
					}
					else if (args[i].equalsIgnoreCase("-b") && !qtName.equals("*"))
					{
						/* -b parameter given */
						setSecondary = true;
						setPrimary = false;
						setWorldEdit = false;
					}
					else if (args[i].equalsIgnoreCase("-r") && !qtName.equals("*"))
					{
						/* -s parameter given */
						setSecondary = false;
						setPrimary = false;
						setWorldEdit = true;
					}
					else if (args[i].equalsIgnoreCase("-v") && !qtName.equals("*"))
					{
						viewWorldEdit = true;
					}
					else if (args[i].equalsIgnoreCase("-t") && !qtName.equals("*"))
					{
						/* -s parameter given */
						location = player.getTargetBlock(null, 100).getLocation();
					}
					else
					{
						/* Invalid parameter */
						player.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			if (qtName.equals("*"))
			{
				if (this.locationManager.getLocationCount() > 0)
				{
					this.locationManager.setQTType(player, world, "cuboid");

					player.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
				
				if (qt != null)
				{
					if (setPrimary || setSecondary || setWorldEdit || viewWorldEdit)
					{
						if (player.getWorld().equals(qt.getWorld()))
						{
							qt.setType(Type.Cuboid);
							
							if (setWorldEdit)
							{
								if (WorldEditSelection.haveWorldEdit())
								{
									Location primary = WorldEditSelection.getMinimumPoint(player);
									Location secondary = WorldEditSelection.getMaximumPoint(player);
									if (primary != null && secondary != null)
									{
										qt.setPrimary(primary, false);
										qt.setSecondary(secondary, true);

										player.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ".");
										player.sendMessage(ChatColor.WHITE + " coords for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " set to " + ChatColor.GOLD + "WorldEdit selection.");
									}
									else
									{
										player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: WorldEdit selection was empty!");
									}
								}
								else
								{
									player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: WorldEdit was not detected!");
								}
							}
							else if (setPrimary || setSecondary)
							{
								/* Checks passed, set range */
								if (setPrimary)
									qt.setPrimary(location, false);
								else
									qt.setSecondary(location, false);
	
								player.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ".");
								player.sendMessage(ChatColor.GOLD + (setPrimary ? "Primary" : "Secondary") + ChatColor.WHITE + " coords for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " set.");
								
								if (qt.getSecondary() == null)
								{
									player.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
								}
							}
							
							if (viewWorldEdit && WorldEditSelection.haveWorldEdit())
							{
								if (qt.getPrimary() != null && qt.getSecondary() != null)
								{
									WorldEditSelection.setSelection(player, qt.getPrimary(), qt.getSecondary());
									player.sendMessage(ChatColor.GOLD + "WorldEdit selection set to cuboid region for " + ChatColor.AQUA + qt.getName() + ChatColor.GOLD + ".");
								}
							}
						}
						else
						{
							/* Incorrect world */
							player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: You are not on the correct World!");
							return;
						}
					}
					else
					{
						this.locationManager.setQTType(player, qt, "cuboid");
					}
				}
				else
				{
					/* QT does not exist */
					player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
			
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Changes the type of QT <name> to cuboid.");
			player.sendMessage("/qt cuboid <name | *> <-a | -b | -r> <-v> <-t> <-w [world]>");
			return;
		}
	}
	
	/**
	 * Moves a QuickTravel location to the player's current location
	 * 
	 * @param player Player issuing the command 
	 * @param args Command-line arguments
	 */
	protected void moveQuickTravel(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 1)
		{
			String qtName = args[1].toLowerCase();
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			boolean moveWorld = (args.length > 2 && args[2].toLowerCase().equals("-w"));
			
			if (qt == null)
			{
				/* QT does not exist */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
			
			if (qt.getType() != Type.Radius)
			{
				/* Is not a radius QT */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + qt.getName() + ChatColor.GOLD + " is not a radius QT!");
				return;
			}
			
			if (!player.getWorld().equals(qt.getWorld()) && !moveWorld)
			{
				/* Incorrect world */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: You are not on the correct World!");
				player.sendMessage(ChatColor.GOLD + "Use " + ChatColor.WHITE + "/qt move <name> -w" + ChatColor.GOLD + " to force QT to be moved to this world");
				return;
			}
			
			qt.setPrimary(player.getLocation(), true);
			this.locationManager.save();
			player.sendMessage("Moved QT " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + ".");
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Moves the selected radius QT to your current location.");
			player.sendMessage("/qt move <name>");
		}
	}
	
	/**
	 * Deletes the specified quick travel entry
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void deleteQuickTravel(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length == 2)
		{
			String qtName = args[1].toLowerCase();
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			if (qt == null)
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not delete: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
			
			this.locationManager.deleteQT(qt);
			this.locationManager.save();
			sender.sendMessage("QT " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " was deleted!");
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Deletes the specified QT. Cannot be undone!");
			sender.sendMessage("/qt delete <name>");
			return;
		}
	}

	/**
	 * Set the destination for the specified QT
	 * 
	 * @param player Player issuing the command
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelDestination(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length == 2)
		{
			String qtName = args[1].toLowerCase();
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			if (qt == null)
			{
				/* QT does not exist */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest" + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
			
			if (!player.getWorld().equals(qt.getWorld()))
			{
				/* Incorrect world */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest: You are not on the correct World!");
				return;
			}
			
			qt.setDestination(player.getLocation());
			this.locationManager.save();
			player.sendMessage("Destination for QT " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " set.");
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Sets the arrival spot for the selected QT to your current location.");
			player.sendMessage("/qt dest <name>");
			return;
		}
	}
	
	/**
	 * Sets the quick travel enabled (or disabled) state based on the supplied command-line arguments
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelEnabled(CommandSender sender, String[] args)
	{
		if (args.length > 1)
		{
			/* Get arguments and deal with appropriately */
			World world = null;
			String action = args[0].toLowerCase();
			boolean newEnabled = action.equals("enable");
			boolean toggle = false;
			
			String qtName = args[1].toLowerCase();
			
			if (args.length > 2)
			{
				for (int i = 2; i < args.length; i++)
				{
					if (args[i].equalsIgnoreCase("-w"))
					{
						/* -w parameter given */
						if (args.length > i + 1)
						{
							world = this.getServer().getWorld(args[i + 1]);
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}

							i++;
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": No world given for -w!");
								return;
							}
						}
					}
					else if (args[i].equalsIgnoreCase("-set"))
					{
						/* -set parameter given */
						if (args.length > i + 1)
						{
							if (args[i + 1].equalsIgnoreCase("true"))
							{
								toggle = false;
								newEnabled = true;
								i++;
							}
							else if (args[i + 1].equalsIgnoreCase("false"))
							{
								toggle = false;
								newEnabled = false;
								i++;
							}
							else if (args[i + 1].equalsIgnoreCase("toggle"))
							{
								toggle = true;
								i++;
							}
							else
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": -set must be true, false, or toggle!");
								return;
							}
						}
						else
						{
							/* Has not specified -set parameter */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": -set must be true, false, or toggle!");
								return;
							}
						}
					}
					else
					{
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			if (qtName.equals("*"))
			{
				if (this.locationManager.getLocationCount() > 0)
				{
					this.locationManager.setQTEnabled(sender, world, toggle, newEnabled);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
				
				if (qt != null)
				{
					this.locationManager.setQTEnabled(sender, qt, toggle, newEnabled);
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
			
			this.locationManager.save();
		}
		else if (args[0] != null && args[0].equalsIgnoreCase("enable"))
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Enables the selected QT.");
			sender.sendMessage("/qt enable <name>");
			return;
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Disables the selected QT.");
			sender.sendMessage("/qt disable <name>");
			return;
		}
	}
	
	/**
	 * Sets the price of the specifed QT from the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelPrice(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		boolean reset = false;
		double price = 0;
		
		if (args.length > 3)
		{
			try
			{
				price = Double.parseDouble(args[3]);
				reset = false;
			}
			catch (NumberFormatException e)
			{
				if (args[3].equalsIgnoreCase("reset"))
				{
					reset = true;
				}
				else
				{
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " is not a valid price!");
					return;
				}
			}
			
			QuickTravelLocation fromQt = this.locationManager.getLocationByName(args[1]);
			QuickTravelLocation toQt = this.locationManager.getLocationByName(args[2]);
			
			if (fromQt != null && toQt != null)
			{
				boolean link = (args.length > 4 && args[4].equalsIgnoreCase("-r"));

				if (reset == false)
				{
					toQt.setChargeFrom(fromQt, price);
					if (link) fromQt.setChargeFrom(toQt, price);
					sender.sendMessage("Price " + (link ? "between " : "from ") + ChatColor.AQUA + fromQt.getName() + ChatColor.WHITE + (link ? " and " : " to ") + ChatColor.AQUA + toQt.getName() + ChatColor.WHITE + " set to " + ChatColor.GOLD + price + ChatColor.WHITE + ".");
				}
				else
				{
					toQt.resetChargeFrom(fromQt);
					if (link) fromQt.resetChargeFrom(toQt);
					sender.sendMessage("Price " + (link ? "between " : "from ") + ChatColor.AQUA + fromQt.getName() + ChatColor.WHITE + (link ? " and " : " to ") + ChatColor.AQUA + toQt.getName() + ChatColor.WHITE + " has been reset.");
				}
				
				this.locationManager.save();
				
				if (!economyEnabled)
				{
					/* Economy is disabled, warn user */
					sender.sendMessage("[Warning] Economy is disabled, prices will have no effect.");
				}
			}
			else if (fromQt == null)
			{
				/* QT <a> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
			}
			else if (toQt == null)
			{
				/* QT <b> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " does not exist!");
			}
			else
			{
				/* Unknown error */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: The specified quick travel locations do not exist!");
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the price from QT <a> to QT <b>. If -r is specified also sets the price from <b> to <a>.");
			sender.sendMessage("/qt price <a> <b> <price> <-r>");
			return;
		}
	}
	
	/**
	 * Sets the "free" flag on the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelFree(CommandSender sender, String[] args)
	{
		if (args.length < 3)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether all travel to/from the selected QT is free.");
			sender.sendMessage("/qt free <name | *> <true | false | toggle>");
			return;
		}
		
		this.setQuickTravelProperty(sender, args, "free");
	}

	/**
	 * Sets the "discovery" flag on the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelDiscovery(CommandSender sender, String[] args)
	{
		if (args.length < 3)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether discovery is required for the selected QT.");
			sender.sendMessage("/qt discovery <name | *> <true | false | toggle>");
			return;
		}
		
		this.setQuickTravelProperty(sender, args, "discovery");
	}

	/**
	 * Sets the "require permission" flag on the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelPermission(CommandSender sender, String[] args)
	{
		if (args.length < 3)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether permissions are required for the selected QT.");
			sender.sendMessage("/qt perms <name | *> <true | false | toggle>");
			return;
		}
		
		this.setQuickTravelProperty(sender, args, "perms");
	}
	
	/**
	 * Sets the "multiworld" flag on the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelMultiworld(CommandSender sender, String[] args)
	{
		if (args.length < 3)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether the selected QT is multiworld.");
			sender.sendMessage("/qt multiworld <name | *> <true | false | toggle>");
			return;
		}
		
		this.setQuickTravelProperty(sender, args, "multiworld");
	}

	/**
	 * Sets the "hidden from dynmap" flag on the specified QT
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelHidden(CommandSender sender, String[] args)
	{
		if (args.length < 3)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether the selected QT is hidden from dynmap when dynmap is enabled");
			sender.sendMessage("/qt hidden <name | *> <true | false | toggle>");
			return;
		}
		
		this.setQuickTravelProperty(sender, args, "hidden");
		this.onLocationsUpdated();
	}

	/**
	 * Sets a boolean flag by name on the specified QT
	 * 
	 * @param sender
	 * @param args
	 * @param propertyName
	 */
	private void setQuickTravelProperty(CommandSender sender, String[] args, String propertyName)
	{
		World world = null;
		boolean toggle = false;
		boolean newValue = true;
		String qtName = args[1].toLowerCase();

		if (args[2].equalsIgnoreCase("true"))
		{
			toggle = false;
			newValue = true;
		}
		else if (args[2].equalsIgnoreCase("false"))
		{
			toggle = false;
			newValue = false;
		}
		else if (args[2].equalsIgnoreCase("toggle"))
		{
			toggle = true;
		}
		else
		{
			/* Invalid setting */
			sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set " + ChatColor.AQUA + propertyName + ChatColor.GOLD + ": " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting! Must be one of (" + ChatColor.AQUA + "true, false, toggle" + ChatColor.GOLD + ")");
			return;
		}
		
		if (args.length > 3)
		{
			for (int i = 3; i < args.length; i++)
			{
				if (args[i].equalsIgnoreCase("-w"))
				{
					/* -w parameter given */
					if (args.length > i + 1)
					{
						world = this.getServer().getWorld(args[i + 1]);
						
						if (world == null)
						{
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set " + ChatColor.AQUA + propertyName + ChatColor.GOLD + ": Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
							return;
						}

						i++;
					}
					else
					{
						/* No world given. */
						if (qtName.equals("*"))
						{
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set " + ChatColor.AQUA + propertyName + ChatColor.GOLD + ": No world given for -w!");
							return;
						}
					}
				}
				else
				{
					/* Invalid parameter */
					sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
				}
			}
		}
		
		if (qtName.equals("*"))
		{
			if (this.locationManager.getLocationCount() > 0)
			{
				this.locationManager.setQTProperty(sender, world, propertyName, toggle, newValue);
				sender.sendMessage("Done.");
			}
			else
			{
				/* Player has not made any QTs yet */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set " + ChatColor.AQUA + propertyName + ChatColor.GOLD + ": You have not made any QTs yet!");
				return;
			}
		}
		else
		{
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			if (qt != null)
			{
				this.locationManager.setQTProperty(sender, qt, propertyName, toggle, newValue);
			}
			else
			{
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set " + ChatColor.AQUA + propertyName + ChatColor.GOLD + ": " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
		}

		this.locationManager.save();
	}
	
	/**
	 * Sets a config option (admin only)
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void setQuickTravelOption(CommandSender sender, String[] args)
	{
		if (args.length < 2)
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets a QuickTravel global option");
			sender.sendMessage("/qt cfg <option> <value>");
		}

		String option = args.length > 1 ? args[1].toLowerCase() : null;
		String value = args.length > 2 ? args[2] : null;
		
		this.getOptions().setOption(sender, option, value);

		if (args.length > 2)
		{
			this.saveConfig();
			this.locationManager.updateOptions();
		}
	}

	/**
	 * Lists quicktravels to the specified sender
	 * 
	 * @param sender Player issuing the command or console
	 * @param args Command-line arguments
	 */
	protected void listQuickTravels(CommandSender sender, String[] args)
	{
		/* "/qt list" passed Get arguments and deal with appropriately */
		if (args.length == 1)
		{
			/* No arguments, display list */
			this.listQuickTravels(sender, 1, true);
		}
		else if (args.length == 2)
		{
			/* 1 argument, should be page number display page 1 otherwise */
			try
			{
				int i = Integer.parseInt(args[1]);
				if (i <= 0)
				{
					i = 1;
				}
				this.listQuickTravels(sender, i, true);
			}
			catch (NumberFormatException e)
			{
				sender.sendMessage("'" + args[1] + "' is not a number, displaying page 1.");
				this.listQuickTravels(sender, 1, true);
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Shows a list of all QT points and related info.");
			sender.sendMessage("/qt list <page (optional)>");
		}
	}

	/**
	 * @param sender Player or console issuing the command
	 * @param page Page to display parsed from args
	 * @param listAll True to list all QT's rather than only those which are accessible
	 */
	protected void listQuickTravels(CommandSender sender, int page, boolean listAll)
	{
		if (listAll || !(sender instanceof Player))
		{
			this.displayFullList(sender, page);
		}
		else
		{
			Player player = (Player)sender;
			QuickTravelLocation origin = this.locationManager.getLocationAt(player.getLocation());
			
			if (origin == null && !this.getOptions().canQtFromAnywhere(player))
			{
				player.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
				return;
			}
		
			List<QuickTravelLocation> destinationList = new ArrayList<QuickTravelLocation>();
			
			player.sendMessage(ChatColor.BLUE + "Current Location: " + (origin != null ? ChatColor.AQUA + origin.getName() : ChatColor.GOLD + "<none>"));
			player.sendMessage(ChatColor.BLUE + "From here you can QuickTravel to:");
			
			for (QuickTravelLocation target : this.locationManager.getLocations())
			{
				if (target != origin && this.checkPlayerCanTravelFromTo(player, origin, target, target.getName(), false))
					destinationList.add(target);
			}
				
			if (destinationList.size() < 1)
			{
				player.sendMessage("You cannot QuickTravel anywhere yet.");
				return;
			}
			
			this.displayList(player, destinationList, origin, page);
		}
	}

	/**
	 * @param sender Player or console issuing the command
	 * @param page Page to display parsed from args
	 */
	private void displayFullList(CommandSender sender, int page)
	{
		List<String> fullList = new ArrayList<String>();
		if (this.locationManager.getLocationCount() > 0)
		{
			for (QuickTravelLocation qt : this.locationManager.getLocations())
			{
				fullList.add(qt.getInfo(sender));
			}
			
			int pages = (int)(Math.ceil((double)fullList.size() / (double)8));
			if (page > pages)
			{
				sender.sendMessage("There is no page " + ChatColor.GOLD + page + ChatColor.WHITE + ", displaying page 1.");
				page = 1;
			}
			
			int start = ((page - 1) * 8) + 1;
			int end = start + 7;
			int listIndex = 0;
			
			for (String listEntry : fullList)
			{
				listIndex++;
				if (listIndex >= start && listIndex <= end)
				{
					sender.sendMessage(listEntry);
				}
			}
			
			String pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages + ChatColor.WHITE + ".";
			
			if (page < pages)
			{
				int nextPage = page + 1;
				pageString += ChatColor.WHITE + " Type " + ChatColor.GOLD + "/qt list " + nextPage + ChatColor.WHITE + " to read the next page.";
			}
			
			sender.sendMessage(pageString);
		}
		else
		{
			sender.sendMessage("The list is empty.");
		}
	}
	
	/**
	 * @param player
	 * @param destinationList
	 * @param origin
	 * @param page
	 */
	private void displayList(Player player, List<QuickTravelLocation> destinationList, QuickTravelLocation origin, int page)
	{
		int pages = (int)(Math.ceil((double)destinationList.size() / (double)8));
		
		if (page > pages)
		{
			player.sendMessage("There is no page " + ChatColor.GOLD + page + ChatColor.WHITE + ", displaying page 1.");
			page = 1;
		}
		
		int start = ((page - 1) * 8) + 1;
		int end = start + 7;
		int listIndex = 0;

		for (QuickTravelLocation destination : destinationList)
		{
			listIndex++;
			
			if (listIndex >= start && listIndex <= end)
			{
				boolean inWorld = destination.isInWorld(player.getWorld());
				String worldString = inWorld ? "" : ChatColor.WHITE + "[" + ChatColor.BLUE + destination.getWorld().getName() + ChatColor.WHITE + "] ";

				double travelCost = 0.0;
				
				if (origin != null)
				{
					travelCost = this.calculateChargeFromTo(player, origin, destination);
				}
				else if (this.getOptions().canQtFromAnywhere(player))
				{
					/* Player is not at a QT location, however the player can QT from anywhere */
					travelCost = this.calculateChargeFromAnywhere(player, destination);
				}
				
				String message = worldString + ChatColor.AQUA + destination.getName();
				if (travelCost > 0)
				{
					message += " | " + ChatColor.GOLD + "Price: " + ChatColor.WHITE + EcoSetup.economy.format(travelCost);
				}

				player.sendMessage(message);
			}

		}
		
		String pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages;

		if (page < pages)
		{
			int nextPage = page + 1;
			pageString += ChatColor.WHITE + ". Type " + ChatColor.GOLD + "/qt " + nextPage + ChatColor.WHITE + " to read the next page.";
		}
		
		player.sendMessage(pageString);
	}
	
	/**
	 * @param player
	 * @return
	 */
	protected List<QuickTravelLocation> getAvailableDestinations(Player player)
	{
		List<QuickTravelLocation> destList = new ArrayList<QuickTravelLocation>();
		QuickTravelLocation qt = this.locationManager.getLocationAt(player.getLocation());
		
		if ((qt != null || this.getOptions().canQtFromAnywhere(player)) && this.locationManager.getLocationCount() > 0)
		{
			for (QuickTravelLocation target : this.locationManager.getLocations())
			{
				if (target != qt && this.checkPlayerCanTravelFromTo(player, qt, target, target.getName(), false))
				{
					destList.add(target);
				}
			}
		}
		
		return destList;
	}

	/**
	 * @param sender
	 * @param name
	 * @param command
	 */
	private boolean checkQTNameIsValid(CommandSender sender, String name, String command)
	{
		try
		{
			/* Player attempting to name a QT as a number */
			@SuppressWarnings("unused")
			int checkInt = Integer.parseInt(name);
			sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + command + ": " + ChatColor.AQUA + name + ChatColor.GOLD + " is not a valid name!");
			sender.sendMessage("Names must contain letters.");
			return false;
		}
		catch (NumberFormatException e) {}
		
		if (reservedWords.contains(name))
		{
			/* Player attempting to name a QT after a command */
			sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + command + ": " + ChatColor.AQUA + name + ChatColor.GOLD + " is not a valid name!");
			sender.sendMessage("Names must not match /qt commands.");
			return false;
		}
		
		if (this.containsLetter(name) == false)
		{
			/* Player attempting to name a QT without letters */
			sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + command + ": " + ChatColor.AQUA + name + ChatColor.GOLD + " is not a valid name!");
			sender.sendMessage("Names must contain letters.");
			return false;
		}
		
		return true;
	}
	
	/**
	 * Sanity checks for a specified journey, checks each travel criterion and returns true if everything checks out, sends an error to the player if required
	 * 
	 * @param player Player to test
	 * @param origin Origin QT, null if in wilderness
	 * @param target Target QT, should not be null but will trigger a false response if it is
	 * @param targetName Name of the target QT
	 * @param showMessage True to echo a message on failure (false when testing for other reasons such as listing QT's)
	 * @return True if travel is acceptable, false if any check fails
	 */
	private boolean checkPlayerCanTravelFromTo(Player player, QuickTravelLocation origin, QuickTravelLocation target, String targetName, boolean showMessage)
	{
		if (target == null)
		{
			// Check requested destination is defined
			if (showMessage)
			{
				player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] " + ChatColor.AQUA + targetName + ChatColor.WHITE + " does not exist.");
			}
			return false;
		}
		
		if (!target.isEnabled())
		{
			// Check requested destination is enabled
			if (showMessage)
			{
				player.sendMessage(ChatColor.AQUA + target.getName() + ChatColor.WHITE + " is disabled.");
			}
			
			return false;
		}
		
		if (!target.checkPermission(player))
		{
			if (showMessage)
			{
				player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + target.getName() + ChatColor.WHITE + "!");
			}
			return false;
		}
		
		/* Multiworld checks */
		if (target.getWorld() != player.getWorld())
		{
			/* Player not on correct world, check multiworld settings */
			if (origin != null)
			{
				/* Player is at a QT, check it */
				if (!origin.isMultiworld())
				{
					if (showMessage)
					{
						player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
					}
					return false;
				}
			}

			if (!target.isMultiworld())
			{
				if (showMessage)
				{
					player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
				}
				
				return false;
			}
		}
		
		/* Discovered checks */
		if ((target.requiresDiscovery() && !target.isDiscoveredBy(player)) || (this.getOptions().permissionsOverride() && target.hasPermission(player)))
		{
			if (showMessage)
			{
				player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + target + ChatColor.WHITE + "!");
			}
			return false;
		}
		
		return true;
	}
	
	/**
	 * Calculate a charge based on distance between origin QT and target QT for the specified player
	 * 
	 * @param player
	 * @param origin
	 * @param target
	 * @return
	 */
	private int calculateCharge(Player player, QuickTravelLocation origin, QuickTravelLocation target)
	{
		if (target != null)
		{
			return target.calculateChargeFrom(player, origin, this.getOptions().getPriceMultiplier(), this.getOptions().getMultiworldMultiplier());
		}
		
		return 0;
	}
	
	/**
	 * @param s
	 * @return
	 */
	private static boolean containsLetter(String s)
	{
		if (s == null || s.length() == 0) return false;
		return Pattern.compile("[a-z]").matcher(s).find();
	}
	
	/**
	 * @param msg
	 */
	public static void info(String msg)
	{
		log.info(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	/**
	 * @param msg
	 */
	public static void warning(String msg)
	{
		log.warning(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	/**
	 * @param msg
	 */
	public static void severe(String msg)
	{
		log.severe(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	static
	{
		// Reserved words is all the commands plus the short forms too
		reservedWords.addAll(QuickTravelCommandHandler.commands);
		reservedWords.add("name");
		reservedWords.add("greeting");
		reservedWords.add("discover");
		reservedWords.add("disc");
		reservedWords.add("perm");
		reservedWords.add("multi");
		reservedWords.add("config");
		reservedWords.add("mult");
		reservedWords.add("charge");
		reservedWords.add("hide");
		reservedWords.add("del");
		reservedWords.add("remove");
		reservedWords.add("t");
		reservedWords.add("r");
		reservedWords.add("c");
		reservedWords.add("e");
		reservedWords.add("f");
		reservedWords.add("d");
		reservedWords.add("p");
		reservedWords.add("m");
		reservedWords.add("h");
		reservedWords.add("*");
	}
}