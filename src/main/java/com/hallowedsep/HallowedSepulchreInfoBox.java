package com.hallowedsep;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class HallowedSepulchreInfoBox extends OverlayPanel
{
	private final HallowedSepulchrePlugin plugin;
	private final HallowedSepulchreConfig config;
	
	@Inject
	public HallowedSepulchreInfoBox(HallowedSepulchrePlugin plugin, HallowedSepulchreConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		
		setPosition(OverlayPosition.BOTTOM_LEFT);
		setPriority(OverlayPriority.LOW);
		panelComponent.setPreferredSize(new Dimension(180, 0));
	}
	
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showInfoBox())
		{
			return null;
		}
		
		// Only show in Hallowed Sepulchre (lobby or floors)
		// Use strict real-time region check to avoid showing in other areas
		if (!plugin.isCurrentlyInSepulchreRegion())
		{
			return null;
		}
		
		PersistentStats stats = plugin.getPersistentStats();
		HallowedSepulchreSession session = plugin.getSession();
		
		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Hallowed Sepulchre")
			.color(new Color(170, 130, 255))
			.build());
		
		// Status based on current run (more reliable than region detection)
		SepulchreRun currentRun = plugin.getCurrentRun();
		boolean hasActiveRun = currentRun != null;
		int floor = plugin.getCurrentFloor();
		
		String statusText = hasActiveRun ? "FLOOR " + floor : "IDLE";
		Color statusColor = hasActiveRun ? Color.GREEN : Color.GRAY;
		
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(statusText)
			.rightColor(statusColor)
			.build());
		
		// Separator
		panelComponent.getChildren().add(LineComponent.builder()
			.left("-------------")
			.right("")
			.leftColor(new Color(80, 80, 90))
			.build());
		
		// Get today's stats
		DailyStats today = stats.getToday();
		int todayRuns = today != null ? today.getRuns() : 0;
		int todayXp = today != null ? today.getTotalXp() : 0;
		double todayHours = today != null ? today.getHoursPlayed() : 0;
		
		// Get session stats (live during run)
		int sessionRuns = session != null ? session.getTotalRuns() : 0;
		int sessionXp = session != null ? session.getTotalXp() : 0;
		
		// Add current run XP if in a run
		int currentRunXp = currentRun != null ? currentRun.getTotalXp() : 0;
		
		// Total XP = today's completed + session's completed + current run
		int totalXpDisplay = todayXp + currentRunXp;
		
		// Runs Today (+ current if in run)
		String runsDisplay = currentRun != null ? 
			todayRuns + " (+1)" : String.valueOf(todayRuns);
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Runs Today:")
			.right(runsDisplay)
			.rightColor(Color.WHITE)
			.build());
		
		// Runs Session
		String sessionDisplay = currentRun != null ?
			sessionRuns + " (+1)" : String.valueOf(sessionRuns);
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Runs Session:")
			.right(sessionDisplay)
			.rightColor(Color.CYAN)
			.build());
		
		// Hours in Sep (today) - include current run time
		double currentRunHours = 0;
		if (currentRun != null)
		{
			currentRunHours = currentRun.getDuration().toMillis() / 3_600_000.0;
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Time in Sep:")
			.right(formatHoursMinutes(todayHours + currentRunHours))
			.rightColor(new Color(100, 180, 255))
			.build());
		
		// XP Gained (today + current run)
		panelComponent.getChildren().add(LineComponent.builder()
			.left("XP Gained:")
			.right(HallowedSepulchrePlugin.formatNumber(totalXpDisplay))
			.rightColor(Color.GREEN)
			.build());
		
		// XP/hr - live calculation including current run
		double xpHrDisplay = plugin.getXpPerHour();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("XP/hr:")
			.right(HallowedSepulchrePlugin.formatNumber((int) xpHrDisplay))
			.rightColor(getXpHrColor(xpHrDisplay))
			.build());
		
		// Runs to next level
		int currentLevel = plugin.getCurrentAgilityLevel();
		if (currentLevel < 99)
		{
			int nextLevel = plugin.getNextLevel();
			int runsToNext = plugin.getRunsToNextLevel();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Runs to " + nextLevel + ":")
				.right(String.valueOf(runsToNext))
				.rightColor(new Color(255, 200, 100))
				.build());
		}
		
		return super.render(graphics);
	}
	
	private Color getXpHrColor(double xpPerHour)
	{
		if (xpPerHour >= 100000)
		{
			return new Color(0, 255, 0);
		}
		else if (xpPerHour >= 80000)
		{
			return new Color(100, 255, 100);
		}
		else if (xpPerHour >= 60000)
		{
			return Color.YELLOW;
		}
		else
		{
			return new Color(255, 150, 100);
		}
	}
	
	private String formatHoursMinutes(double hours)
	{
		int totalMinutes = (int) (hours * 60);
		int h = totalMinutes / 60;
		int m = totalMinutes % 60;
		
		if (h > 0)
		{
			return String.format("%dh %dm", h, m);
		}
		else
		{
			return String.format("%dm", m);
		}
	}
}
