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
		panelComponent.setPreferredSize(new Dimension(180, 0));
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only show in Hallowed Sepulchre using strict real-time region check
		if (!config.showOverlay() || !plugin.isCurrentlyInSepulchreRegion())
		{
			return null;
		}
		
		SepulchreRun currentRun = plugin.getCurrentRun();
		if (currentRun == null)
		{
			return null;
		}

		PersistentStats stats = plugin.getPersistentStats();
		
		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Hallowed Sepulchre")
			.color(new Color(170, 130, 255))
			.build());
		
		boolean runIdle = plugin.isRunIdle();
		int currentFloor = plugin.getCurrentFloor();
		
		// Split times per floor (only show floors with a personal best)
		if (config.showFloorTimer() && stats != null)
		{
			for (int floor = 1; floor <= 5; floor++)
			{
				long pbMs = stats.getBestFloorTimeMs(floor);
				if (pbMs <= 0)
				{
					continue;
				}

				SepulchreRun.FloorData floorData = currentRun.getFloorData().get(floor);
				String splitText = "--";
				long splitMs = 0;
				boolean timeFromGame = false;

				if (floorData != null && floorData.getDuration() != null && floorData.getDuration().toMillis() > 0)
				{
					splitMs = floorData.getDuration().toMillis();
					splitText = HallowedSepulchrePlugin.formatDuration(floorData.getDuration());
					timeFromGame = floorData.isTimeFromGame();
				}
				else if (!runIdle && floor == currentFloor && plugin.getFloorStartTime() != null)
				{
					Duration liveTime = Duration.between(plugin.getFloorStartTime(), Instant.now());
					splitText = HallowedSepulchrePlugin.formatDuration(liveTime);
					splitMs = liveTime.toMillis();
				}

				String deltaText = "";
				Color leftColor = Color.WHITE;
				if (splitMs > 0)
				{
					long deltaMs = splitMs - pbMs;
					if (timeFromGame)
					{
						deltaText = " " + formatDeltaColored(deltaMs);
					}
					else if (deltaMs > 0)
					{
						// Show only live "behind PB" deltas to reduce noise mid-floor.
						deltaText = " " + formatDeltaColored(deltaMs);
					}
				}

				panelComponent.getChildren().add(LineComponent.builder()
					.left("F" + floor + ": " + splitText + deltaText)
					.leftColor(leftColor)
					.right("PB " + HallowedSepulchrePlugin.formatDuration(Duration.ofMillis(pbMs)))
					.rightColor(new Color(180, 180, 190))
					.build());
			}
		}
		
		// Total run timer vs PB (if available)
		if (config.showRunTimer() && stats != null)
		{
			int pbFloors = getContiguousPbFloors(stats);

			if (pbFloors > 0)
			{
				long totalPbMs = stats.getBestRunTimeForFloorsMs(pbFloors);
				if (totalPbMs <= 0)
				{
					// Fallback for existing users before per-run totals are recorded
					totalPbMs = getFallbackTotalPbMs(stats, pbFloors);
				}
				if (totalPbMs > 0)
				{
					long totalRunMs = getTotalRunMs(currentRun, pbFloors, currentFloor, runIdle, plugin.getFloorStartTime());
					String runText = totalRunMs > 0 ? HallowedSepulchrePlugin.formatDuration(Duration.ofMillis(totalRunMs)) : "--";
					boolean canCompare = hasOfficialSplits(currentRun, pbFloors);
					long deltaMs = totalRunMs - totalPbMs;
					String deltaText = "";
					if (totalRunMs > 0 && (canCompare || deltaMs > 0))
					{
						deltaText = " " + formatDeltaColored(deltaMs);
					}
					Color leftColor = Color.WHITE;

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Total: " + runText + deltaText)
						.leftColor(leftColor)
						.right("PB " + HallowedSepulchrePlugin.formatDuration(Duration.ofMillis(totalPbMs)))
						.rightColor(new Color(180, 180, 190))
						.build());
				}
			}
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
		
		// Current floor
		if (!runIdle && currentFloor > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Floor:")
				.right(String.valueOf(currentFloor))
				.rightColor(getFloorColor(currentFloor))
				.build());
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

	private String formatDelta(long deltaMs)
	{
		long absSeconds = Math.abs(deltaMs) / 1000;
		String sign = deltaMs >= 0 ? "+" : "-";

		if (absSeconds < 60)
		{
			return sign + absSeconds + "s";
		}

		long minutes = absSeconds / 60;
		long seconds = absSeconds % 60;
		return String.format("%s%d:%02d", sign, minutes, seconds);
	}

	private String formatDeltaColored(long deltaMs)
	{
		String color = deltaMs <= 0 ? "64dc64" : "ffd700";
		// Use an explicit reset color tag instead of </col>, which can render literally.
		return "<col=" + color + ">(" + formatDelta(deltaMs) + ")<col=ffffff>";
	}

	private int getContiguousPbFloors(PersistentStats stats)
	{
		int count = 0;
		for (int floor = 1; floor <= 5; floor++)
		{
			if (stats.getBestFloorTimeMs(floor) > 0)
			{
				count++;
			}
			else
			{
				break;
			}
		}
		return count;
	}

	private long getTotalRunMs(SepulchreRun run, int floors, int currentFloor, boolean runIdle, Instant floorStartTime)
	{
		long totalMs = 0;
		for (int floor = 1; floor <= floors; floor++)
		{
			SepulchreRun.FloorData data = run.getFloorData().get(floor);
			if (data != null && data.getDuration() != null)
			{
				totalMs += data.getDuration().toMillis();
			}
			else if (!runIdle && floor == currentFloor && floorStartTime != null)
			{
				totalMs += Duration.between(floorStartTime, Instant.now()).toMillis();
			}
			else
			{
				break;
			}
		}
		return totalMs;
	}

	private boolean hasOfficialSplits(SepulchreRun run, int floors)
	{
		for (int floor = 1; floor <= floors; floor++)
		{
			SepulchreRun.FloorData data = run.getFloorData().get(floor);
			if (data == null || !data.isTimeFromGame() || data.getDuration() == null || data.getDuration().toMillis() <= 0)
			{
				return false;
			}
		}
		return true;
	}

	private long getFallbackTotalPbMs(PersistentStats stats, int floors)
	{
		long totalMs = 0;
		for (int floor = 1; floor <= floors; floor++)
		{
			long floorPbMs = stats.getBestFloorTimeMs(floor);
			if (floorPbMs <= 0)
			{
				return 0;
			}
			totalMs += floorPbMs;
		}
		return totalMs;
	}
}
