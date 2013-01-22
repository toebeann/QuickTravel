package com.live.toadbomb.QuickTravel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Class containing settings for QuickTravel
 *
 * @author Mumfrey
 */
public class QuickTravelOptions
{
	private FileConfiguration config;
	
	/**
	 * Mapping of types to option names so that options can be parsed to appropriate types
	 */
	private static Map<String, Class<?>> optionValueTypes = new HashMap<String, Class<?>>(); 
	
	private double defaultRadius = 5;
	private int heightModifier = 2;
	private boolean enabledByDefault = true;
	private boolean requireDiscoveryByDefault = true;
	private boolean requirePermissionsByDefault = false;
	private boolean multiworldByDefault = false;
	private boolean qtFromAnywhere = false;
	private boolean enableEconomy = false;
	private boolean withdrawFromPlayerNotBank = true;
	private boolean freeByDefault = false;
	private float priceMultiplier = 0.8F;
	private float multiworldMultiplier = 1.2F;
	private float multiworldTax = 500.0F;
	private boolean freeFromQts = false;
	private boolean enableSafetyChecks = false;
	private int warmUpTicks = 20;
	private int coolDownTicks = 100;
	private boolean useGlobalPrice = false;
	private double globalPrice = 0.0;
	private boolean enableEffects = true;

	/**
	 * Load settings from the plugin config
	 */
	protected void load(FileConfiguration config)
	{
		this.config = config;
	
		this.config.addDefault("radius", 5);
		this.config.addDefault("height-modifier", 2);
		this.config.addDefault("enabled-by-default", true);
		this.config.addDefault("require-discovery-by-default", true);
		this.config.addDefault("require-permissions-by-default", false);
		this.config.addDefault("multiworld-by-default", false);
		this.config.addDefault("qt-from-anywhere", false);
		this.config.addDefault("enable-economy", false);
		this.config.addDefault("withdraw-from-player-not-bank", true);
		this.config.addDefault("free-by-default", false);
		this.config.addDefault("price-multiplier", 0.8);
		this.config.addDefault("multiworld-multiplier", 1.2);
		this.config.addDefault("price-multiplier", 500);
		this.config.addDefault("free-from-qts", false);
		this.config.addDefault("enable-safety-checks", false);
		this.config.addDefault("warmup-ticks", 20);
		this.config.addDefault("cooldown-ticks", 100);
		this.config.addDefault("use-global-price", false);
		this.config.addDefault("global-price", 0.0);
		this.config.addDefault("enable-fx", true);
		
		this.config.options().copyDefaults(true);
		
		if (this.config.get("radius-when-only-primary-set") != null)
		{
			this.config.set("radius", this.config.getDouble("radius-when-only-primary-set"));
			this.config.set("radius-when-only-primary-set", null);
		}
		if (this.config.get("locations-must-be-discovered") != null)
		{
			this.config.set("require-discovery-by-default", this.config.getBoolean("locations-must-be-discovered"));
			this.config.set("locations-must-be-discovered", null);
		}
		if (this.config.get("players-always-need-permissions") != null)
		{
			this.config.set("require-permissions-by-default", this.config.getBoolean("players-always-need-permissions"));
			this.config.set("players-always-need-permissions", null);
		}
		
		this.enableEconomy               = this.config.getBoolean("enable-economy", false);
		this.defaultRadius               = Math.min(Math.max(this.config.getDouble("radius", 5), 0.0), 64.0);
		this.heightModifier              = Math.min(Math.max(this.config.getInt("height-modifier", 2), 0), 16);
		this.enabledByDefault            = this.config.getBoolean("enabled-by-default", true);
		this.requireDiscoveryByDefault   = this.config.getBoolean("require-discovery-by-default", true);
		this.requirePermissionsByDefault = this.config.getBoolean("require-permissions-by-default", false);
		this.multiworldByDefault         = this.config.getBoolean("multiworld-by-default", false);
		this.qtFromAnywhere              = this.config.getBoolean("qt-from-anywhere", false);
		this.enableEconomy               = this.config.getBoolean("enable-economy", false);
		this.withdrawFromPlayerNotBank   = this.config.getBoolean("withdraw-from-player-not-bank", true);
		this.freeByDefault               = this.config.getBoolean("free-by-default", false);
		this.priceMultiplier             = Math.min(Math.max((float)this.config.getDouble("price-multiplier", 500.0), 0), 1000000.0F);
		this.multiworldMultiplier        = Math.min(Math.max((float)this.config.getDouble("multiworld-multiplier", 1.2), 0), 1000000.0F);
		this.multiworldTax               = Math.min(Math.max((float)this.config.getDouble("multiworld-tax", 500.0), 0), 1000000.0F);
		this.freeFromQts                 = this.config.getBoolean("free-from-qts", false);
		this.enableSafetyChecks          = this.config.getBoolean("enable-safety-checks", false);
		this.warmUpTicks                 = Math.max(this.config.getInt("warmup-ticks", 20), 0);
		this.coolDownTicks               = Math.max(this.config.getInt("cooldown-ticks", 100), 0);
		this.useGlobalPrice              = this.config.getBoolean("use-global-price", false);
		this.globalPrice                 = Math.max(this.config.getDouble("global-price", 0.0), 0.0);
		this.enableEffects               = this.config.getBoolean("enable-fx", true);
	}
	
