# LamDeathPenalties

LamDeathPenalties adds a soul points system with progressive death penalties for Paper/Folia 1.21+. Players lose soul points on death, facing harsher consequences as their soul weakens—from item drops to max health reduction.

## ✨ Key Features

* **Soul points system**: Each player has a soul point pool that depletes on death and recovers over time with configurable intervals.
* **Max soul points mechanics**: Personal max soul points that decrease on PvP kills and regenerate over time, adding long-term consequences to player killing.
* **Progressive penalties**: Penalties scale with soul points—low points mean more item drops, vulnerable hotbar/armor, money loss, and reduced max health.
* **Flexible item drops**: Configure drop percentages per soul points level; protect hotbar and armor at higher levels, expose everything at zero.
* **Soulbound support**: Items with ExcellentEnchants Soulbound enchantment are never dropped on death.
* **Max health penalties**: Remove hearts (REMOVE mode) or grant bonus hearts (ADD mode) based on soul points, with fallback inheritance between levels.
* **Money integration**: Vault-based penalties with flat amounts or percentages; money transfers to killer on PvP death, disappears on natural death.
* **PvP money transfer**: Configure what percentage of lost money goes to the killer (0-100%), with the rest disappearing.
* **Level-based commands**: Trigger console commands (titles, sounds, kicks, etc.) when players reach specific soul points thresholds.
* **Recovery modes**: Choose between real-time (wall-clock) or active-time (playtime-based) soul points recovery with configurable intervals.
* **Fallback inheritance**: Penalties cascade upward through soul points levels—unset values inherit from the next higher tier automatically.
* **Rich notifications**: Customizable death messages showing items lost, money penalties, max health changes, and recovery info.
* **PlaceholderAPI support**: Expose current points, max points, penalties, and recovery times to other plugins via placeholders.
* **AxGraves integration**: Seamless integration with modified AxGraves for proper kept items handling.
* **Developer API**: Public API for other plugins to interact with the soul points system.

## How It Works

### Soul Points Lifecycle

1. **Starting state**: Players begin with configurable starting soul points (default: 10/10).
2. **Death penalty**: Lose 1 soul point on death; penalties for the *new* level apply immediately.
3. **Progressive scaling**: Each soul points tier has item drop %, hotbar/armor vulnerability, money/health penalties, and trigger commands.
4. **Recovery**: Points regenerate over time (real-time or active-time mode) until reaching the max.
5. **Commands on threshold**: When dropping to specific levels (9, 5, 0), execute warning titles, sounds, or even kicks.

### Penalty Inheritance

If a soul points level doesn't define a penalty, it inherits from the next higher level:

```yaml
soul-points:
   drop-rates:
      9:
         max-health:
            mode: "remove"
            amount: 5.0  # Level 9 loses 5 hearts
      5:
         max-health:
            mode: "remove"
            amount: 2.0  # Level 5 loses 2 hearts
      3:
      # No max-health defined → inherits from level 5 (2.0 hearts)
      0:
      # No max-health defined → inherits from level 3 → level 5 (2.0 hearts)
```

## 📝 Commands

| Command                            | Permission                   | Description                                    |
| ---------------------------------- | ---------------------------- | ---------------------------------------------- |
| `/lmdp`                            | *(default)*                  | Check your soul points and max soul points     |
| `/lmdp check <player>`             | `lmdp.check.others`          | View another player's soul points              |
| `/lmdp set <player> <amount>`      | `lmdp.admin`                 | Set soul points (0 to max)                     |
| `/lmdp give <player> <amount>`     | `lmdp.admin`                 | Give soul points (capped at max)               |
| `/lmdp take <player> <amount>`     | `lmdp.admin`                 | Remove soul points (min 0)                     |
| `/lmdp setmax <player> <amount>`   | `lmdp.admin`                 | Set player's max soul points                   |
| `/lmdp givemax <player> <amount>`  | `lmdp.admin`                 | Increase player's max soul points              |
| `/lmdp takemax <player> <amount>`  | `lmdp.admin`                 | Decrease player's max soul points              |
| `/lmdp reload`                     | `lmdp.admin`                 | Reload config and refresh penalties            |

**Aliases:** `/soulpoints`, `/sp`

## 🔐 Permissions

| Permission          | Description                                         |
| ------------------- | --------------------------------------------------- |
| `lmdp.bypass`       | Bypass all death penalties (keeps inventory/xp)     |
| `lmdp.check.others` | Check other players' soul points                    |
| `lmdp.admin`        | Modify soul points, max soul points, reload plugin  |

## 📦 Setup

### Requirements

