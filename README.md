# LamDeathPenalties

Soul-point death penalties for Paper/Folia 1.21+. Each death drains a soul point; penalties scale per tier until soul points regenerate.

## Features

- **Soul points**: Pool that depletes on death, regenerates at configurable intervals.
- **Max soul points**: Personal capacity that drops on PvP kills and regenerates over time. Adds a long-term cost to killing other players.
- **Per-tier penalties**: Item drops, hotbar/armor exposure, money loss, heart loss, custom commands — all configurable per soul-points level.
- **Item drops**: Drop percentage per tier with hotbar/armor toggles.
- **Soulbound support**: Items with the ExcellentEnchants Soulbound enchantment never drop on death.
- **Max health**: Remove hearts (`remove` mode) or grant bonus hearts (`add` mode) per tier.
- **Vault money**: Flat or percent penalties. On PvP death, money transfers to the killer; on natural death, it disappears.
- **PvP money transfer**: Percent of lost money that goes to the killer (0–100).
- **Threshold commands**: Run console commands at specific soul-points levels (titles, sounds, kicks).
- **Recovery modes**: Real-time (wall-clock) or active-time (playtime-based).
- **Penalty inheritance**: Undefined tier values inherit from the next-higher defined tier.
- **Editable messages**: Every player-facing line is configurable.
- **PlaceholderAPI**: Exposes current points, max points, penalties, and recovery info.
- **AxGraves integration**: Works with modified AxGraves for kept items.
- **Developer API**: Service-provider API with events for other plugins.

## How It Works

### Soul Points Lifecycle

1. Players start with configurable soul points (default: 10/10).
2. On death, lose 1 soul point. Penalties for the *new* level apply immediately.
3. Each tier (0–10) defines item drop %, hotbar/armor vulnerability, money/health penalties, and trigger commands.
4. Soul points regenerate over time (real-time or active-time) up to the max.
5. Threshold commands fire when dropping to configured levels (e.g., 9, 5, 0).

### Penalty Inheritance

If a tier doesn't define a penalty, it inherits from the next higher tier:

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

## Commands

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

## Permissions

| Permission          | Description                                         |
| ------------------- | --------------------------------------------------- |
| `lmdp.bypass`       | Bypass all death penalties (keeps inventory/xp)     |
| `lmdp.check.others` | Check other players' soul points                    |
| `lmdp.admin`        | Modify soul points, max soul points, reload plugin  |

## Setup

### Requirements

- Paper or Folia 1.21+
- Java 21
- [YskLib](https://github.com/YusakiDev/YskLib/releases) **1.6.9+** (required)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) (optional, money penalties)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (optional)

### Installation

1. Drop [YskLib 1.6.9+](https://github.com/YusakiDev/YskLib/releases) into `plugins/`.
2. Drop LamDeathPenalties into `plugins/` and start the server.
3. Edit `plugins/LamDeathPenalties/config.yml`:
   - Set `soul-points.max` and `soul-points.starting`
   - Configure `soul-points.max-soul-points` mechanics
   - Pick recovery mode (`real-time` or `active-time`) and interval
   - Configure penalty tiers (0–10)
   - Adjust `money-transfer` settings
4. Customize `messages:` for player-facing text.
5. Optional: install Vault, PlaceholderAPI, or modified AxGraves.
6. Grant `lmdp.admin` to staff.
7. Run `/lmdp reload` to apply.

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
money-transfer:
  enabled: true           # Enable money transfer when a player kills another player
  transfer-percent: 100.0 # Percentage of lost money to transfer to killer (0-100)
                          # 100 = killer gets all lost money
                          # 50 = killer gets half, rest disappears
                          # 0 = all money disappears (like natural death)

# Example: Player with 5 soul points dies (loses 150 coins in flat mode)
# - PvP death: killer receives 150 coins (transfer-percent: 100)
# - Natural death (lava, fall, mob): 150 coins disappear
```

## PlaceholderAPI Placeholders

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

## Configuration Validation

Critical values are validated on startup and reload:

- `soul-points.max` > 0
- `soul-points.starting` >= 0
- `money-transfer.transfer-percent` in 0–100
- Recovery intervals > 0

Invalid values auto-correct to defaults with a console warning.

## Max Soul Points

Each player has a personal max soul-points capacity that can differ from the config default:

- PvP kills reduce the killer's max (configurable reduction per kill)
- Max regenerates over time back to the config maximum
- Configurable minimum floor
- Admin commands modify max directly

## Developer API

LamDeathPenalties exposes a public API via Bukkit's service manager:

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

## Limitations & Roadmap

- No cross-server soul points syncing yet

## Support

- Discord: [YusakiDev](https://discord.gg/AjEh3dMPfq)
- Issues: [GitHub](https://github.com/LamaliaNetwork/LamDeathPenalties/issues)
