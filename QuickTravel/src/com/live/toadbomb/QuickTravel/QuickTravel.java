package com.live.toadbomb.QuickTravel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class QuickTravel extends JavaPlugin implements Listener {
	public static QuickTravel plugin;
	private static final Logger logger = Logger.getLogger("Minecraft");
	private static final String LOG_PREFIX = "[QuickTravel] ";
	public static boolean economyEnabled;
	
	Plugin Vault;
	
	public static void info(String msg) {
        logger.log(Level.INFO, LOG_PREFIX + msg);
    }
	public static void warning(String msg) {
        logger.log(Level.WARNING, LOG_PREFIX + msg);
    }
    public static void severe(String msg) {
        logger.log(Level.SEVERE, LOG_PREFIX + msg);
    }
	
	/* Loads config files */
	public File configFile;
	protected FileConfiguration config;
	private File locationsFile = null;
	private FileConfiguration locations = null;
		
	public void onDisable() {
		info("Has been disabled.");
	}
	
	public void onEnable() {
		info("Initializing");
		
		/* Initialize config.yml */
		this.getConfig().addDefault("radius", 5);
		this.getConfig().addDefault("height-modifier", 2);
		this.getConfig().addDefault("enabled-by-default", true);
		this.getConfig().addDefault("must-be-discovered", true);
		this.getConfig().addDefault("require-permissions-by-default", false);
		this.getConfig().addDefault("qt-from-anywhere", false);
		this.getConfig().addDefault("enable-economy", false);
		this.getConfig().addDefault("withdraw-from-player-not-bank", true);
		this.getConfig().addDefault("free-by-default", false);
		this.getConfig().addDefault("price-multiplier", 0.8);
		this.getConfig().addDefault("free-from-qts", false);
		this.getConfig().options().copyDefaults(true);
		this.saveConfig();
		
		PluginManager pm = getServer().getPluginManager();
		/* Check if Vault is required */
		if(getConfig().getBoolean("enable-economy") == true) {
			/* Get Vault */
			Vault = pm.getPlugin("Vault");
			if(Vault == null) {
				severe("Cannot find Vault!");
				severe("Disabling economy!");
				economyEnabled = false;
			} else {
				info("Vault has been detected");
				EcoSetup eco = new EcoSetup();
			    if(!eco.setupEconomy()) {
			    	warning("Could not set up economy!");
			    	economyEnabled = false;
			    } else {
			    	info("Using " + EcoSetup.economy.getName() + " for economy.");
			    	economyEnabled = true;
			    }
			}
		} else {
			info("Economy is disabled.");
		}
		
		this.getServer().getPluginManager().registerEvents(this, this);
		
		info("v" + this.getDescription().getVersion() + " is enabled.");
	}
	
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event) {
		if(getConfig().getBoolean("must-be-discovered") == true) {
			Player p = event.getPlayer();
			String qt = checkPlayerQT(p);
			if(qt != null && playerHasPermission(p, qt)) {
				boolean discovered = false;
				@SuppressWarnings("unchecked")
				List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(qt) + ".discovered-by");
				if(dList != null) {
					ListIterator<Object> li = dList.listIterator();
					while(li.hasNext()) {
						String v = li.next().toString();
						if(v.equalsIgnoreCase(p.getName())) {
							discovered = true;
						}
					}
				}
				if(discovered == false) {
					if(dList != null) {
						dList.add(p.getName());
					} else {
						List<Object> newDList = new ArrayList<Object>();
						newDList.add(p.getName());
						getLocations().set("locations." + getLocation(qt) + ".discovered-by", newDList);
					}
					this.saveLocations();
					p.sendMessage(ChatColor.BLUE + "You have discovered " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
					p.sendMessage("Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if(cmd.getName().equalsIgnoreCase("qt")) {
			/* Command Handling */
			if(args.length == 0) {
				/* "/qt" passed
				* Make sure is not being run from console and display list of available QTs */
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				} else {
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("create")) {
				/* "/qt create" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				/* Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.create")) {
					if(args.length == 2) {
						try {
							/* Player attempting to name a QT as a number */
							@SuppressWarnings("unused")
							int i = Integer.parseInt(args[1]);
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
							sender.sendMessage("Names must contain letters.");
							return true;
						} catch(NumberFormatException e) {
							if(!(!args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("range") && !args[1].equalsIgnoreCase("dest") && !args[1].equalsIgnoreCase("update") && !args[1].equalsIgnoreCase("enable") && !args[1].equalsIgnoreCase("disable") && !args[1].equalsIgnoreCase("list") && !args[1].equalsIgnoreCase("rename") && !args[1].equalsIgnoreCase("price"))) {
								/* Player attempting to name a QT after a command */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
								sender.sendMessage("Names must not match /qt commands.");
								return true;
							}
							if(containsLetter(args[1]) == false) {
								/* Player attempting to name a QT without letters */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
								sender.sendMessage("Names must contain letters.");
								return true;
							}
							if(checkLocations(args[1]) == true) {
								/* QT with name chosen already exists */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " already exists!");
								return true;
							}
							/* Checks passed, create QT */
							Player p = (Player)sender;
							Location coord = p.getLocation();
							this.getLocations().set("locations." + args[1] + ".world", p.getWorld().getName());
							this.getLocations().set("locations." + args[1] + ".coords.primary.x", coord.getX());
							this.getLocations().set("locations." + args[1] + ".coords.primary.y", coord.getY());
							this.getLocations().set("locations." + args[1] + ".coords.primary.z", coord.getZ());
							this.getLocations().set("locations." + args[1] + ".name", args[1]);
							
							List<Object> locList = (List<Object>) getLocations().getList("list");
							if(locList != null) {
								locList.add(args[1]);
							} else {
								List<Object> lList = new ArrayList<Object>();
								lList.add(args[1]);
								this.getLocations().set("list", lList);
							}
							
							this.saveLocations();
							sender.sendMessage("QT " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " created.");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Creates a new QT at your current location.");
						sender.sendMessage("/qt create <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("rename")) {
				/* "/qt rename" passed
				 * Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.rename")) {
					if(args.length == 3) {
						try {
							/* Player attempting to name a QT as a number */
							@SuppressWarnings("unused")
							int i = Integer.parseInt(args[1]);
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
							sender.sendMessage("Names must contain letters.");
							return true;
						} catch(NumberFormatException e) {
							if(!(!args[2].equalsIgnoreCase("create") && !args[2].equalsIgnoreCase("range") && !args[2].equalsIgnoreCase("dest") && !args[2].equalsIgnoreCase("update") && !args[2].equalsIgnoreCase("enable") && !args[2].equalsIgnoreCase("disable") && !args[2].equalsIgnoreCase("list") && !args[2].equalsIgnoreCase("rename") && !args[2].equalsIgnoreCase("price"))) {
								/* Player attempting to name a QT after a command */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid name!");
								sender.sendMessage("Names must not match /qt commands.");
								return true;
							}
							if(containsLetter(args[2]) == false) {
								/* Player attempting to name a QT without letters */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + "] Could not create: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid name!");
								sender.sendMessage("Names must contain letters.");
								return true;
							}
							if(checkLocations(args[1]) == true && checkLocations(args[2]) == false) {
								/* Checks passed, rename QT */
								this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
								this.saveLocations();
								sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2] + ChatColor.WHITE + ".");
								return true;
							} else if(checkLocations(args[1]) == false) {
								/* QT does not exist */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
								return true;
							} else if(checkLocations(args[1]) == true && checkLocations(args[2]) == true) {
								if(getLocation(args[1]).equals(getLocation(args[2]))) {
									/* Checks passed, rename QT */
									this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
									this.saveLocations();
									sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2] + ChatColor.WHITE + ".");
									return true;
								} else {
									/* QT with name chosen already exists */
									sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + getLocationName(args[2]) + ChatColor.GOLD + " already exists!");
									return true;
								}
							} else {
								/* Unknown error */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: Unexpected error!");
								return true;
							}
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Renames the selected QT.");
						sender.sendMessage("/qt rename <name> <new name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("range")) {
				/* "/qt range" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				/* Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.range")) {
					if(args.length == 2) {
						if(checkLocations(args[1]) == true) {
							/* QT exists, get info and check world */
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								/* Checks passed, set range */
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.z", coord.getZ());
								this.saveLocations();
								sender.sendMessage("Range for QT " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " set.");
								return true;
							} else {
								/* Incorrect world */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set range: You are not on the correct World!");
								return true;
							}
						} else if(checkLocations(args[1]) == false) {
							/* QT does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set range: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
							return true;
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set range: Unexpected error!");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Transforms the QT into a cuboid and sets the far corner to your current location.");
						sender.sendMessage("/qt range <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("dest")) {
				/* "/qt dest" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				/* Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.dest")) {
					if(args.length == 2) {
						if(checkLocations(args[1]) == true) {
							/* QT exists, get info and check world */
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								/* Checks passed, set dest */
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.z", coord.getZ());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.pitch", coord.getPitch());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.yaw", coord.getYaw());
								this.saveLocations();
								sender.sendMessage("Destination for QT " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " set.");
								return true;
							} else {
								/* Incorrect world */
								sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] Could not set dest: You are not on the correct World!");
								return true;
							}
						} else if(checkLocations(args[1]) == false) {
							/* QT does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest" + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
							return true;
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest: Unexpected error!");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Sets the arrival point for when users warp to the selected QT to your current location.");
						sender.sendMessage("/qt dest <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("update")) {
				/* "/qt update" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				/* Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.update")) {
					if(args.length == 2) {
						if(checkLocations(args[1]) == true) {
							/* QT exists, get info and check world */
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								/* Checks passed, set coords */
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.z", coord.getZ());
								this.saveLocations();
								sender.sendMessage("Updated primary coords for QT " + ChatColor.AQUA + args[1] + ChatColor.WHITE + ".");
								return true;
							} else {
								/* Incorrect world */
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not update: You are not on the correct World!");
								return true;
							}
						} else if(checkLocations(args[1]) == false) {
							/* QT does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not update: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
							return true;
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + "] Could not update: Unexpected error!");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Updates the primary coordinates of the selected QT to your current location.");
						sender.sendMessage("/qt update <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("enable")) {
				/* "/qt enable" passed
				 * Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.enable")) {
					if(args.length == 2) {
						if(checkLocations(args[1]) == true) {
							/* QT exists, enable it */
							this.getLocations().set("locations." + getLocation(args[1]) + ".enabled", true);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.WHITE + " is now enabled!");
							return true;
						} else if(checkLocations(args[1]) == false) {
							/* QT does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
							return true;
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: Unexpected error!");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Enables the selected QT.");
						sender.sendMessage("/qt enable <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("disable")) {
				/* "/qt disable" passed
				 * Get arguments and deal with appropriately */
				if(sender.hasPermission("qt.admin.disable")) {
					if(args.length == 2) {
						if(checkLocations(args[1]) == true) {
							/* QT exists, disable it */
							this.getLocations().set("locations." + getLocation(args[1]) + ".enabled", false);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.WHITE + " is now disabled!");
							return true;
						} else if(checkLocations(args[1]) == false) {
							/* QT does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
							return true;
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.WHITE + " Could not disable: Unexpected error!");
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Disables the selected QT.");
						sender.sendMessage("/qt disable <name>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("list")) { 
				if(sender.hasPermission("qt.admin.list")) {
					/* "/qt list" passed
					 * Get arguments and deal with appropriately */
					if(args.length == 1) {
						/* No arguments, display list */
						QTList(sender, 1, true);
						return true;
					} else if(args.length == 2) {
						/* 1 argument, should be page number
						 * Display page 1 otherwise */
						try {
							int i = Integer.parseInt(args[1]);
							if(i <= 0) {
								i = 1;
							}
							QTList(sender, i, true);
							return true;
						} catch(NumberFormatException e) {
							sender.sendMessage("'" + args[1] + "' is not a number, displaying page 1.");
							QTList(sender, 1, true);
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Shows a list of all QT points and related info.");
						sender.sendMessage("/qt list <page (optional)>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args[0].equalsIgnoreCase("price")) {
				/* "/qt price" passed
				 * Get arguments and deal with appropriately */
	    		if(sender.hasPermission("qt.admin.price")) {
	    			if(args.length == 4) {
	    				if(checkLocations(args[2]) == true && checkLocations(args[3]) == true) {
	    					/* QTs exist, set price */
							this.getLocations().set("locations." + getLocation(args[3]) + ".charge-from." + getLocation(args[2]), Double.parseDouble(args[1]));
							this.saveLocations();
							sender.sendMessage("Set price from " + ChatColor.AQUA + args[2] + ChatColor.WHITE + " to " + ChatColor.AQUA + args[3] + ChatColor.WHITE + " to " + ChatColor.GOLD + args[1]);
							if(economyEnabled == false) {
								/* Economy is disabled, warn user */
								sender.sendMessage("[Warning] Economy is disabled, prices will have no effect.");
							}
						} else if(checkLocations(args[2]) != true) {
							/* QT <a> does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " does not exist!");
						} else if(checkLocations(args[3]) != true) {
							/* QT <b> does not exist */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " does not exist!");
						} else {
							/* Unknown error */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: Unexpected error!");
						}
						return true;
	    			} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Sets the price from QT <a> to QT <b>.");
						sender.sendMessage("/qt price <price> <a> <b>");
						return true;
					}
				} else {
					/* Not authorised */
					return false;
				}
			} else if(args.length == 1) {
				/* "/qt" passed with 1 argument 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				try {
					/* Argument is a number, throw list at player */
					int i = Integer.parseInt(args[0]);
					if(i <= 0) {
						i = 1;
					}
					QTList(sender, i, false);
					return true;
				} catch(NumberFormatException e) {
					/* Argument presumed to be a request to QT
					 * Check QT is valid */
					if(runChecks(sender, args[0]) == true) {
						/* QT is valid, gather info and
						 * prepare to send QT */
						Player p = (Player)sender;
						String qt = checkPlayerQT(sender);
						if(qt != null) {
							/* Player is at a QT location */
							if(qt.equalsIgnoreCase(args[0])) {
								/* Player is already at the requested QT, do not send */
								sender.sendMessage(ChatColor.BLUE + "You are already at " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
								return true;
							}
							/* Check economy */
							double c = getLocations().getDouble("locations." + getLocation(args[0]) + ".charge-from." + getLocation(qt));
							if(economyEnabled == true) {
								/* Economy is enabled */
								if(getLocations().get("locations." + getLocation(args[0]) + ".charge-from." + getLocation(qt)) != null) {
									/* Price has been manually set for QT */
									if(c > 0) {
										/* Check player has enough money */
										if(EcoSetup.economy.has(p.getName(), c)) {
											/* Withdraw money from player */
			                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
			                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
			                            	} else {
			                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
			                            	}
			                            	/* Send QT */
			                            	QT(sender, args[0], c);
			                                return true;
			                            } else {
			                            	/* Player does not have enough money */
			                                sender.sendMessage("You do not have enough money to go there.");
			                                return true;
										}
									} else {
										/* Send QT */
										QT(sender, args[0], c);
		                                return true;
									}
								} else {
									/* No custom price set, check whether it should be free
									 * or if we should set the price */
									if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
										/* QT should not be free, calculate price */
										c = calculatePrice(getLocation(qt), getLocation(args[0]));
										/* Check player has enough money */
										if(EcoSetup.economy.has(p.getName(), c)) {
											/* Withdraw money from player */
			                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
			                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
			                            	} else {
			                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
			                            	}
			                            	/* Send QT */
			                            	QT(sender, args[0], c);
			                                return true;
			                            } else {
			                            	/* Player does not have enough money */
			                                sender.sendMessage("You do not have enough money to go there.");
			                                return true;
										}
									} else {
										/* QT should be free, send QT */
										QT(sender, args[0], 0);
										return true;																	
									}
								}
							} else {
								/* Economy is disabled, do not charge and send QT */
								QT(sender, args[0], 0);
								return true;	
							}
						} else if(getConfig().getBoolean("qt-from-anywhere") == true) {
							/* Player is not at a QT location,
							 * however QTs are enabled from anywhere */
							if(getConfig().getBoolean("free-by-default") == false && economyEnabled == true) {
								/* Economy is enabled
								 * QT should not be free, calculate price */
								int c = calculatePrice(sender, getLocation(args[0]));
								/* Check player has enough money */
								if(EcoSetup.economy.has(p.getName(), c)) {
									/* Withdraw money from player */
	                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
	                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
	                            	} else {
	                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
	                            	}
	                            	/* Send QT */
	                            	QT(sender, args[0], c);
	                                return true;
	                            } else {
	                            	/* Player does not have enough money */
	                                sender.sendMessage("You do not have enough money to go there.");
	                                return true;
								}
							} else {
								/* No price required or economy disabled, send QT */
								QT(sender, args[0], 0);
								return true;																	
							}
						} else {
							/* Player is not at a valid location to QT */
							sender.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
							return true;	
						}
					} else {
						/* It has been determined that this is an invalid QT.
						 * runChecks() will have reported any error messages
						 * so do nothing */
						return true;
					}
				}
			} else {
				/* Invalid QT command has been passed. */
				return false;
			}
		}
		return false;
	}
	
	public void QT(CommandSender sender, String rQT, double c) {
		if(c > 0) {
			sender.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + getLocationName(rQT) + ChatColor.BLUE + " for " + ChatColor.GOLD + EcoSetup.economy.format(c) + ChatColor.BLUE + "...");	
		} else {
			sender.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + getLocationName(rQT) + ChatColor.BLUE + "...");
		}
		
		Player p = (Player)sender;
        World w = p.getWorld();
        if(getLocations().getString("locations." + getLocation(rQT) + ".coords.dest") != null) {
          double x = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.x");
          double y = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.y");
          double z = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.z");
          float pitch = getLocations().getInt("locations." + getLocation(rQT) + ".coords.dest.pitch");
          float yaw = getLocations().getInt("locations." + getLocation(rQT) + ".coords.dest.yaw");
          Location dest = new Location(w, x, y, z, yaw, pitch);
          p.teleport(dest);
        } else {
          double x = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.x");
          double y = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.y");
          double z = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.z");
          Location dest = new Location(w, x, y, z);
          p.teleport(dest);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTList(CommandSender sender, int page, boolean listAll) {
		if(listAll == false) {
			Player p = (Player)sender;
			String pWorld = p.getWorld().getName();
			String qt = checkPlayerQT(sender);
			if(qt != null || getConfig().getBoolean("qt-from-anywhere") == true) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				List<Object> destList = new ArrayList<Object>();
				if(qt != null) {
					sender.sendMessage(ChatColor.BLUE + "Current Location: " + ChatColor.AQUA + qt);
				}
				sender.sendMessage(ChatColor.BLUE + "From here you can QuickTravel to:");
				if(locList != null) {
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String v = li.next().toString();
						if((getLocations().get("locations." + v + ".enabled") == null && getConfig().getBoolean("enabled-by-default") == true) || getLocations().getBoolean("locations." + v + ".enabled") == true) {
							String w = getLocations().getString("locations." + v + ".world");
							if(pWorld.equals(w) && qt != getLocationName(v)) {
								if(getConfig().getBoolean("must-be-discovered") == true) {
									List<Object> newDList = (List<Object>) getLocations().getList("locations." + v + ".discovered-by");
									if(newDList != null) {
										ListIterator<Object> ndli = newDList.listIterator();
										while(ndli.hasNext()) {
											String ndv = ndli.next().toString();
											if(ndv.equalsIgnoreCase(sender.getName())) {
												destList.add(v);
											}
										}
									}
								} else {
									destList.add(v);
								}
							}
						}
					}
					if(destList.size() <= 0) {
						sender.sendMessage("You cannot QuickTravel anywhere yet.");
					} else {
						displayList(sender, destList, qt, page);
					}
				} else {
					sender.sendMessage("You cannot QuickTravel anywhere yet.");
				}
			} else {
				sender.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
			}	
		} else {
			List<Object> fullList = new ArrayList<Object>();
			List<Object> locList = (List<Object>) getLocations().getList("list");
			if(locList != null) {
				ListIterator<Object> li = locList.listIterator();
				while(li.hasNext()) {
					String v = li.next().toString();
					String w = getLocations().getString("locations." + v + ".world");
					String e = null;
					boolean eState = false;
					ChatColor eColour = ChatColor.WHITE;
					String d = null;
					boolean dState = false;
					boolean dCfg = getConfig().getBoolean("must-be-discovered");
					ChatColor dColour = ChatColor.WHITE;
					if(getLocations().get("locations." + v + ".enabled") != null) {
						eState = getLocations().getBoolean("locations." + v + ".enabled");
					} else {
						eState = getConfig().getBoolean("enabled-by-default");
					}
					if(eState == true) {
						e = "Enabled";
						eColour = ChatColor.GREEN;
					} else {
						e = "Disabled";
						eColour = ChatColor.RED;
					}
					List<Object> discoveredList = (List<Object>) getLocations().getList("locations." + v + ".discovered-by");
					if(discoveredList != null) {
						ListIterator<Object> dli = discoveredList.listIterator();
						while(dli.hasNext()) {
							String dv = dli.next().toString();
							if(dv.equalsIgnoreCase(sender.getName())) {
								dState = true;
							}
						}
					}
					if(dCfg == false) {
						if(dState == true) {
							d = "Discovered";
							dColour = ChatColor.GRAY;
						} else {
							d = "Undiscovered";
							dColour = ChatColor.DARK_GRAY;
						}
					} else { 
						if(dState == true) {
							d = "Discovered";
							dColour = ChatColor.GOLD;
						} else {
							d = "Undiscovered";
							dColour = ChatColor.GRAY;
						}
					}
					String x = ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | [" + w + "] | " + eColour + e + ChatColor.WHITE + " | " + dColour + d;
					fullList.add(x);
				}
				double dpages = Math.ceil((double)fullList.size() / (double)8);
				int pages = (int) dpages;
				if(page > pages) {
					sender.sendMessage("There is no page " + page + ", displaying page 1.");
					page = 1;
				}
				int start = ((page - 1) * 8) + 1;
				int end = start + 7;
				int n = 0;
				ListIterator<Object> fullLI = fullList.listIterator();
				while(fullLI.hasNext()) {
					String v = fullLI.next().toString();
					n++;
					if(n >= start && n <= end) {
						sender.sendMessage(v);
					}
				}
				String pageString = null;
				if(page < pages) {
					int nextPage = page + 1;
					pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages + ChatColor.WHITE + ". Type " + ChatColor.GOLD + "/qt list " + nextPage + ChatColor.WHITE + " to read the next page.";	
				} else {
					pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages;
				}
				sender.sendMessage(pageString);
			} else {
				sender.sendMessage("The list is empty.");
			}
		}
	}
	
	public void displayList(CommandSender sender, List<Object> destList, String qt, int page) {
		double dpages = Math.ceil((double)destList.size() / (double)8);
		int pages = (int) dpages;
		if(page > pages) {
			sender.sendMessage("There is no page " + page + ", displaying page 1.");
			page = 1;
		}
		int start = ((page - 1) * 8) + 1;
		int end = start + 7;
		int n = 0;
		ListIterator<Object> destLI = destList.listIterator();
		while(destLI.hasNext()) {
			String v = destLI.next().toString();
			n++;
			if(n >= start && n <= end) {
				if(qt != null) {
					/* If player is at a QT, get price from this location, if any */
					double c = getLocations().getDouble("locations." + v + ".charge-from." + getLocation(qt));
					/* Is server running a valid economy? */
					if(economyEnabled == true) {
						if(getLocations().get("locations." + v + ".charge-from." + getLocation(qt)) != null) {
							/* If price has been manually set */
							if(c > 0) {
								sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
							} else {
								sender.sendMessage(ChatColor.AQUA + getLocationName(v));
							}
						} else if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
							/* If no price set, but server still requires payment for this QT */
							c = calculatePrice(getLocation(qt), v);
							sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
						} else {
							/* No price for this QT */
							sender.sendMessage(ChatColor.AQUA + getLocationName(v));
						}
					} else {
						/* No valid economy found, no price */
						sender.sendMessage(ChatColor.AQUA + getLocationName(v));
					}
				} else if(getConfig().getBoolean("free-by-default") == false && economyEnabled == true) {
						/* Player is not at a QT. Calculate price. */
						int c = calculatePrice(sender, v);
						sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
				} else {
					/* No price required or economy disabled */
					sender.sendMessage(ChatColor.AQUA + getLocationName(v));
				}
			}
		}
		String pageString = null;
		if(page < pages) {
			int nextPage = page + 1;
			pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages + ChatColor.WHITE + ". Type " + ChatColor.GOLD + "/qt " + nextPage + ChatColor.WHITE + " to read the next page.";	
		} else {
			pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages;
		}
		sender.sendMessage(pageString);
	}
	
	@SuppressWarnings("unchecked")
	public boolean runChecks(CommandSender sender, String rQT) {
		if(checkLocations(rQT) == false) {
			// Check requested destination is valid
			sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
			QTList(sender, 1, false);
			return false;
		}
		if(!((getLocations().get("locations." + getLocation(rQT) + ".enabled") == null && getConfig().getBoolean("enabled-by-default") == true) || getLocations().getBoolean("locations." + getLocation(rQT) + ".enabled") == true)) {
			// Check requested destination is enabled
			sender.sendMessage(ChatColor.AQUA + getLocationName(rQT) + ChatColor.WHITE + " is disabled.");
			QTList(sender, 1, false);
			return false;
		}
		Player p = (Player)sender;
		if(!playerHasPermission(p, rQT.toLowerCase())) {
			// Check player has permission to use the requested QT
			sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
			QTList(sender, 1, false);
			return false;
		}
		String pWorld = p.getWorld().getName();
		String locWorld = getLocations().getString("locations." + getLocation(rQT) + ".world");
		if(!pWorld.equals(locWorld)) {
			// Check player is on correct world
			sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
			return false;
		}
		boolean discovered = false;
		if(getConfig().getBoolean("must-be-discovered") == true) {
			// If locations must be discovered, check player has discovered it
			List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(rQT) + ".discovered-by");
			if(dList != null) {
				ListIterator<Object> dli = dList.listIterator();
				while(dli.hasNext()) {
					String dv = dli.next().toString();
					if(dv.equalsIgnoreCase(sender.getName())) {
						discovered = true;
						break;
					}
				}
				if(discovered == false) {
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return false;
				}
			} else {
				sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
				QTList(sender, 1, false);
				return false;
			}
		}
		return true;
	}
	
	public int calculatePrice(String from, String to) {
		double xFrom = 0;
		double yFrom = 0;
		double zFrom = 0;
		double xTo = 0;
		double yTo = 0;
		double zTo = 0;
		if(getLocations().getString("locations." + from + ".coords.dest") != null) {
			xFrom = getLocations().getDouble("locations." + from + ".coords.dest.x");
			yFrom = getLocations().getDouble("locations." + from + ".coords.dest.y");
			zFrom = getLocations().getDouble("locations." + from + ".coords.dest.z");      
		} else {
			xFrom = getLocations().getInt("locations." + from + ".coords.primary.x");
			yFrom = getLocations().getInt("locations." + from + ".coords.primary.y");
			zFrom = getLocations().getInt("locations." + from + ".coords.primary.z");
		}
		if(getLocations().getString("locations." + to + ".coords.dest") != null) {
			xTo = getLocations().getDouble("locations." + to + ".coords.dest.x");
			yTo = getLocations().getDouble("locations." + to + ".coords.dest.y");
			zTo = getLocations().getDouble("locations." + to + ".coords.dest.z");      
		} else {
			xTo = getLocations().getInt("locations." + to + ".coords.primary.x");
			yTo = getLocations().getInt("locations." + to + ".coords.primary.y");
			zTo = getLocations().getInt("locations." + to + ".coords.primary.z");
		}
		double xDiff = calculateDiff(xFrom, xTo); 
		double yDiff = calculateDiff(yFrom, yTo);
		double zDiff = calculateDiff(zFrom, zTo);
		double m = getConfig().getDouble("price-multiplier");
		return (int) Math.ceil((xDiff + yDiff + zDiff) * m);
	}
	
	public int calculatePrice(CommandSender sender, String to) {
		Player p = (Player)sender;
		Location coord = p.getLocation();
		double xFrom = coord.getX();
		double yFrom = coord.getY();
		double zFrom = coord.getZ();
		double xTo = 0;
		double yTo = 0;
		double zTo = 0;
		if(getLocations().getString("locations." + to + ".coords.dest") != null) {
			xTo = getLocations().getDouble("locations." + to + ".coords.dest.x");
			yTo = getLocations().getDouble("locations." + to + ".coords.dest.y");
			zTo = getLocations().getDouble("locations." + to + ".coords.dest.z");      
		} else {
			xTo = getLocations().getInt("locations." + to + ".coords.primary.x");
			yTo = getLocations().getInt("locations." + to + ".coords.primary.y");
			zTo = getLocations().getInt("locations." + to + ".coords.primary.z");
		}
		double xDiff = calculateDiff(xFrom, xTo); 
		double yDiff = calculateDiff(yFrom, yTo);
		double zDiff = calculateDiff(zFrom, zTo);
		double m = getConfig().getDouble("price-multiplier");
		return (int) Math.ceil((xDiff + yDiff + zDiff) * m);
	}
	
	public double calculateDiff(double n1, double n2) {
		if(n1 >= 0 && n2 >= 0) {
			return Math.max(n1, n2) - Math.min(n1, n2);
		} else if(n1 < 0 && n2 < 0) {
			double x1 = -n1;
			double x2 = -n2;
			return Math.max(x1, x2) - Math.min(x1, x2);
		} else if(n1 < 0) {
			return -n1 + n2; 
		} else if(n2 < 0) {
			return n1 + -n2;
		} else {
			// Not sure what went wrong here...
			return 0;
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getLocation(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(vName.equalsIgnoreCase(locName)) {
					return v;
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public String getLocationName(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(v.equalsIgnoreCase(locName)) {
					return vName;
				}
			}
		}
		return null;
	}

	public boolean playerHasPermission(Player p, String qt) {
		if((getConfig().getBoolean("require-permissions-by-default") == true && p.hasPermission("qt.use." + getLocation(qt).toLowerCase())) || (getConfig().getBoolean("require-permissions-by-default") == false || getConfig().get("require-permissions-by-default") == null) || p.hasPermission("qt.use.*")) {
			return true;
		} else {
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	public boolean checkLocations(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(vName.equalsIgnoreCase(locName)) {
					return true;
				}
			}
			return false;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public String checkPlayerQT(CommandSender sender) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				if((getLocations().get("locations." + v + ".enabled") == null && getConfig().getBoolean("enabled-by-default") == true) || getLocations().getBoolean("locations." + v + ".enabled") == true) {
					if(getLocations().getString("locations." + v + ".coords.secondary") == null && getLocations().getString("locations." + v + ".coords.primary") != null ) {
						// Primary Coords set, no Secondary Coords.
						int radius = getConfig().getInt("radius");
						int yMod = getConfig().getInt("height-modifier");
						String w = getLocations().getString("locations." + v + ".world");
						int x = getLocations().getInt("locations." + v + ".coords.primary.x");
						int y = getLocations().getInt("locations." + v + ".coords.primary.y");
						int z = getLocations().getInt("locations." + v + ".coords.primary.z");
						Player p = (Player)sender;
						Location coord = p.getLocation();
						String pWorld = p.getWorld().getName();
						
						if((pWorld.equals(w)) && (coord.getX() >= x-radius && coord.getX() <= x+radius) && (coord.getZ() >= z-radius && coord.getZ() <= z+radius) && (coord.getY() >= y-yMod && coord.getY() <= y+yMod) && (playerHasPermission(p, v))) {
							return getLocationName(v);
						}
					} else if(getLocations().getString("locations." + v + ".coords.secondary") != null && getLocations().getString("locations." + v + ".coords.primary") != null ) {
						// Primary & Secondary Coords set.
						int yMod = getConfig().getInt("height-modifier");
						String w = getLocations().getString("locations." + v + ".world");
						int x1 = getLocations().getInt("locations." + v + ".coords.primary.x");
						int y1 = getLocations().getInt("locations." + v + ".coords.primary.y");
						int z1 = getLocations().getInt("locations." + v + ".coords.primary.z");
						int x2 = getLocations().getInt("locations." + v + ".coords.secondary.x");
						int y2 = getLocations().getInt("locations." + v + ".coords.secondary.y");
						int z2 = getLocations().getInt("locations." + v + ".coords.secondary.z");
						Player p = (Player)sender;
						Location coord = p.getLocation();
						String pWorld = p.getWorld().getName();
						
						if((pWorld.equals(w)) && ((coord.getX() >= x1 && coord.getX() <= x2) || (coord.getX() <= x1 && coord.getX() >= x2)) && ((coord.getZ() >= z1 && coord.getZ() <= z2) || (coord.getZ() <= z1 && coord.getZ() >= z2)) && ((coord.getY() >= y1-yMod && coord.getY() <= y2+yMod) || (coord.getY() <= y1+yMod && coord.getY() >= y2-yMod)) && (playerHasPermission(p, v))) {
							return getLocationName(v);
						}
					} else {
						// All other cases.
						sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " is broken!");
						return null;
					}
				}
			}
			return null;
		} else {
			// Empty list
			return null;
		}
	}
	
	public void reloadLocations() {
		if(locationsFile == null) {
			locationsFile = new File(getDataFolder(), "locations.yml");
		}
		locations = YamlConfiguration.loadConfiguration(locationsFile);
		
		// Look for defaults in the jar
		InputStream defLocationsStream = getResource("locations.yml");
		if(defLocationsStream != null) {
			YamlConfiguration defLocations = YamlConfiguration.loadConfiguration(defLocationsStream);
			locations.setDefaults(defLocations);
		}
	}
	
	public FileConfiguration getLocations() {
		if(locations == null) {
			reloadLocations();
		}
		return locations;
	}
	
	public void saveLocations() {
		if(locations == null || locationsFile == null) {
			return;
		}
		try {
			locations.save(locationsFile);
		} catch (IOException ex) {
			Logger.getLogger(JavaPlugin.class.getName()).log(Level.SEVERE, "Could not save config to " + locationsFile, ex);
		}
	}
	
	public boolean containsLetter(String s) {
		if(s == null)
			return false;
		boolean letterFound = false;
		for (int i = 0; !letterFound && i < s.length(); i++)
		letterFound = letterFound
				|| Character.isLetter(s.charAt(i));
		return letterFound;
		} 
}