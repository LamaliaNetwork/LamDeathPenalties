# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Minecraft Paper plugin called "LamDeathPenalties" that implements a soul points system with configurable death penalties. Players start with 10 soul points and lose 1 per death, with configurable item/experience drop rates based on current soul points. Recovery occurs hourly through real-time or active play time.

## Build and Development Commands

- **Build the plugin**: `mvn clean package`
- **Compile only**: `mvn compile`
- **Clean build artifacts**: `mvn clean`

The built plugin JAR will be located in `target/LamDeathPenalties-0.1.jar` after running the package command.

## Project Structure

- **Main plugin class**: `src/main/java/org/yusaki/lamDeathPenalties/LamDeathPenalties.java`
- **Soul points manager**: `src/main/java/org/yusaki/lamDeathPenalties/SoulPointsManager.java`
- **Death event handler**: `src/main/java/org/yusaki/lamDeathPenalties/DeathListener.java`
- **Recovery system**: `src/main/java/org/yusaki/lamDeathPenalties/RecoveryScheduler.java`
- **Commands**: `src/main/java/org/yusaki/lamDeathPenalties/SoulPointsCommand.java`
- **PlaceholderAPI**: `src/main/java/org/yusaki/lamDeathPenalties/SoulPointsPlaceholder.java`
- **Configuration**: `src/main/resources/config.yml`
- **Plugin metadata**: `src/main/resources/plugin.yml`

## Development Environment

- **Java Version**: 21
- **Minecraft API**: Paper 1.21-R0.1-SNAPSHOT
- **Build Tool**: Maven
- **Package**: `org.yusaki.lamDeathPenalties`
- **Folia Support**: Yes (via FoliaLib 0.5.1)

## Core Features

- **Soul Points System**: 10 starting points, -1 per death, +1 per hour recovery
- **Configurable Drop Rates**: Per soul point level (0-10) for items, hotbar, armor, and experience
- **Recovery Modes**: Real-time (continues offline) or active-time (only while playing)
- **JSON Data Persistence**: Player data stored in `plugins/LamDeathPenalties/playerdata.json`
- **Commands**: `/soulpoints` for checking/managing soul points
- **PlaceholderAPI Integration**: Various placeholders for external plugin integration

## Dependencies

- **Paper API 1.21-R0.1-SNAPSHOT** (provided)
- **FoliaLib 0.5.1** (shaded) - For Folia/Paper compatibility
- **PlaceholderAPI 2.11.6** (provided, optional) - For placeholder support

## Configuration

The plugin uses `config.yml` for:
- Recovery mode settings (real-time vs active-time)
- Drop rates per soul point level (0-10)
- Maximum and starting soul points
- Recovery interval timing

## Commands and Permissions

- `/soulpoints` - Check soul points (no permission required)
- `/soulpoints set <player> <amount>` - Admin command (`soulpoints.admin`)
- `/soulpoints give <player> <amount>` - Give soul points (`soulpoints.admin`)
- `/soulpoints check <player>` - Check others' soul points (`soulpoints.check.others`)

## Soul Points Plugin Design

### Core System
- **Starting Soul Points:** 10 (max 10)
- **Death Penalty:** -1 soul point per death
- **Recovery:** +1 soul point per hour played

### Configurable Drop Rates per Soul Point

Each soul point level has individual configuration:
```yaml
drop-rates:
  10:
    item-drop: 0      # % of items to drop
    hotbar-drop: false # Include hotbar in drops
    armor-drop: false  # Include armor in drops  
    exp-drop: 0        # % of exp to drop
  9:
    item-drop: 10
    hotbar-drop: false
    armor-drop: false
    exp-drop: 10
  8:
    item-drop: 20
    hotbar-drop: false
    armor-drop: false
    exp-drop: 15
  7:
    item-drop: 30
    hotbar-drop: true   # Hotbar becomes vulnerable
    armor-drop: false
    exp-drop: 20
  # ... continue for each soul point level down to 0
  0:
    item-drop: 100
    hotbar-drop: true
    armor-drop: true    # Everything drops
    exp-drop: 100
```

### PlaceholderAPI Support
```
%soulpoints_current% - Current soul points (0-10)
%soulpoints_max% - Maximum soul points (10)  
%soulpoints_percentage% - Soul points as percentage (0-100%)
%soulpoints_next_item_drop% - Item drop % at current soul points
%soulpoints_next_hotbar_drop% - Will hotbar drop? (true/false)
%soulpoints_next_armor_drop% - Will armor drop? (true/false)
%soulpoints_next_exp_drop% - Exp drop % at current soul points
%soulpoints_recovery_time% - Time until next recovery (if implemented)
```

### Commands
- `/soulpoints` - Check current soul points
- `/soulpoints set <player> <amount>` - Admin command

### Simple Logic
1. **Body items** = regular inventory slots (most vulnerable)
2. **Hotbar protection** until soul points drop below threshold  
3. **Armor protection** optional setting
4. **Percentage drop** = random selection from vulnerable slots