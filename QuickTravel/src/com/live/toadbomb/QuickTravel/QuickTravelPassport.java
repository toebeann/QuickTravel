package com.live.toadbomb.QuickTravel;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * When a player wants to QT, the details of the QT request are stored in this object while so that the
 * warm up (if any) can be applied. Once the warm up period expires the passport teleports the player. We
 * also use this class to play warmup FX at the player's location while warmup is in progress 
 * 
 * @author Mumfrey
 */
public class QuickTravelPassport
{
	/**
	 * Player being teleported
	 */
	private final Player player;
	
	/**
	 * Cost for teleport
	 */
	private final double cost;
	
	/**
	 * Message to display once teleported
	 */
	private final String message;
	
	/**
	 * FX to play when arriving
	 */
	private final QuickTravelFX arrivalEffect;
	
	/**
	 * FX to play when departing
	 */
	private final QuickTravelFX departureEffect;
	
	/**
	 * Plugin options, for global stuff
	 */
	private final QuickTravelOptions options;
	
	/**
	 * Object to call when/if teleport takes place
	 */
	private final QuickTravelCallback notifyCallback;
	
	/**
	 * QT we are teleporting from, this can be null
	 */
	private final QuickTravelLocation origin;
	
	/**
	 * QT we are teleporting to, this must not be null
	 */
	private final QuickTravelLocation target;
	
	/**
	 * Ticks remaining in the warm up period
	 */
	private int warmUpTicks;
	
	/**
	 * Currently configured warm up effect
	 */
	private QuickTravelFX warmUpFX = new QuickTravelFXWarmUp(16);
	
	/**
	 * Number of ticks between playing warm up FX 
	 */
	private int warmUpFXInterval = 2;

	/**
	 * @param player
	 * @param cost
	 * @param message
	 * @param origin
	 * @param target
	 * @param wildernessEffect
	 * @param options
	 * @param notify
	 */
	public QuickTravelPassport(Player player, double cost, String message, QuickTravelLocation origin, QuickTravelLocation target, QuickTravelFX wildernessEffect, QuickTravelOptions options, QuickTravelCallback notify)
	{
		super();
		
		this.player          = player;
		this.cost            = cost;
		this.message         = message;
		this.origin          = origin;
		this.target          = target;
		this.arrivalEffect   = target.getArrivalEffect();
		this.departureEffect = origin != null ? origin.getDepartureEffect() : wildernessEffect;
		this.options         = options;
		this.notifyCallback  = notify;
	}
	
	/**
	 * @param heightModifier
	 * @return
	 */
	public boolean onTick(int heightModifier)
	{
		this.warmUpTicks--;
		
		// Warm up period has expired, so do the teleport now 
		if (this.warmUpTicks < 1)
		{
			this.doTeleport();
			return true;
		}
		
		// Check if the player has left the QT region
		if (this.origin != null && !this.origin.regionContains(this.player.getLocation(), heightModifier))
		{
			this.player.sendMessage(ChatColor.BLUE + "You left the QuickTravel area, cancelling travel");
			return true;
		}
		
		// Display the warm-up countdown
		int warmUpNotifyPeriod = warmUpTicks > 100 ? 100 : 20;
		if (warmUpTicks % warmUpNotifyPeriod == 0 && this.warmUpTicks > 19)
		{
			int timeRemaining = this.warmUpTicks / 20;
			this.player.sendMessage(ChatColor.BLUE + "Travelling to " + ChatColor.AQUA + this.target.getName() + ChatColor.BLUE + " in " + ChatColor.GOLD + timeRemaining + ChatColor.BLUE + " seconds... ");
		}
		
		// Play warm-up effects every few ticks
		if (this.options.enableEffects() && this.warmUpTicks % this.warmUpFXInterval == 0 && this.warmUpFX != null)
		{
			this.warmUpFX.playTeleportEffect(this.player.getLocation(), this.warmUpTicks);
		}
		
		// Effect immediately before teleport
		if (this.warmUpTicks == 3)
		{
			this.preTeleport();
		}
		
		return false;
	}

	/**
	 * Play effect immediately prior to teleporting
	 */
	public void preTeleport()
	{
		if (this.options.enableEffects() && this.departureEffect != null) this.departureEffect.playPreTeleportEffect(this.player.getLocation(), 0);
	}
	