	/**
	 * @return the enableEffects
	 */
	public boolean enableEffects()
	{
		return enableEffects;
	}

	/**
	 * @param enableEffects the enableEffects to set
	 */
	public void setEnableEffects(boolean enableEffects)
	{
		this.enableEffects = enableEffects;
	}

	/**
	 * Set an option value directly
	 * 
	 * @param sender
	 * @param option
	 * @param value
	 */
	public void setOption(CommandSender sender, String option, String value)
	{
		if (option != null && option.matches("^[a-z][a-z\\-]*$"))
		{
			String optionName = this.completeOption(sender, option);
			
			if (optionName != null && this.optionValueTypes.containsKey(optionName))
			{
				Object oValue = null;
				
				if (value != null)
				{
					Class<?> optionClass = this.optionValueTypes.get(optionName);
					oValue = this.parseValue(optionName, optionClass, value);
					
					if (oValue != null)
					{
						this.config.set(optionName, oValue);
						this.load(this.config);
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "[Error] " + ChatColor.GOLD + "The value " + ChatColor.WHITE + value + ChatColor.GOLD + " is not valid for " + ChatColor.WHITE + optionName + ChatColor.GOLD + ". Expecting value of type " + ChatColor.WHITE + optionClass.getSimpleName().toLowerCase() + ".");
					}
				}
					
				sender.sendMessage(ChatColor.GOLD + optionName + ": " + ChatColor.WHITE + this.config.getString(optionName));
			}
		}
		else
		{
			this.listOptions(sender, this.config.getKeys(false));
		}
	}

	/**
	 * "Auto-complete"s an option name
	 * 
	 * @param sender
	 * @param option
	 * @return
	 */
	private String completeOption(CommandSender sender, String option)
	{
		List<String> matchingOptions = new ArrayList<String>();
		
		for (String key : this.config.getKeys(false))
		{
			if (key.equals(option)) return key;
			if (key.startsWith(option)) matchingOptions.add(key);
		}
		
		if (matchingOptions.size() == 1)
			return matchingOptions.get(0);
		
		this.listOptions(sender, matchingOptions);
		return null;
	}

	/**
	 * @param sender
	 * @param options
	 */
	public void listOptions(CommandSender sender, Collection<String> options)
	{
		sender.sendMessage(ChatColor.BLUE + "Options:");
		
		StringBuilder optionsList = new StringBuilder();
		String separator = "";
		for (String key : options)
		{
			optionsList.append(separator).append(ChatColor.GOLD).append(key);
			separator = ChatColor.WHITE + ", ";
		}
		
		sender.sendMessage(optionsList.toString());
	}

	/**
	 * Parse a value
	 * 
	 * @param optionName
	 * @param value
	 * @return
	 */
	private Object parseValue(String optionName, Class<?> optionClass, String value)
	{
		if (optionClass.equals(Boolean.class))
		{
			if (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("enable") || value.equalsIgnoreCase("enabled")) return Boolean.valueOf(true);
			if (value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("disable") || value.equalsIgnoreCase("disabled")) return Boolean.valueOf(false);
		}
		else if (optionClass.equals(String.class))
		{
			return value;
		}
		else if (optionClass.equals(Integer.class))
		{
			try
			{
				return Integer.valueOf(Integer.parseInt(value));
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
		}
		else if (optionClass.equals(Double.class))
		{
			try
			{
				return Double.valueOf(Double.parseDouble(value));
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
		}
		else if (optionClass.equals(Float.class))
		{
			try
			{
				return Float.valueOf(Float.parseFloat(value));
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
		}

		return null;
	}

	/**
	 * @return the defaultRadius
	 */
	public double getDefaultRadius()
	{
		return defaultRadius;
	}

	/**
	 * @return the heightModifier
	 */
	public int getHeightModifier()
	{
		return heightModifier;
	}

	/**
	 * @return the enabledByDefault
	 */
	public boolean enabledByDefault()
	{
		return enabledByDefault;
	}

	/**
	 * @return the requireDiscoveryByDefault
	 */
	public boolean requireDiscoveryByDefault()
	{
		return requireDiscoveryByDefault;
	}

	/**
	 * @return the requirePermissionsByDefault
	 */
	public boolean requirePermissionsByDefault()
	{
		return requirePermissionsByDefault;
	}

	/**
	 * @return the multiworldByDefault
	 */
	public boolean isMultiworldByDefault()
	{
		return multiworldByDefault;
	}

	/**
	 * @return the qtFromAnywhere
	 */
	public boolean canQtFromAnywhere(Player player)
	{
		return qtFromAnywhere || (player != null && player.hasPermission("qt.fromanywhere"));
	}

	/**
	 * @return the enableEconomy
	 */
	public boolean enableEconomy()
	{
		return enableEconomy;
	}

	/**
	 * @return the withdrawFromPlayerNotBank
	 */
	public boolean withdrawFromPlayerNotBank()
	{
		return withdrawFromPlayerNotBank;
	}

	/**
	 * @return the freeByDefault
	 */
	public boolean isFreeByDefault()
	{
		return freeByDefault;
	}

	/**
	 * @return the priceMultiplier
	 */
	public float getPriceMultiplier()
	{
		return priceMultiplier;
	}

	/**
	 * @return the multiworldMultiplier
	 */
	public float getMultiworldMultiplier()
	{
		return multiworldMultiplier;
	}

	/**
	 * @return the multiworldTax
	 */
	public float getMultiworldTax()
	{
		return multiworldTax;
	}

	/**
	 * @return the freeFromQts
	 */
	public boolean isFreeFromQts()
	{
		return freeFromQts;
	}

	/**
	 * @return the enableSafetyChecks
	 */
	public boolean enableSafetyChecks()
	{
		return enableSafetyChecks;
	}

	/**
	 * @return the warmUpTicks
	 */
	public int getWarmUpTicks()
	{
		return warmUpTicks;
	}

	/**
	 * @param warmUpTicks the warmUpTicks to set
	 */
	public void setWarmUpTicks(int warmUpTicks)
	{
		this.warmUpTicks = warmUpTicks;
	}

	/**
	 * @return the coolDownTicks
	 */
	public int getCoolDownTicks()
	{
		return coolDownTicks;
	}

	/**
	 * @param coolDownTicks the coolDownTicks to set
	 */
	public void setCoolDownTicks(int coolDownTicks)
	{
		this.coolDownTicks = coolDownTicks;
	}

	/**
	 * @return the useGlobalPrice
	 */
	public boolean useGlobalPrice()
	{
		return useGlobalPrice;
	}

	/**
	 * @param useGlobalPrice the useGlobalPrice to set
	 */
	public void setUseGlobalPrice(boolean useGlobalPrice)
	{
		this.useGlobalPrice = useGlobalPrice;
	}

	/**
	 * @return the globalPrice
	 */
	public double getGlobalPrice()
	{
		return globalPrice;
	}

	/**
	 * @param globalPrice the globalPrice to set
	 */
	public void setGlobalPrice(double globalPrice)
	{
		this.globalPrice = globalPrice;
	}
	
	static
	{
		optionValueTypes.put("enable-economy",                 Boolean.class);                
		optionValueTypes.put("radius",                         Double.class);                             
		optionValueTypes.put("height-modifier",                Integer.class);                       
		optionValueTypes.put("enabled-by-default",             Boolean.class);             
		optionValueTypes.put("require-discovery-by-default",   Boolean.class);   
		optionValueTypes.put("require-permissions-by-default", Boolean.class);
		optionValueTypes.put("multiworld-by-default",          Boolean.class);         
		optionValueTypes.put("qt-from-anywhere",               Boolean.class);              
		optionValueTypes.put("enable-economy",                 Boolean.class);                
		optionValueTypes.put("withdraw-from-player-not-bank",  Boolean.class);  
		optionValueTypes.put("free-by-default",                Boolean.class);               
		optionValueTypes.put("price-multiplier",               Float.class);        
		optionValueTypes.put("multiworld-multiplier",          Float.class);     
		optionValueTypes.put("multiworld-tax",                 Float.class);          
		optionValueTypes.put("free-from-qts",                  Boolean.class);                 
		optionValueTypes.put("enable-safety-checks",           Boolean.class);          
		optionValueTypes.put("warmup-ticks",                   Integer.class);                         
		optionValueTypes.put("cooldown-ticks",                 Integer.class);                      
		optionValueTypes.put("use-global-price",               Boolean.class);              
		optionValueTypes.put("global-price",                   Double.class);                     		
		optionValueTypes.put("enable-fx",                      Boolean.class);              
	}
}