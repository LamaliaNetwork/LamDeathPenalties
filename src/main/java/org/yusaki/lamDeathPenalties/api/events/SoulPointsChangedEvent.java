package org.yusaki.lamDeathPenalties.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Called after a player's soul points have changed
 * This event is not cancellable as the change has already occurred
 */
public class SoulPointsChangedEvent extends PlayerEvent {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final int oldSoulPoints;
    private final int newSoulPoints;
    private final SoulPointsChangeEvent.ChangeReason reason;
    
    public SoulPointsChangedEvent(Player player, int oldSoulPoints, int newSoulPoints, SoulPointsChangeEvent.ChangeReason reason) {
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
    public SoulPointsChangeEvent.ChangeReason getReason() {
        return reason;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}