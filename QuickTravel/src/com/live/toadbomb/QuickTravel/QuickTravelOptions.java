package com.live.toadbomb.QuickTravel;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Class containing settings for QuickTravel
 *
 * @author Mumfrey
 */
public class QuickTravelOptions
{
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

	/**
	 * Load settings from the plugin config
	 */
	protected void load(FileConfiguration config)
	{
		config.addDefault("radius", 5);
		config.addDefault("height-modifier", 2);
		config.addDefault("enabled-by-default", true);
		config.addDefault("require-discovery-by-default", true);
		config.addDefault("require-permissions-by-default", false);
		config.addDefault("multiworld-by-default", false);
		config.addDefault("qt-from-anywhere", false);
		config.addDefault("enable-economy", false);
		config.addDefault("withdraw-from-player-not-bank", true);
		config.addDefault("free-by-default", false);
		config.addDefault("price-multiplier", 0.8);
		config.addDefault("multiworld-multiplier", 1.2);
		config.addDefault("price-multiplier", 500);
		config.addDefault("free-from-qts", false);
		config.addDefault("enable-safety-checks", false);
		config.addDefault("warmup-ticks", 20);
		config.addDefault("cooldown-ticks", 100);
		config.addDefault("use-global-price", false);
		config.addDefault("global-price", 0.0);
		
		config.options().copyDefaults(true);
		
		if (config.get("radius-when-only-primary-set") != null)
		{
			config.set("radius", config.getDouble("radius-when-only-primary-set"));
			config.set("radius-when-only-primary-set", null);
		}
		if (config.get("locations-must-be-discovered") != null)
		{
			config.set("require-discovery-by-default", config.getBoolean("locations-must-be-discovered"));
			config.set("locations-must-be-discovered", null);
		}
		if (config.get("players-always-need-permissions") != null)
		{
			config.set("require-permissions-by-default", config.getBoolean("players-always-need-permissions"));
			config.set("players-always-need-permissions", null);
		}
		
		this.enableEconomy               = config.getBoolean("enable-economy", false);
		this.defaultRadius               = config.getDouble("radius", 5);
		this.heightModifier              = config.getInt("height-modifier", 2);
		this.enabledByDefault            = config.getBoolean("enabled-by-default", true);
		this.requireDiscoveryByDefault   = config.getBoolean("require-discovery-by-default", true);
		this.requirePermissionsByDefault = config.getBoolean("require-permissions-by-default", false);
		this.multiworldByDefault         = config.getBoolean("multiworld-by-default", false);
		this.qtFromAnywhere              = config.getBoolean("qt-from-anywhere", false);
		this.enableEconomy               = config.getBoolean("enable-economy", false);
		this.withdrawFromPlayerNotBank   = config.getBoolean("withdraw-from-player-not-bank", true);
		this.freeByDefault               = config.getBoolean("free-by-default", false);
		this.priceMultiplier             = (float)config.getDouble("price-multiplier", 500.0);
		this.multiworldMultiplier        = (float)config.getDouble("multiworld-multiplier", 1.2);
		this.multiworldTax               = (float)config.getDouble("multiworld-tax", 500.0);
		this.freeFromQts                 = config.getBoolean("free-from-qts", false);
		this.enableSafetyChecks          = config.getBoolean("enable-safety-checks", false);
		this.warmUpTicks                 = config.getInt("warmup-ticks", 20);
		this.coolDownTicks               = config.getInt("cooldown-ticks", 100);
		this.useGlobalPrice              = config.getBoolean("use-global-price", false);
		this.globalPrice                 = config.getDouble("global-price", 0.0);
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
	public boolean canQtFromAnywhere()
	{
		return qtFromAnywhere;
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
}