* **Minecraft**: Paper or Folia 1.21+
* **Java**: Java 21 runtime
* **Dependencies**:
  * [YskLib](https://github.com/YusakiDev/YskLib/releases) **1.6.9** (required)
  * [Vault](https://www.spigotmc.org/resources/vault.34315/) (optional, for money penalties)
  * [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (optional, for placeholders)

### Installation

1. **Install YskLib**: Download [YskLib 1.6.9+](https://github.com/YusakiDev/YskLib/releases) and place it in `plugins/`.
2. **Drop the jar**: Add LamDeathPenalties to `plugins/` and start the server.
3. **Configure penalties**: Edit `plugins/LamDeathPenalties/config.yml`:
   - Set `soul-points.max` and `soul-points.starting` values
   - Configure max soul points mechanics under `soul-points.max-soul-points`
   - Choose recovery mode (`real-time` or `active-time`) and interval
   - Configure penalty tiers (0-10) with item drops, max health, money, and commands
   - Adjust money transfer settings under `money-transfer`
4. **Customize messages**: All notification text lives under `messages:` in the config (death penalties, recovery alerts, command responses).
5. **Optional integrations**:
   - Install Vault for money penalties and PvP transfers
   - Install PlaceholderAPI to use `%lamdeathpenalties_*%` placeholders
   - Install modified AxGraves for graves integration
6. **Grant permissions**: Give staff `lmdp.admin` for admin commands.
7. **Reload**: Run `/lmdp reload` to apply configuration changes.

## Configuration Examples

### Soul Points Level with Commands

```yaml
soul-points:
   drop-rates:
      5:
         items:
            drop-percent: 50
            hotbar: true
            armor: false
         max-health:
            mode: "remove"
            amount: 2.0
         money:
            mode: "flat"
            amount: 150.0
         commands:
            - "title %player% title {\"text\":\"Soul Points Critical!\",\"color\":\"red\"}"
            - "title %player% subtitle {\"text\":\"Half your soul is gone...\",\"color\":\"gray\"}"
```

### Max Health Modes

```yaml
default-penalty:
   max-health:
      mode: "remove"  # Deducts hearts from base health
      amount: 0.0

# Or grant bonus hearts:
9:
   max-health:
      mode: "add"     # Grants extra hearts as reward for high soul points
      amount: 5.0
```

### Recovery Settings

```yaml
soul-points:
   recovery:
      mode: "real-time"       # Options: "real-time" or "active-time"
      interval-seconds: 3600  # 1 hour for real-time, 1 hour of playtime for active-time
```

### Money Transfer on PvP Death

```yaml
# Money transfer settings
money-transfer:
  enabled: true           # Enable money transfer when a player kills another player
  transfer-percent: 100.0 # Percentage of lost money to transfer to killer (0-100)
                          # 100 = killer gets all lost money
                          # 50 = killer gets half, rest disappears
                          # 0 = all money disappears (like natural death)

# Example: Player with 5 soul points dies (loses 150 coins with flat mode)
# - Killed by another player: killer receives 150 coins (if transfer-percent: 100)
# - Natural death (lava, fall, mob): 150 coins disappear completely
```

## 🔌 PlaceholderAPI Placeholders

### Soul Points
| Placeholder                                    | Description                                  |
| ---------------------------------------------- | -------------------------------------------- |
| `%lamdeathpenalties_current_points%`           | Current soul points                          |
| `%lamdeathpenalties_max_points%`               | Current maximum soul points (personal)       |
| `%lamdeathpenalties_config_max_points%`        | Config maximum soul points (default)         |

### Next Death Penalties
| Placeholder                                    | Description                                  |
| ---------------------------------------------- | -------------------------------------------- |
| `%lamdeathpenalties_next_item_drop%`           | Item drop % at current level                 |
| `%lamdeathpenalties_next_hotbar_drop%`         | Hotbar vulnerable? (true/false)              |
| `%lamdeathpenalties_next_armor_drop%`          | Armor vulnerable? (true/false)               |
| `%lamdeathpenalties_next_money_drop%`          | Money penalty at current level               |
| `%lamdeathpenalties_next_max_health%`          | Max health penalty at current level          |

### Recovery Information
| Placeholder                                    | Description                                  |
| ---------------------------------------------- | -------------------------------------------- |
| `%lamdeathpenalties_time_until_next_recovery%` | Time until next soul point recovery          |
| `%lamdeathpenalties_recovery_mode%`            | Current recovery mode (real-time/active)     |

## 🔧 Configuration Validation (v0.6.3)

The plugin now validates critical configuration values on startup and reload:
- **soul-points.max** must be > 0
- **soul-points.starting** must be >= 0
- **money-transfer.transfer-percent** must be 0-100
- **recovery intervals** must be > 0

Invalid values are auto-corrected to defaults with console warnings.

## 🎯 Max Soul Points System

Players have a **personal maximum** soul points capacity that can differ from the config default:
- **PvP kills reduce** the killer's max soul points (configurable reduction per kill)
- **Regeneration over time** gradually restores max soul points back to config maximum
- **Minimum cap** prevents max soul points from going below a configured threshold
- **Commands** allow admins to directly modify player max soul points

This adds long-term consequences to player killing beyond single-death penalties.

## 🛠️ Developer API

LamDeathPenalties provides a public API for other plugins:

```java
// Get API instance
LamDeathPenaltiesAPI api = Bukkit.getServicesManager()
    .getRegistration(LamDeathPenaltiesAPI.class)
    .getProvider();

// Access managers
SoulPointsManager soulPoints = api.getSoulPointsManager();
RecoveryScheduler recovery = api.getRecoveryScheduler();

// Listen to events
@EventHandler
public void onSoulPointsChange(SoulPointsChangeEvent event) {
    // Cancellable - fired before soul points change
    if (event.getReason() == ChangeReason.DEATH) {
        event.setCancelled(true); // Prevent the change
    }
}

@EventHandler
public void onSoulPointsChanged(SoulPointsChangedEvent event) {
    // Informational - fired after soul points changed
    int oldPoints = event.getOldSoulPoints();
    int newPoints = event.getNewSoulPoints();
}
```

## ⚠️ Limitations & Roadmap

* No cross-server soul points syncing yet

## Support

* Discord: [YusakiDev](https://discord.gg/AjEh3dMPfq)

Add consequence to death with a soul points system that scales penalties as players lose their essence.
