# LamDeathPenalties

LamDeathPenalties adds a soul points system with progressive death penalties for Paper/Folia 1.20+. Players lose soul points on death, facing harsher consequences as their soul weakens—from item drops to max health reduction.

## Key Features

* **Soul points system**: Each player has a soul point pool that depletes on death and recovers over time with configurable intervals.
* **Progressive penalties**: Penalties scale with soul points—low points mean more item drops, vulnerable hotbar/armor, money loss, and reduced max health.
* **Flexible item drops**: Configure drop percentages per soul points level; protect hotbar and armor at higher levels, expose everything at zero.
* **Max health penalties**: Remove hearts (REMOVE mode) or grant bonus hearts (ADD mode) based on soul points, with fallback inheritance between levels.
* **Money integration**: Vault-based penalties with flat amounts or percentages; optional commands when funds are depleted.
* **Level-based commands**: Trigger console commands (titles, sounds, kicks, etc.) when players reach specific soul points thresholds.
* **Recovery modes**: Choose between real-time (wall-clock) or active-time (playtime-based) soul points recovery with configurable intervals.
* **Fallback inheritance**: Penalties cascade upward through soul points levels—unset values inherit from the next higher tier automatically.
* **Rich notifications**: Customizable death messages showing items lost, money penalties, max health changes, and recovery info.
* **PlaceholderAPI support**: Expose current points, penalties, and recovery times to other plugins via placeholders.
* **Public API**: Events and methods for developers to integrate soul points mechanics into custom plugins.
* **Folia ready**: Uses FoliaLib scheduler for smooth regional threading on Folia servers.
* **Hot reload**: `/lmdp reload` reloads config, messages, and refreshes penalties for online players instantly.

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

## Commands

| Command                         | Permission                   | Description                          |
| ------------------------------- | ---------------------------- | ------------------------------------ |
| `/lmdp`                         | *(default)*                  | Check your soul points               |
| `/lmdp check <player>`          | `lmdp.check.others`          | View another player's soul points    |
| `/lmdp set <player> <amount>`   | `lmdp.admin`                 | Set soul points (0 to max)           |
| `/lmdp give <player> <amount>`  | `lmdp.admin`                 | Give soul points (capped at max)     |
| `/lmdp take <player> <amount>`  | `lmdp.admin`                 | Remove soul points (min 0)           |
| `/lmdp reload`                  | `lmdp.admin`                 | Reload config and refresh penalties  |

**Aliases:** `/soulpoints`, `/sp`

## Permissions

| Permission          | Description                                  |
| ------------------- | -------------------------------------------- |
| `lmdp.bypass`       | Bypass all death penalties (keepInventory)   |
| `lmdp.check.others` | Check other players' soul points             |
| `lmdp.admin`        | Modify soul points and reload plugin         |

## Setup

1. **Install dependencies**: Download [YskLib](https://github.com/YusakiDev/YskLib/releases) (1.6.7+) and place it in `plugins/`.
2. **Drop the jar**: Add LamDeathPenalties to `plugins/` and start the server.
3. **Configure penalties**: Edit `plugins/LamDeathPenalties/config.yml`:
   - Set `soul-points.max` and `soul-points.starting` values
   - Choose recovery mode (`real-time` or `active-time`) and interval
   - Configure penalty tiers (0-10) with item drops, max health, money, and commands
4. **Customize messages**: All notification text lives under `messages:` in the config (death penalties, recovery alerts, command responses).
5. **Optional integrations**:
   - Install Vault for money penalties
   - Install PlaceholderAPI to use `%lamdeathpenalties_*%` placeholders
6. Grant permissions to staff for admin commands.

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

## PlaceholderAPI Placeholders

| Placeholder                              | Description                                  |
| ---------------------------------------- | -------------------------------------------- |
| `%lamdeathpenalties_current_points%`     | Current soul points                          |
| `%lamdeathpenalties_max_points%`         | Maximum soul points                          |
| `%lamdeathpenalties_next_item_drop%`     | Item drop % at current level                 |
| `%lamdeathpenalties_next_hotbar_drop%`   | Hotbar vulnerable? (true/false)              |
| `%lamdeathpenalties_next_armor_drop%`    | Armor vulnerable? (true/false)               |
| `%lamdeathpenalties_next_money_drop%`    | Money penalty at current level               |
| `%lamdeathpenalties_next_max_health%`    | Max health penalty at current level          |

## Limitations & Roadmap

* No cross-server soul points syncing yet
* Active-time recovery mode requires playtime tracking improvements

## Requirements

* Paper or Folia 1.21+
* Java 21 runtime
* [YskLib](https://github.com/YusakiDev/YskLib/releases) 1.6.7 or above
* (Optional) Vault for money penalties
* (Optional) PlaceholderAPI for placeholder support

## Support

* Issues: [GitHub](https://github.com/LamaliaNetwork/LamDeathPenalties/issues)
* Discord: [YusakiDev](https://discord.gg/AjEh3dMPfq)

Add consequence to death with a soul points system that scales penalties as players lose their essence.
