package com.hallowedsep;

import net.runelite.client.config.*;

import java.awt.Color;

@ConfigGroup("hallowedsep")
public interface HallowedSepulchreConfig extends Config
{
	@ConfigSection(
		name = "Display",
		description = "Display settings",
		position = 0
	)
	String displaySection = "display";
	
	@ConfigSection(
		name = "Tracking",
		description = "What to track",
		position = 1
	)
	String trackingSection = "tracking";
	
	@ConfigSection(
		name = "Goals",
		description = "Goal settings",
		position = 2
	)
	String goalsSection = "goals";
	
	@ConfigSection(
		name = "XP Estimates",
		description = "XP estimation values",
		position = 3
	)
	String xpSection = "xp";
	
	// Display Section
	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Show the in-game overlay during runs",
		section = displaySection,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}
	
	@ConfigItem(
		keyName = "showInfoBox",
		name = "Show Info Box",
		description = "Show the info box with XP/hr",
		section = displaySection,
		position = 1
	)
	default boolean showInfoBox()
	{
		return true;
	}
	
	@ConfigItem(
		keyName = "overlayColor",
		name = "Overlay Color",
		description = "Color of the overlay background",
		section = displaySection,
		position = 2
	)
	default Color overlayColor()
	{
		return new Color(45, 45, 45, 200);
	}
	
	@ConfigItem(
		keyName = "showFloorTimer",
		name = "Show Floor Timer",
		description = "Show time spent on current floor",
		section = displaySection,
		position = 3
	)
	default boolean showFloorTimer()
	{
		return true;
	}
	
	@ConfigItem(
		keyName = "showRunTimer",
		name = "Show Run Timer",
		description = "Show total run time",
		section = displaySection,
		position = 4
	)
	default boolean showRunTimer()
	{
		return true;
	}
	
	@ConfigItem(
		keyName = "showXpGained",
		name = "Show XP Gained",
		description = "Show XP gained during current run",
		section = displaySection,
		position = 5
	)
	default boolean showXpGained()
	{
		return true;
	}
	
	// Tracking Section
	@ConfigItem(
		keyName = "trackChests",
		name = "Track Chest Looting",
		description = "Track coffin/chest looting per floor",
		section = trackingSection,
		position = 0
	)
	default boolean trackChests()
	{
		return true;
	}
	
	@ConfigItem(
		keyName = "trackDeaths",
		name = "Track Deaths",
		description = "Track deaths per floor (coming soon)",
		section = trackingSection,
		position = 1
	)
	default boolean trackDeaths()
	{
		return false;
	}
	
	// Goals Section
	@ConfigItem(
		keyName = "targetLevel",
		name = "Target Level",
		description = "Your target Agility level (for projection calculations)",
		section = goalsSection,
		position = 0
	)
	@Range(min = 1, max = 99)
	default int targetLevel()
	{
		return 99;
	}
	
	@ConfigItem(
		keyName = "runsPerDay",
		name = "Runs Per Day",
		description = "Estimated runs per day (for time projection)",
		section = goalsSection,
		position = 1
	)
	@Range(min = 1, max = 200)
	default int runsPerDay()
	{
		return 25;
	}
	
	@ConfigItem(
		keyName = "milestoneLevel",
		name = "Milestone Level",
		description = "Intermediate milestone level (e.g., 92 for floor 5 access)",
		section = goalsSection,
		position = 2
	)
	@Range(min = 1, max = 99)
	default int milestoneLevel()
	{
		return 92;
	}
	
	// XP Estimates Section
	@ConfigItem(
		keyName = "estimatedXpPerRun",
		name = "Estimated XP/Run",
		description = "Estimated XP per run (used before you have data)",
		section = xpSection,
		position = 0
	)
	@Range(min = 1000, max = 20000)
	default int estimatedXpPerRun()
	{
		return 5850;
	}
	
	@ConfigItem(
		keyName = "floor1Xp",
		name = "Floor 1 XP",
		description = "Base XP for completing Floor 1",
		section = xpSection,
		position = 1
	)
	default int floor1Xp()
	{
		return 575;
	}
	
	@ConfigItem(
		keyName = "floor2Xp",
		name = "Floor 2 XP",
		description = "Base XP for completing Floor 2",
		section = xpSection,
		position = 2
	)
	default int floor2Xp()
	{
		return 925;
	}
	
	@ConfigItem(
		keyName = "floor3Xp",
		name = "Floor 3 XP",
		description = "Base XP for completing Floor 3",
		section = xpSection,
		position = 3
	)
	default int floor3Xp()
	{
		return 1500;
	}
	
	@ConfigItem(
		keyName = "floor4Xp",
		name = "Floor 4 XP",
		description = "Base XP for completing Floor 4",
		section = xpSection,
		position = 4
	)
	default int floor4Xp()
	{
		return 2700;
	}
	
	@ConfigItem(
		keyName = "floor5Xp",
		name = "Floor 5 XP",
		description = "Base XP for completing Floor 5",
		section = xpSection,
		position = 5
	)
	default int floor5Xp()
	{
		return 6000;
	}
	
	@ConfigItem(
		keyName = "floor4LootXp",
		name = "Floor 4 Loot XP",
		description = "Extra XP from looting 1 chest on Floor 4",
		section = xpSection,
		position = 6
	)
	default int floor4LootXp()
	{
		return 150;
	}
	
	@ConfigItem(
		keyName = "floor5LootXp",
		name = "Floor 5 Loot XP",
		description = "Extra XP from looting chests + Grand Coffin on Floor 5",
		section = xpSection,
		position = 7
	)
	default int floor5LootXp()
	{
		return 580;
	}
}
