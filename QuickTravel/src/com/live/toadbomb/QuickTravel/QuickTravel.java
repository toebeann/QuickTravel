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
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class QuickTravel extends JavaPlugin implements Listener {
	public static QuickTravel plugin;
	public final Logger logger = Logger.getLogger("Minecraft");
	
	// Loads config files
	public File configFile;
	protected FileConfiguration config;
	private File locationsFile = null;
	private FileConfiguration locations = null;
		
	public void onDisable() {
		PluginDescriptionFile pdffile = this.getDescription();
		this.logger.info("[" + pdffile.getName() + "] has been disabled.");
	}
	
	public void onEnable() {
		PluginDescriptionFile pdffile = this.getDescription();
		this.logger.info("[" + pdffile.getName() + "] is running.");
		this.getConfig().addDefault("radius-when-only-primary-set", 5);
		this.getConfig().addDefault("height-modifier", 2);
		this.getConfig().addDefault("enabled-by-default", true);
		this.getConfig().addDefault("locations-must-be-discovered", true);
		this.getConfig().addDefault("withdraw-from-player-not-bank", true);
		this.getConfig().addDefault("players-always-need-permissions", false);
		this.getConfig().addDefault("qt-from-anywhere", false);
		this.getConfig().addDefault("free-by-default", false);
		this.getConfig().addDefault("price-multiplier", 0.8);
		this.getConfig().addDefault("free-from-qts", false);
		this.getConfig().options().copyDefaults(true);
		this.saveConfig();
		getServer().getPluginManager().registerEvents(this, this);
	    EcoSetup eco = new EcoSetup();
	    if(!eco.setupEconomy())logger.warning("[" + pdffile.getName() + "] Could not set up economy!");
	}
	
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event) {
		if(getConfig().getBoolean("locations-must-be-discovered") == true) {
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
						this.getLocations().set("locations." + getLocation(qt) + ".discovered-by", newDList);
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
			if(args.length == 1) {
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				// "/qt" has been passed with 1 argument - check to see whether 'list' command, page number or QT destination
				try {
					// Has been determined to be a list page number, throw list at player
					int i = Integer.parseInt(args[0]);
					if(i <= 0) {
						i = 1;
					}
					QTList(sender, i, false);
					return true;
				} catch(NumberFormatException e) {
					// Has been determined to be a request for 'list' command
					if(args[0].equalsIgnoreCase("list") && sender.hasPermission("qt.admin.list")) {
						QTList(sender, 1, true);
						return true;
					} else {
						// Is neither 'list' command nor page number, presume QT destination
						if(runChecks(sender, args[0])) {
							Player p = (Player)sender;
							String qt = checkPlayerQT(sender);
							if(qt != null) {
								// If player is at a valid QT point:
								if(qt.equalsIgnoreCase(args[0])) {
									// Check player isn't already at the requested destination
									sender.sendMessage(ChatColor.BLUE + "You are already at " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
									return true;
								}
								
								double c = getLocations().getDouble("locations." + getLocation(args[0]) + ".charge-from." + getLocation(qt));
								if(c > 0) {
									// If price has been set manually for this QT
									if(EcoSetup.economy.has(p.getName(), c)) {
		                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
		                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
		                            	} else {
		                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
		                            	}
		                            	QT(sender, args[0], c);
		                                return true;
		                            } else {
		                                sender.sendMessage("You do not have enough money to go there.");
		                                return true;
									}
								} else {
									// No custom price set, check whether should be free or if we should set the price
									if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
										// Calculate price for QT
										c = calculatePrice(getLocation(qt), getLocation(args[0]));
										if(EcoSetup.economy.has(p.getName(), c)) {
			                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
			                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
			                            	} else {
			                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
			                            	}
			                            	QT(sender, args[0], c);
			                                return true;
			                            } else {
			                                sender.sendMessage("You do not have enough money to go there.");
			                                return true;
										}
									} else {
										// This QT is free
										QT(sender, args[0], 0);
										return true;																	
									}
								}
							} else if(getConfig().getBoolean("qt-from-anywhere") == true) {
								if(getConfig().getBoolean("free-by-default") == false) {
									// Calculate price for QT
									int c = calculatePrice(sender, getLocation(args[0]));
									if(EcoSetup.economy.has(p.getName(), c)) {
		                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
		                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
		                            	} else {
		                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
		                            	}
		                            	QT(sender, args[0], c);
		                                return true;
		                            } else {
		                                sender.sendMessage("You do not have enough money to go there.");
		                                return true;
									}
								} else {
									// This QT is free
									QT(sender, args[0], 0);
									return true;																	
								}
							} else {
								sender.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
								return true;	
							}
						}
					}
				}
			} else if(args.length == 2) {
				if(args[0].equalsIgnoreCase("create")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You must be a player!");
						return true;
					}
					// "/qt create" has been passed
					try {
						// Player attmpting to create QT with a name that is an int
						@SuppressWarnings("unused")
						int i = Integer.parseInt(args[1]);
						sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
						sender.sendMessage("Names must contain letters.");
						return true;
					} catch(NumberFormatException e) {
						if(sender.hasPermission("qt.admin.create")) {
							boolean matchesCommand = false;
							if(!args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("range") && !args[1].equalsIgnoreCase("dest") && !args[1].equalsIgnoreCase("update") && !args[1].equalsIgnoreCase("enable") && !args[1].equalsIgnoreCase("disable") && !args[1].equalsIgnoreCase("list") && !args[1].equalsIgnoreCase("rename") && !args[1].equalsIgnoreCase("price")) {
								matchesCommand = false;
							} else {
								matchesCommand = true;
							}
							if(checkLocations(args[1]) == false && !matchesCommand) {
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
								sender.sendMessage("Travel location " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " created.");
							} else if(checkLocations(args[1]) == true) {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " already exists!");
							} else if(matchesCommand) {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
								sender.sendMessage("Names must not match /qt commands.");
							} else {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not create: " + ChatColor.GOLD + "Unexpected error!");
							}
							return true;
						} else {
							// Not authorised
							return false;
						}
					}
				} else if(args[0].equalsIgnoreCase("range")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You must be a player!");
						return true;
					}
					// "/qt range" has been passed
					if(sender.hasPermission("qt.admin.range")) {
						if(checkLocations(args[1]) == true) {
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.secondary.z", coord.getZ());
								this.saveLocations();
								sender.sendMessage("Range for location " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " set.");								
							} else {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set range: " + ChatColor.GOLD + "You are not on the correct World!");
							}
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set range: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set range: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else if(args[0].equalsIgnoreCase("dest")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You must be a player!");
						return true;
					}
					// "/qt dest" has been passed
					if(sender.hasPermission("qt.admin.dest")) {
						if(checkLocations(args[1]) == true) {
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.z", coord.getZ());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.pitch", coord.getPitch());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.dest.yaw", coord.getYaw());
								this.saveLocations();
								sender.sendMessage("Destination for location " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " set.");								
							} else {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set dest: " + ChatColor.GOLD + "You are not on the correct World!");
							}
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set dest" + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set dest: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else if(args[0].equalsIgnoreCase("update")) {
					if(!(sender instanceof Player)) {
						sender.sendMessage(ChatColor.RED + "You must be a player!");
						return true;
					}
					// "/qt update" has been passed
					if(sender.hasPermission("qt.admin.update")) {
						if(checkLocations(args[1]) == true) {
							Player p = (Player)sender;
							Location coord = p.getLocation();
							String pWorld = p.getWorld().getName();
							String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
							if(pWorld.equals(locWorld)) {
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.x", coord.getX());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.y", coord.getY());
								this.getLocations().set("locations." + getLocation(args[1]) + ".coords.primary.z", coord.getZ());
								this.saveLocations();
								sender.sendMessage("Updated primary coords of location " + ChatColor.AQUA + args[1] + ChatColor.WHITE + ".");
							} else {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not update: " + ChatColor.GOLD + "You are not on the correct World!");
							}
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not update: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not update: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else if(args[0].equalsIgnoreCase("enable")) {
					// "/qt update" has been passed
					if(sender.hasPermission("qt.admin.enable")) {
						if(checkLocations(args[1]) == true) {
							this.getLocations().set("locations." + getLocation(args[1]) + ".enabled", true);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.WHITE + " is now enabled!");
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not enable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not enable: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else if(args[0].equalsIgnoreCase("disable")) {
					// "/qt update" has been passed
					if(sender.hasPermission("qt.admin.disable")) {
						if(checkLocations(args[1]) == true) {
							this.getLocations().set("locations." + getLocation(args[1]) + ".enabled", false);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.WHITE + " is now disabled!");
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not disable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not disable: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else if(args[0].equalsIgnoreCase("list")) {
					// "/qt list" has been passed
					if(sender.hasPermission("qt.admin.list")) {
						try {
							// Has been determined to be a list page number, throw list at player
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
						// Not authorised
						return false;
					}
				} else {
					// Incorrect argument
					return false;
				}
			} else if(args.length == 3) {
				if(args[0].equalsIgnoreCase("rename")) {
					// "/qt rename" has been passed
					if(sender.hasPermission("qt.admin.rename")) {
						if(checkLocations(args[1]) == true && checkLocations(args[2]) == false) {
							sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2]);
							this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
							this.saveLocations();
						} else if(checkLocations(args[1]) == false) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not rename: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
						} else if(checkLocations(args[1]) == true && checkLocations(args[2]) == true) {
							if(getLocation(args[1]).equals(getLocation(args[2]))) {
								sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2]);
								this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
								this.saveLocations();
							} else {
								sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not rename: " + ChatColor.AQUA + getLocationName(args[2]) + ChatColor.GOLD + " already exists!");								
							}
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not rename: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else {
					// Incorrect argument
					return false;
				}
		    } else if(args.length == 4) {
		    	if(args[0].equalsIgnoreCase("price")) {
					// "/qt price" has been passed
		    		if(sender.hasPermission("qt.admin.price")) {
						if(checkLocations(args[2]) == true && checkLocations(args[3]) == true) {
							this.getLocations().set("locations." + getLocation(args[3]) + ".charge-from." + getLocation(args[2]), Double.parseDouble(args[1]));
							this.saveLocations();
							sender.sendMessage("Set charge from " + ChatColor.AQUA + args[2] + ChatColor.WHITE + " to " + ChatColor.AQUA + args[3] + ChatColor.WHITE + " to " + ChatColor.GOLD + args[1]);
						} else if(checkLocations(args[2]) != true) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set price: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " does not exist!");
						} else if(checkLocations(args[3]) != true) {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set price: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " does not exist!");
						} else {
							sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] Could not set price: " + ChatColor.GOLD + "Unexpected error!");
						}
						return true;
					} else {
						// Not authorised
						return false;
					}
				} else {
					// Incorrect argument
					return false;
				}
		    } else if(args.length > 4) {
				// Too many arguments
				return false;
			} else {
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				} else {
					// No arguments, show list of QTs
					QTList(sender, 1, false);
					return true;
				}
			}
		}
		return false;
	}
	
	public void QT(CommandSender sender, String rQT, double c) {
		if(c > 0) {
			sender.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + getLocationName(rQT) + ChatColor.BLUE + " for " + ChatColor.GOLD + c + ChatColor.BLUE + "...");	
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
								if(getConfig().getBoolean("locations-must-be-discovered") == true) {
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
					boolean dCfg = getConfig().getBoolean("locations-must-be-discovered");
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
					// If price has been set manually for this QT
					double c = getLocations().getDouble("locations." + v + ".charge-from." + getLocation(qt));
					if(c > 0) {
						sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + c);
					} else if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
						// Calculate price for QT
						c = calculatePrice(getLocation(qt), v);
						sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + (int) c);
					} else {
						sender.sendMessage(ChatColor.AQUA + getLocationName(v));
					}
				} else if(getConfig().getBoolean("free-by-default") == false) {
						// Calculate price for QT
						int c = calculatePrice(sender, v);
						sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + c);
				} else {
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
			sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
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
			sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
			QTList(sender, 1, false);
			return false;
		}
		String pWorld = p.getWorld().getName();
		String locWorld = getLocations().getString("locations." + getLocation(rQT) + ".world");
		if(!pWorld.equals(locWorld)) {
			// Check player is on correct world
			sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] You are not on the correct World!");
			return false;
		}
		boolean discovered = false;
		if(getConfig().getBoolean("locations-must-be-discovered") == true) {
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
					sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return false;
				}
			} else {
				sender.sendMessage("[" + ChatColor.RED + "ERROR" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
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
		if((getConfig().getBoolean("players-always-need-permissions") == true && p.hasPermission("qt.use." + getLocation(qt).toLowerCase())) || (getConfig().getBoolean("players-always-need-permissions") == false || getConfig().get("players-always-need-permissions") == null) || p.hasPermission("qt.use.*")) {
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
						int radius = getConfig().getInt("radius-when-only-primary-set");
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
}