	/**
	 * @param player
	 * @param departureEffect
	 * @param enableSafetyChecks
	 */
	public void doTeleport()
	{
		if (this.cost > 0 && this.options.enableEconomy() && EcoSetup.economy != null)
		{
			if (EcoSetup.economy.has(this.player.getName(), this.cost))
			{
				this.chargePlayer();
			}
			else
			{
				player.sendMessage("You do not have enough money to go there.");
				return;
			}
		}

		Location originLocation = this.player.getLocation();
		Location targetLocation = this.target.getTargetLocation();
		
		if (targetLocation != null && this.player.getLocation() != null)
		{		
			Location destination = this.options.enableSafetyChecks() ? this.checkSafe(targetLocation, this.player) : targetLocation;
			this.player.teleport(destination);
			
			if (this.options.enableEffects())
			{
				if (this.departureEffect != null) this.departureEffect.playTeleportEffect(originLocation, 0);
				if (this.arrivalEffect != null) this.arrivalEffect.playTeleportEffect(destination, 0);
			}
			
			if (this.notifyCallback != null)
			{
				this.notifyCallback.notifyPlayerTeleported(this.player, this.cost);
			}
			
			if (this.message != null)
			{
				this.player.sendMessage(this.message);
			}
		}
	}

	/**
	 * @param player
	 * @param fee
	 */
	protected void chargePlayer()
	{
		/* Withdraw money from player */
		if (EcoSetup.economy.hasBankSupport() && !this.options.withdrawFromPlayerNotBank())
		{
			EcoSetup.economy.bankWithdraw(this.player.getName(), this.cost);
		}
		else
		{
			EcoSetup.economy.withdrawPlayer(this.player.getName(), this.cost);
		}
	}

	/**
	 * Checks a target region is safe and encases the player in glass if not
	 * 
	 * @param targetLocation
	 * @param p
	 * @return
	 */
	public Location checkSafe(Location targetLocation, Player p)
	{
		Block targetBlock = targetLocation.getBlock();
		boolean fixed = false;
		
		for (int pass = 0; pass < 2; pass++)
		{
			for (int yOffset = 3; yOffset > -2; yOffset--)
			{
				for (int xOffset = -1; xOffset < 2; xOffset++)
				{
					for (int zOffset = -1; zOffset < 2; zOffset++)
					{
						Block adjacentBlock = targetBlock.getRelative(xOffset, yOffset, zOffset);
						fixed |= this.makeSafe(adjacentBlock, (yOffset > -1 && yOffset < 2) && xOffset == 0 && zOffset == 0, yOffset == 0, fixed);
					}				
				}
			}
		}
		
		return targetLocation;
	}

	/**
	 * Make a block in the target region safe
	 * 
	 * @param block
	 * @param mustBeClear
	 * @param mustBeSolid
	 * @param fixed
	 * @return
	 */
	private boolean makeSafe(Block block, boolean mustBeClear, boolean mustBeSolid, boolean fixed)
	{
		Material blockType = block.getType();
		
		if ((blockType == Material.STATIONARY_LAVA || blockType == Material.LAVA) && (mustBeClear || mustBeSolid || fixed))
		{
			block.setType(mustBeClear ? Material.AIR : Material.GLASS);
			return true;
		}
		
		if (mustBeClear && blockType != Material.AIR)
		{
			block.setType(Material.AIR);
			return false;
		}
		
		return false;
	}

	/**
	 * @return the player
	 */
	public Player getPlayer()
	{
		return player;
	}

	/**
	 * @return the cost
	 */
	public double getCost()
	{
		return cost;
	}

	/**
	 * @return the message
	 */
	public String getMessage()
	{
		return message;
	}

	/**
	 * @return the warmUpTicks
	 */
	public int getWarmUpTicks()
	{
		return warmUpTicks;
	}

	/**
	 * @param warmUpTicks
	 */
	public void setWarmUpTicks(int warmUpTicks)
	{
		this.warmUpTicks = warmUpTicks + 1;
	}

	/**
	 * @return the origin
	 */
	public QuickTravelLocation getOrigin()
	{
		return origin;
	}
}
