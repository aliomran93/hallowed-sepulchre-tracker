package com.hallowedsep;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class HallowedSepulchreOverlay extends OverlayPanel
{
	private final HallowedSepulchrePlugin plugin;
	private final HallowedSepulchreConfig config;
	
	@Inject
	public HallowedSepulchreOverlay(HallowedSepulchrePlugin plugin, HallowedSepulchreConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.isInSepulchre())
		{
			return null;
		}
		
		SepulchreRun currentRun = plugin.getCurrentRun();
		if (currentRun == null)
		{
			return null;
		}
		
		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Hallowed Sepulchre")
			.color(new Color(170, 130, 255))
			.build());
		
		// Current floor
		int currentFloor = plugin.getCurrentFloor();
		if (currentFloor > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Floor:")
				.right(String.valueOf(currentFloor))
				.rightColor(getFloorColor(currentFloor))
				.build());
		}
		
		// Run timer
		if (config.showRunTimer())
		{
			Duration runTime = currentRun.getDuration();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Run Time:")
				.right(HallowedSepulchrePlugin.formatDuration(runTime))
				.rightColor(Color.WHITE)
				.build());
		}
		
		// Floor timer
		if (config.showFloorTimer() && currentFloor > 0 && plugin.getFloorStartTime() != null)
		{
			Duration floorTime = Duration.between(plugin.getFloorStartTime(), Instant.now());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Floor Time:")
				.right(HallowedSepulchrePlugin.formatDuration(floorTime))
				.rightColor(Color.YELLOW)
				.build());
		}
		
		// XP gained this run
		if (config.showXpGained())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("XP Gained:")
				.right(HallowedSepulchrePlugin.formatNumber(currentRun.getTotalXp()))
				.rightColor(Color.GREEN)
				.build());
		}
		
		// Chests looted
		if (config.trackChests())
		{
			int chestsLooted = currentRun.getTotalChestsLooted();
			if (chestsLooted > 0 || currentRun.isLootedGrandCoffin())
			{
				String chestText = String.valueOf(chestsLooted);
				if (currentRun.isLootedGrandCoffin())
				{
					chestText += " + GC";
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Chests:")
					.right(chestText)
					.rightColor(new Color(255, 215, 0))
					.build());
			}
		}
		
		// Floors completed this run
		int floorsCompleted = currentRun.getFloorsCompleted();
		if (floorsCompleted > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Floors Done:")
				.right(String.valueOf(floorsCompleted))
				.rightColor(Color.CYAN)
				.build());
		}
		
		return super.render(graphics);
	}
	
	private Color getFloorColor(int floor)
	{
		switch (floor)
		{
			case 1:
				return new Color(144, 238, 144); // Light green
			case 2:
				return new Color(135, 206, 235); // Sky blue
			case 3:
				return new Color(255, 255, 150); // Light yellow
			case 4:
				return new Color(255, 165, 0);   // Orange
			case 5:
				return new Color(255, 100, 100); // Light red
			default:
				return Color.WHITE;
		}
	}
}
