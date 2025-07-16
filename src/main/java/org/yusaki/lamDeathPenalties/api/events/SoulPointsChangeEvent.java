package org.yusaki.lamDeathPenalties.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Called when a player's soul points are about to change
 * This event is cancellable
 */
public class SoulPointsChangeEvent extends PlayerEvent implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    
    private final int oldSoulPoints;
    private int newSoulPoints;
    private final ChangeReason reason;
    
    public enum ChangeReason {
        DEATH,
        RECOVERY,
        COMMAND,
        API,
        PLUGIN_SET,
        PLUGIN_ADD,
        PLUGIN_REMOVE
    }
    
    public SoulPointsChangeEvent(Player player, int oldSoulPoints, int newSoulPoints, ChangeReason reason) {
        super(player);
        this.oldSoulPoints = oldSoulPoints;
        this.newSoulPoints = newSoulPoints;
        this.reason = reason;
    }
    
    /**
     * Get the player's old soul points value
     * @return Old soul points
     */
    public int getOldSoulPoints() {
        return oldSoulPoints;
    }
    
    /**
     * Get the player's new soul points value
     * @return New soul points
     */
    public int getNewSoulPoints() {
        return newSoulPoints;
    }
    
    /**
     * Set the new soul points value
     * @param newSoulPoints New soul points (will be clamped to valid range)
     */
    public void setNewSoulPoints(int newSoulPoints) {
        this.newSoulPoints = newSoulPoints;
    }
    
    /**
     * Get the change amount (positive for increase, negative for decrease)
     * @return Change amount
     */
    public int getChangeAmount() {
        return newSoulPoints - oldSoulPoints;
    }
    
    /**
     * Get the reason for the soul points change
     * @return Change reason
     */
    public ChangeReason getReason() {
        return reason;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}