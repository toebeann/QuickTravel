package com.live.toadbomb.QuickTravel;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
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
	 * Map of players to QT's, used so that we can keep track of when a player walks onto a QT and display a helpful message
	 */
	private Map<String, QuickTravelLocation> playerAt = new Hashtable<String, QuickTravelLocation>();
	
	/**
	 * FX to display when teleporting in wilderness (not from a QT) 
	 */
	private QuickTravelFX wildernessDepartureFX = new QuickTravelFX(32);

	/**
	 * Get the configured options
	 * 
	 * @return options
	 */
	public QuickTravelOptions getOptions()
	{
		return this.options;
	}
	
	@Override
	public void onEnable()
	{
		info("Initializing");
		
		/* Initialize config.yml */
		this.options = new QuickTravelOptions();
		this.options.load(this.getConfig());
		this.saveConfig();
		
		this.locationManager = new QuickTravelLocationManager(this);
		
		PluginManager pluginManager = getServer().getPluginManager();
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
	}
	
	@Override
	public void onDisable()
	{
		this.locationManager = null;
		this.options = null;
		
		this.playerAt.clear();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		String playerName = player.getName();

		QuickTravelLocation qt = this.locationManager.getLocationAt(player.getLocation());
		
		if (qt != null && qt.checkPermission(player))
		{
			boolean discovered = qt.isDiscoveredBy(player);

			if (!discovered)
			{
				qt.setDiscovered(player);
				this.locationManager.save();
				
				player.sendMessage(ChatColor.BLUE + "You have discovered " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
				player.sendMessage("Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
			}
			else
			{
				if (!qt.equals(playerAt.get(playerName)))
				{
					player.sendMessage(ChatColor.BLUE + "You are now in range of " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
					player.sendMessage("Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
				}
						
				playerAt.put(playerName, qt);
			}
		}
		else
		{
			playerAt.remove(playerName);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) 
	{
		if (event.getPlayer() != null)
			this.playerAt.remove(event.getPlayer().getName());
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		if (sender == null) return false;
		
		if (cmd.getName().equalsIgnoreCase("qt"))
		{
			if (args.length == 0)
			{
				this.listQuickTravels(sender, 1, false);
				return true;
			}
			else if (args[0].equalsIgnoreCase("create"))
			{
				/* "/qt create" passed Make sure is not being run from console */
				if (!(sender instanceof Player))
				{
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if (sender.hasPermission("qt.admin.create"))
				{
					this.createQuickTravel((Player)sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("rename") || args[0].equalsIgnoreCase("name"))
			{
				/* "/qt rename" passed */
				if (sender.hasPermission("qt.admin.rename"))
				{
					this.renameQuickTravel(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("type") || args[0].equalsIgnoreCase("t"))
			{
				/* "/qt type" passed */
				if (sender.hasPermission("qt.admin.type"))
				{
					this.setQuickTravelType(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("radius") || args[0].equalsIgnoreCase("r"))
			{
				/* "/qt radius" passed */
				if (sender.hasPermission("qt.admin.radius"))
				{
					this.setQuickTravelRadius(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("cuboid"))
			{
				/* "/qt cuboid" passed Make sure is not being run from console */
				if (!(sender instanceof Player))
				{
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if (sender.hasPermission("qt.admin.cuboid"))
				{
					this.setQuickTravelCuboid((Player)sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("move"))
			{
				/* "/qt update" passed Make sure is not being run from console */
				if (!(sender instanceof Player))
				{
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if (sender.hasPermission("qt.admin.move"))
				{
					this.moveQuickTravel((Player)sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("dest"))
			{
				/* "/qt dest" passed Make sure is not being run from console */
				if (!(sender instanceof Player))
				{
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if (sender.hasPermission("qt.admin.dest"))
				{
					this.setQuickTravelDestination((Player)sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("e"))
			{
				/* "/qt enable" passed */
				if (sender.hasPermission("qt.admin.enable"))
				{
					this.enableQuickTravel(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels((Player)sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("disable"))
			{
				/* "/qt disable" passed */
				if (sender.hasPermission("qt.admin.disable"))
				{
					this.enableQuickTravel(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("price") || args[0].equalsIgnoreCase("charge"))
			{
				/* "/qt price" passed */
				if (sender.hasPermission("qt.admin.price"))
				{
					this.setQuickTravelPrice(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("free") || args[0].equalsIgnoreCase("f"))
			{
				/* "/qt price" passed */
				if (sender.hasPermission("qt.admin.free"))
				{
					this.setQuickTravelFree(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("discovery") || args[0].equalsIgnoreCase("discover") || args[0].equalsIgnoreCase("disc") || args[0].equalsIgnoreCase("d"))
			{
				/* "/qt price" passed */
				if (sender.hasPermission("qt.admin.discovery"))
				{
					this.setQuickTravelDiscovery(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("perms") || args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("p"))
			{
				/* "/qt price" passed */
				if (sender.hasPermission("qt.admin.perms"))
				{
					this.setQuickTravelPermission(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("multiworld") || args[0].equalsIgnoreCase("multi") || args[0].equalsIgnoreCase("m"))
			{
				/* "/qt price" passed */
				if (sender.hasPermission("qt.admin.multiworld"))
				{
					this.setQuickTravelMultiworld(sender, args);
					return true;
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("list"))
			{
				if (sender.hasPermission("qt.admin.list"))
				{
					/* "/qt list" passed Get arguments and deal with appropriately */
					if (args.length == 1)
					{
						/* No arguments, display list */
						this.listQuickTravels(sender, 1, true);
						return true;
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
							return true;
						}
						catch (NumberFormatException e)
						{
							sender.sendMessage("'" + args[1] + "' is not a number, displaying page 1.");
							this.listQuickTravels(sender, 1, true);
							return true;
						}
					}
					else
					{
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Shows a list of all QT points and related info.");
						sender.sendMessage("/qt list <page (optional)>");
						return true;
					}
				}
				else
				{
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					this.listQuickTravels(sender, 1, false);
					return true;
				}
			}
			else if (args.length == 1)
			{
				if (!(sender instanceof Player))
				{
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				else
				{
					this.quickTravel(args, (Player)sender);
					return true;
				}
			}
		}
		
		return false;
	}

	/**
	 * @param args
	 * @param player
	 */
	public void quickTravel(String[] args, Player player)
	{
		try
		{
			/* Argument is a number, throw list at player */
			int page = Math.max(1, Integer.parseInt(args[0]));
			this.listQuickTravels(player, page, false);
			return;
		}
		catch (NumberFormatException e) {}
		
		QuickTravelLocation origin = this.locationManager.getLocationAt(player.getLocation());
		QuickTravelLocation target = this.locationManager.getLocationByName(args[0]); 
		
		/* Argument presumed to be a request to QT
		 * Check QT is valid */
		if (checkPlayerCanTravelFromTo(player, origin, target, args[0], true))
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
				
				/* Check economy */
				if (economyEnabled == true)
				{
					/* Economy is enabled */
					if (origin.isFree() || target.isFree())
					{
						/* One or both of these QTs are free */
						if (!origin.getWorld().equals(target.getWorld()))
						{
							/*
							 * Charge multiworld tax
							 */
							travelCost = this.getOptions().getMultiworldTax();
							if (!EcoSetup.economy.has(player.getName(), travelCost))
							{
								chargePlayer(player, travelCost);
							}
							else
							{
								/* Player does not have enough money */
								player.sendMessage("You do not have enough money to go there.");
								return;
							}
						}
					}
					else
					{
						if (target.shouldChargeFrom(origin))
						{
							/* Price has been manually set for QT */
							travelCost = target.getChargeFrom(origin);
							if (!target.getWorld().equals(origin.getWorld()))
							{
								travelCost += this.getOptions().getMultiworldTax();
							}
							
							if (travelCost > 0)
							{
								/* Check player has enough money */
								if (EcoSetup.economy.has(player.getName(), travelCost))
								{
									chargePlayer(player, travelCost);
								}
								else
								{
									/*
									 * Player does not have enough money
									 */
									player.sendMessage("You do not have enough money to go there.");
									return;
								}
							}
						}
						else
						{
							/*
							 * No custom price set, check whether it should be free or if we should set the price
							 */
							if ((this.getOptions().canQtFromAnywhere() && !this.getOptions().isFreeFromQts()) || (!this.getOptions().canQtFromAnywhere() && !this.getOptions().isFreeByDefault()))
							{
								/* QT should not be free, calculate price */
								travelCost = calculatePriceTo(player, origin, target);
								if (!target.getWorld().equals(origin.getWorld()))
								{
									travelCost += this.getOptions().getMultiworldTax();
								}
								
								/* Check player has enough money */
								if (EcoSetup.economy.has(player.getName(), travelCost))
								{
									this.chargePlayer(player, travelCost);
								}
								else
								{
									/* Player does not have enough money */
									player.sendMessage("You do not have enough money to go there.");
									return;
								}
							}
							else
							{
								/* QT should be free QT */
								if (!target.getWorld().equals(origin.getWorld()))
								{
									/* Charge multiworld tax */
									travelCost += this.getOptions().getMultiworldTax();
									if (EcoSetup.economy.has(player.getName(), travelCost))
									{
										this.chargePlayer(player, travelCost);
									}
									else
									{
										/* Player does not have enough money */
										player.sendMessage("You do not have enough money to go there.");
										return;
									}
								}
							}
						}
					}
				}
			}
			else if (this.getOptions().canQtFromAnywhere())
			{
				/* Player is not at a QT location, however QTs are enabled from anywhere */
				if (economyEnabled == true)
				{
					if (!this.getOptions().enabledByDefault())
					{
						/* Economy is enabled QTs are not free by default
						 * Check whether destination is free and calculate if not */
						if (target.isFree())
						{
							travelCost = 0.0;
							
							if (!target.getWorld().equals(player.getWorld()))
							{
								/* Charge multiworld tax */
								travelCost += this.getOptions().getMultiworldTax();
								
								/* Check player has enough money */
								if (EcoSetup.economy.has(player.getName(), travelCost))
								{
									this.chargePlayer(player, travelCost);
								}
								else
								{
									/* Player does not have enough money */
									player.sendMessage("You do not have enough money to go there.");
									return;
								}
							}
						}
						else
						{
							travelCost = calculatePriceTo(player, target);
							
							if (!target.getWorld().equals(player.getWorld()))
							{
								travelCost += this.getOptions().getMultiworldTax();
							}
							
							/* Check player has enough money */
							if (EcoSetup.economy.has(player.getName(), travelCost))
							{
								this.chargePlayer(player, travelCost);
							}
							else
							{
								/* Player does not have enough money */
								player.sendMessage("You do not have enough money to go there.");
								return;
							}
						}
					}
					else
					{
						if (!target.getWorld().equals(player.getWorld()))
						{
							travelCost += this.getOptions().getMultiworldTax();
						}
						
						/* Check player has enough money */
						if (EcoSetup.economy.has(player.getName(), travelCost))
						{
							this.chargePlayer(player, travelCost);
						}
						else
						{
							/* Player does not have enough money */
							player.sendMessage("You do not have enough money to go there.");
							return;
						}
					}
				}
			}
			else
			{
				/* Player is not at a valid location to QT */
				player.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
				return;
			}
			
			/* Economy disabled, send QT */
			this.quickTravel(player, origin, target, travelCost);
			return;
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
	 * @param player
	 * @param fee
	 */
	protected void chargePlayer(Player player, double fee)
	{
		/* Withdraw money from player */
		if (EcoSetup.economy.hasBankSupport() && !this.getOptions().withdrawFromPlayerNotBank())
		{
			EcoSetup.economy.bankWithdraw(player.getName(), fee);
		}
		else
		{
			EcoSetup.economy.withdrawPlayer(player.getName(), fee);
		}
	}
	
	public void quickTravel(Player player, QuickTravelLocation fromQT, QuickTravelLocation requestedQT, double cost)
	{
		if (player != null && requestedQT != null)
		{
			String costMessage = cost > 0 ? ChatColor.BLUE + " for " + ChatColor.GOLD + EcoSetup.economy.format(cost) : "";
			player.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + requestedQT.getName() + costMessage + ChatColor.BLUE + "...");
			
			QuickTravelFX departureFX = fromQT != null ? fromQT.getDepartureEffect() : this.wildernessDepartureFX;
			requestedQT.teleport(player, departureFX, this.getOptions().enableSafetyChecks());
		}
	}
	
	public void createQuickTravel(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length > 1)
		{
			String qtName = args[1].toLowerCase();

			if (!this.checkQTNameIsValid(player, qtName, "create"))
				return;
			
			if (this.locationManager.getLocationByName(qtName) != null)
			{
				/* QT with name chosen already exists */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + "create" + ": " + ChatColor.AQUA + qtName + ChatColor.GOLD + " already exists!");
				return;
			}
			
			/* Checks passed, create QT */
			QuickTravelLocation newQT = locationManager.createQT(qtName, player.getLocation(), this.getOptions().getDefaultRadius());
			newQT.setDiscovered(player);
			
			player.sendMessage("QT " + ChatColor.AQUA + qtName + ChatColor.WHITE + " created.");
			
			/* QT created, check optional parameters and set options. */
			if (args.length > 2)
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
								newQT.setRadius(radius);
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
							newQT.setEnabled(Boolean.valueOf(args[i + 1]));
							player.sendMessage("Enabled: " + ChatColor.GOLD + newQT.isEnabled());
							i++;
						}
						else
						{
							/* Player has not given true/false, use the default setting */
							newQT.setEnabled(this.getOptions().enabledByDefault());
							player.sendMessage("Enabled: " + ChatColor.GOLD + this.getOptions().enabledByDefault());
						}
					}
					else if (args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("-free"))
					{
						/* Set free status */
						if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
						{
							newQT.setFree(Boolean.valueOf(args[i + 1]));
							player.sendMessage("Free: " + ChatColor.GOLD + newQT.isFree());
							i++;
						}
						else
						{
							newQT.setFree(!((this.getOptions().canQtFromAnywhere() && this.getOptions().isFreeFromQts()) || this.getOptions().isFreeByDefault()));
							player.sendMessage("Free: " + ChatColor.GOLD + newQT.isFree());
						}
					}
					else if (args[i].equalsIgnoreCase("-d") || args[i].equalsIgnoreCase("-disc") || args[i].equalsIgnoreCase("-discover") || args[i].equalsIgnoreCase("-discovery"))
					{
						/* Set discovery status */
						if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
						{
							newQT.setRequiresDiscovery(Boolean.valueOf(args[i + 1]));
							player.sendMessage("Require discovery: " + ChatColor.GOLD + newQT.requiresDiscovery());
							i++;
						}
						else
						{
							/* Player has not given true/false, figure out what they want. */
							newQT.setRequiresDiscovery(!this.getOptions().requireDiscoveryByDefault());
							player.sendMessage("Require discovery: " + ChatColor.GOLD + newQT.requiresDiscovery());
						}
					}
					else if (args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("-perm") || args[i].equalsIgnoreCase("-perms"))
					{
						/* Set permissions status */
						if (args.length > i + 1 && (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false")))
						{
							newQT.setRequiresPermission(Boolean.valueOf(args[i + 1]));
							player.sendMessage("Require permissions: " + ChatColor.GOLD + newQT.requiresPermission());
							i++;
						}
						else
						{
							/* Player has not given true/false, figure out what they want. */
							newQT.setRequiresPermission(!this.getOptions().requirePermissionsByDefault());
							player.sendMessage("Require permissions: " + ChatColor.GOLD + newQT.requiresPermission());
						}
					}
					else if (args[i].equalsIgnoreCase("-m") || args[i].equalsIgnoreCase("-multi") || args[i].equalsIgnoreCase("-multiworld"))
					{
						/* Set multiworld status */
						if (args.length > i + 1)
						{
							if (args[i + 1].equalsIgnoreCase("true") || args[i + 1].equalsIgnoreCase("false"))
							{
								newQT.setMultiWorld(Boolean.valueOf(args[i + 1]));
								player.sendMessage("Multiworld: " + ChatColor.GOLD + newQT.isMultiworld());
								i++;
							}
						}
						else
						{
							/* Player has not given true/false, figure out what they want. */
							newQT.setMultiWorld(!this.getOptions().isMultiworldByDefault());
							player.sendMessage("Multiworld: " + ChatColor.GOLD + newQT.isMultiworld());
						}
					}
					else
					{
						/* Invalid parameter */
						player.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			this.locationManager.save();
			return;
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Creates a new QT at your current location.");
			player.sendMessage("/qt create <name>");
			return;
		}
	}
	
	public void renameQuickTravel(CommandSender sender, String[] args)
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
			return;
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Renames the QT <name>.");
			sender.sendMessage("/qt rename <name> <new name>");
			return;
		}
	}
	
	public void setQuickTravelType(CommandSender sender, String[] args)
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
				
				if (this.locationManager.getLocationCount() > 0)
				{
					this.locationManager.setQTType(sender, world, type);
					this.locationManager.save();
					
					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(args[1]);
				
				if (qt != null)
				{
					this.locationManager.setQTType(sender, qt, type);
					this.locationManager.save();
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
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
	
	public void setQuickTravelRadius(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean setRadius = false;
		double radius = this.getOptions().getDefaultRadius();
		
		if (args.length >= 2)
		{
			String qtName = args[1];
			
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equalsIgnoreCase("*"))
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
								i++;
							}
							catch (NumberFormatException e2)
							{
								if (args[i + 1].equalsIgnoreCase("reset"))
								{
									radius = this.getOptions().getDefaultRadius();
									setRadius = true;
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
					else
					{
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			if (qtName.equalsIgnoreCase("*"))
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
					this.locationManager.setQTRadius(sender, qt, setRadius, radius);
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
			sender.sendMessage("/qt radius <name>");
			return;
		}
	}
	
	public void setQuickTravelCuboid(Player player, String[] args)
	{
		if (args.length >= 2)
		{
			/* Get arguments and deal with appropriately */
			World world = null;
			
			boolean setPrimary = false;
			boolean setSecondary = false;
			
			String qtName = args[1];
			
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
							i++;
							
							if (world == null)
							{
								player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equalsIgnoreCase("*"))
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
					}
					else if (args[i].equalsIgnoreCase("-b") && !qtName.equals("*"))
					{
						/* -b parameter given */
						setSecondary = true;
						setPrimary = false;
					}
					else
					{
						/* Invalid parameter */
						player.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			
			if (qtName.equalsIgnoreCase("*"))
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
					if (setPrimary == true || setSecondary == true)
					{
						if (player.getWorld().equals(qt.getWorld()))
						{
							qt.setType(Type.Cuboid);
							
							/* Checks passed, set range */
							if (setPrimary)
								qt.setPrimary(player.getLocation(), false);
							else
								qt.setSecondary(player.getLocation());

							player.sendMessage(ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + qt.getType() + ChatColor.WHITE + ".");
							player.sendMessage(ChatColor.GOLD + (setPrimary ? "Primary" : "Secondary") + ChatColor.WHITE + " coords for " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " set.");
							
							if (qt.getSecondary() == null)
							{
								player.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
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
			player.sendMessage("/qt radius <name>");
			return;
		}
	}
	
	public void moveQuickTravel(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length == 2)
		{
			String qtName = args[1];
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			if (qt != null)
			{
				/* QT exists, get info and check world */
				if (qt.getType() == Type.Radius)
				{
					if (player.getWorld().equals(qt.getWorld()))
					{
						qt.setPrimary(player.getLocation(), true);
						this.locationManager.save();
						player.sendMessage("Moved QT " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + ".");
						return;
					}
					else
					{
						/* Incorrect world */
						player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: You are not on the correct World!");
						return;
					}
				}
				else
				{
					/* Is not a radius QT */
					player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + qt.getName() + ChatColor.GOLD + " is not a radius QT!");
					return;
				}
			}
			else
			{
				/* QT does not exist */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Moves the selected radius QT to your current location.");
			player.sendMessage("/qt move <name>");
			return;
		}
	}
	
	public void setQuickTravelDestination(Player player, String[] args)
	{
		/* Get arguments and deal with appropriately */
		if (args.length == 2)
		{
			String qtName = args[1];
			QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
			
			if (qt != null)
			{
				/* QT exists, get info and check world */
				if (player.getWorld().equals(qt.getWorld()))
				{
					qt.setDestination(player.getLocation());
					this.locationManager.save();
					player.sendMessage("Destination for QT " + ChatColor.AQUA + qt.getName() + ChatColor.WHITE + " set.");
					return;
				}
				else
				{
					/* Incorrect world */
					player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest: You are not on the correct World!");
					return;
				}
			}
			else
			{
				/* QT does not exist */
				player.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest" + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
				return;
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			player.sendMessage("Sets the arrival spot for the selected QT to your current location.");
			player.sendMessage("/qt dest <name>");
			return;
		}
	}
	
	public void enableQuickTravel(CommandSender sender, String[] args)
	{
		if (args.length >= 2)
		{
			/* Get arguments and deal with appropriately */
			World world = null;
			String action = args[0].toLowerCase();
			boolean newEnabled = action.equals("enable");
			boolean toggle = false;
			
			String qtName = args[1];
			
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not " + action + ": Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equalsIgnoreCase("*"))
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
							if (qtName.equalsIgnoreCase("*"))
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
	
	public void setQuickTravelPrice(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		boolean reset = false;
		double price = 0;
		
		if (args.length == 4)
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
				if (reset == false)
				{
					toQt.setChargeFrom(fromQt, price);
					sender.sendMessage("Price from " + ChatColor.AQUA + fromQt.getName() + ChatColor.WHITE + " to " + ChatColor.AQUA + toQt.getName() + ChatColor.WHITE + " set to " + ChatColor.GOLD + price + ChatColor.WHITE + ".");
				}
				else
				{
					toQt.resetChargeFrom(fromQt);
					sender.sendMessage("Price from " + ChatColor.AQUA + fromQt.getName() + ChatColor.WHITE + " to " + ChatColor.AQUA + toQt.getName() + ChatColor.WHITE + " has been reset.");
				}
				
				this.locationManager.save();
				
				if (!economyEnabled)
				{
					/* Economy is disabled, warn user */
					sender.sendMessage("[Warning] Economy is disabled, prices will have no effect.");
				}
				return;
			}
			else if (fromQt == null)
			{
				/* QT <a> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			}
			else if (toQt == null)
			{
				/* QT <b> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " does not exist!");
				return;
			}
			else
			{
				/* Unknown error */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: The specified quick travel locations do not exist!");
				return;
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the price from QT <a> to QT <b>.");
			sender.sendMessage("/qt price <a> <b> <price>");
			return;
		}
	}
	
	public void setQuickTravelFree(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean toggle = false;
		boolean free = true;
		
		if (args.length > 2)
		{
			String qtName = args[1];

			if (args[2].equalsIgnoreCase("true"))
			{
				toggle = false;
				free = true;
			}
			else if (args[2].equalsIgnoreCase("false"))
			{
				toggle = false;
				free = false;
			}
			else if (args[2].equalsIgnoreCase("toggle"))
			{
				toggle = true;
			}
			else
			{
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equalsIgnoreCase("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: No world given for -w!");
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
					this.locationManager.setQTFree(sender, world, toggle, free);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
				
				if (qt != null)
				{
					this.locationManager.setQTFree(sender, qt, toggle, free);
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether all travel to/from the selected QT is free.");
			sender.sendMessage("/qt free <name | *> <true | false | toggle>");
			return;
		}
	}
	
	public void setQuickTravelDiscovery(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean toggle = false;
		boolean requireDiscovery = true;
		
		if (args.length > 2)
		{
			String qtName = args[1];

			if (args[2].equalsIgnoreCase("true"))
			{
				toggle = false;
				requireDiscovery = true;
			}
			else if (args[2].equalsIgnoreCase("false"))
			{
				toggle = false;
				requireDiscovery = false;
			}
			else if (args[2].equalsIgnoreCase("toggle"))
			{
				toggle = true;
			}
			else
			{
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: No world given for -w!");
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
					this.locationManager.setQTRequiresDiscovery(sender, world, toggle, requireDiscovery);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
				
				if (qt != null)
				{
					this.locationManager.setQTRequiresDiscovery(sender, qt, toggle, requireDiscovery);
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: " + ChatColor.AQUA + qtName + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether discovery is required for the selected QT.");
			sender.sendMessage("/qt discovery <name | *> <true | false | toggle>");
			return;
		}
	}
	
	public void setQuickTravelPermission(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean toggle = false;
		boolean requirePermission = true;
		
		if (args.length > 2)
		{
			String qtName = args[1];

			if (args[2].equalsIgnoreCase("true"))
			{
				toggle = false;
				requirePermission = true;
			}
			else if (args[2].equalsIgnoreCase("false"))
			{
				toggle = false;
				requirePermission = false;
			}
			else if (args[2].equalsIgnoreCase("toggle"))
			{
				toggle = true;
			}
			else
			{
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: No world given for -w!");
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
					this.locationManager.setQTRequiresPermissions(sender, world, toggle, requirePermission);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
				
				if (qt != null)
				{
					this.locationManager.setQTRequiresPermissions(sender, qt, toggle, requirePermission);
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether permissions are required for the selected QT.");
			sender.sendMessage("/qt perms <name | *> <true | false | toggle>");
			return;
		}
	}
	
	public void setQuickTravelMultiworld(CommandSender sender, String[] args)
	{
		/* Get arguments and deal with appropriately */
		World world = null;
		boolean toggle = false;
		boolean multiWorld = true;
		
		if (args.length > 2)
		{
			String qtName = args[1];

			if (args[2].equalsIgnoreCase("true"))
			{
				toggle = false;
				multiWorld = true;
			}
			else if (args[2].equalsIgnoreCase("false"))
			{
				toggle = false;
				multiWorld = false;
			}
			else if (args[2].equalsIgnoreCase("toggle"))
			{
				toggle = true;
			}
			else
			{
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
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
							i++;
							
							if (world == null)
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: Specified world " + ChatColor.WHITE + args[i + 1] + ChatColor.GOLD + " was not found");
								return;
							}
						}
						else
						{
							/* No world given. */
							if (qtName.equals("*"))
							{
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: No world given for -w!");
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
					this.locationManager.setQTMultiWorld(sender, world, toggle, multiWorld);

					sender.sendMessage("Done.");
				}
				else
				{
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: You have not made any QTs yet!");
					return;
				}
			}
			else
			{
				QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
				
				if (qt != null)
				{
					this.locationManager.setQTMultiWorld(sender, qt, toggle, multiWorld);
				}
				else
				{
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
			
			this.locationManager.save();
		}
		else
		{
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether the selected QT is multiworld.");
			sender.sendMessage("/qt multiworld <name | *> <true | false | toggle>");
			return;
		}
	}
	
	public void listQuickTravels(CommandSender sender, int page, boolean listAll)
	{
		if (sender instanceof Player)
		{
			this.listQuickTravels((Player)sender, page, listAll);
		}
		else
		{
			this.displayFullList(sender, page);
		}
	}
	
	public void listQuickTravels(Player player, int page, boolean listAll)
	{
		if (listAll)
		{
			this.displayFullList(player, page);
		}
		else
		{
			QuickTravelLocation qt = this.locationManager.getLocationAt(player.getLocation());
			
			if (qt != null || this.getOptions().canQtFromAnywhere())
			{
				List<QuickTravelLocation> destList = new ArrayList<QuickTravelLocation>();
				
				player.sendMessage(ChatColor.BLUE + "Current Location: " + ChatColor.AQUA + qt);
				player.sendMessage(ChatColor.BLUE + "From here you can QuickTravel to:");
				
				if (this.locationManager.getLocationCount() > 0)
				{
					for (QuickTravelLocation target : this.locationManager.getLocations())
					{
						if (target != qt && this.checkPlayerCanTravelFromTo(player, qt, target, target.getName(), false))
						{
							destList.add(target);
						}
					}
						
					if (destList.size() < 1)
					{
						player.sendMessage("You cannot QuickTravel anywhere yet.");
					}
					else
					{
						this.displayList(player, destList, qt, page);
					}
				}
				else
				{
					player.sendMessage("You cannot QuickTravel anywhere yet.");
				}
			}
			else
			{
				player.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
			}
		}
	}

	/**
	 * @param sender
	 * @param page
	 */
	public void displayFullList(CommandSender sender, int page)
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
	
	public void displayList(Player player, List<QuickTravelLocation> destList, QuickTravelLocation qt, int page)
	{
		int pages = (int)(Math.ceil((double)destList.size() / (double)8));
		
		if (page > pages)
		{
			player.sendMessage("There is no page " + ChatColor.GOLD + page + ChatColor.WHITE + ", displaying page 1.");
			page = 1;
		}
		
		int start = ((page - 1) * 8) + 1;
		int end = start + 7;
		int listIndex = 0;

		for (QuickTravelLocation destination : destList)
		{
			listIndex++;
			
			if (listIndex >= start && listIndex <= end)
			{
				boolean inWorld = destination.isInWorld(player.getWorld());
				String worldString = inWorld ? "" : "[" + destination.getWorld().getName() + "] ";
				double travelCost = 0;
				
				if (qt != null)
				{
					/* If player is at a QT, get price from this location, if any */
					
					/* Is server running a valid economy? */
					if (economyEnabled == true)
					{
						if (destination.isFree() || qt.isFree())
						{
							/* One or both of these QTs are free, no price */
							if (!inWorld)
							{
								travelCost += this.getOptions().getMultiworldTax();
								player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
							}
							else
							{
								player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
							}
						}
						else
						{
							if (destination.shouldChargeFrom(qt))
							{
								travelCost = destination.getChargeFrom(qt);
								
								if (!inWorld)
								{
									travelCost += this.getOptions().getMultiworldTax();
								}
								
								/* If price has been manually set */
								if (travelCost > 0)
								{
									player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
								}
								else
								{
									player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE);
								}
							}
							else if ((this.getOptions().canQtFromAnywhere() && !this.getOptions().isFreeFromQts()) || (!this.getOptions().canQtFromAnywhere() && !this.getOptions().isFreeByDefault()))
							{
								/* If no price set, but server still requires payment for this QT */
								travelCost = calculatePriceTo(player, qt, destination);
								
								if (!inWorld)
								{
									travelCost += this.getOptions().getMultiworldTax();
								}
								
								player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
							}
							else
							{
								/* No price for this QT */
								if (!inWorld)
								{
									travelCost = travelCost + this.getOptions().getMultiworldTax();
									player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
								}
								else
								{
									player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
								}
							}
						}
					}
					else
					{
						/* No valid economy found, no price */
						player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
					}
				}
				else if (!this.getOptions().isFreeByDefault())
				{
					if (economyEnabled == true)
					{
						/* Player is not at a QT */
						if (destination.isFree())
						{
							/* QT is set to free, no price */
							if (!inWorld)
							{
								travelCost += this.getOptions().getMultiworldTax();
								player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
							}
							else
							{
								player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
							}
						}
						else
						{
							/* Calculate price */
							travelCost = calculatePriceTo(player, destination);
							
							if (!inWorld)
							{
								travelCost += this.getOptions().getMultiworldTax();
							}
							
							player.sendMessage(worldString + ChatColor.AQUA + destination.getName() + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(travelCost));
						}
					}
					else
					{
						/* Economy disabled */
						player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
					}
				}
				else
				{
					/* No price required or economy disabled */
					player.sendMessage(worldString + ChatColor.AQUA + destination.getName());
				}
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
	 * @param sender
	 * @param name
	 * @param command
	 */
	public boolean checkQTNameIsValid(CommandSender sender, String name, String command)
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
	
	public boolean checkPlayerCanTravelFromTo(Player player, QuickTravelLocation origin, QuickTravelLocation target, String targetName, boolean showMessage)
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
		if (target.requiresDiscovery() && !target.isDiscoveredBy(player))
		{
			if (showMessage)
			{
				player.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + target + ChatColor.WHITE + "!");
			}
			return false;
		}
		
		return true;
	}
	
	public int calculatePriceTo(Player player, QuickTravelLocation origin, QuickTravelLocation target)
	{
		if (target != null)
		{
			return target.calculateChargeFrom(player, origin, this.getOptions().getPriceMultiplier(), this.getOptions().getMultiworldMultiplier());
		}
		
		return 0;
	}
	
	public int calculatePriceTo(Player player, QuickTravelLocation target)
	{
		return this.calculatePriceTo(player, null, target);
	}
	
	public boolean playerHasPermission(Player player, String qtName)
	{
		QuickTravelLocation qt = this.locationManager.getLocationByName(qtName);
		return (qt == null) ? false : qt.checkPermission(player);
	}
	
	private static boolean containsLetter(String s)
	{
		if (s == null) return false;
		Pattern validQTNamePattern = Pattern.compile("[a-z]");
		return validQTNamePattern.matcher(s).find();
	}
	
	public static void info(String msg)
	{
		log.info(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	public static void warning(String msg)
	{
		log.warning(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	public static void severe(String msg)
	{
		log.severe(String.format("[%s] %s", LOG_PREFIX, msg));
	}
	
	static
	{
		reservedWords.add("create");
		reservedWords.add("rename");
		reservedWords.add("name");
		reservedWords.add("type");
		reservedWords.add("t");
		reservedWords.add("radius");
		reservedWords.add("r");
		reservedWords.add("cuboid");
		reservedWords.add("c");
		reservedWords.add("update");
		reservedWords.add("u");
		reservedWords.add("dest");
		reservedWords.add("enable");
		reservedWords.add("e");
		reservedWords.add("disable");
		reservedWords.add("price");
		reservedWords.add("charge");
		reservedWords.add("free");
		reservedWords.add("f");
		reservedWords.add("discovery");
		reservedWords.add("discover");
		reservedWords.add("disc");
		reservedWords.add("d");
		reservedWords.add("perms");
		reservedWords.add("perm");
		reservedWords.add("p");
		reservedWords.add("multiworld");
		reservedWords.add("multi");
		reservedWords.add("m");
		reservedWords.add("*");
	}
}