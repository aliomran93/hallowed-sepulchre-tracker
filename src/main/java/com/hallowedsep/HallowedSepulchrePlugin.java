package com.hallowedsep;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@PluginDescriptor(
	name = "Hallowed Sepulchre Tracker",
	description = "Track your Hallowed Sepulchre runs, XP, floor completions, and progress to 99 Agility",
	tags = {"agility", "hallowed", "sepulchre", "xp", "tracker", "darkmeyer"}
)
public class HallowedSepulchrePlugin extends Plugin
{
	// Hallowed Sepulchre Region IDs
	// Each floor has multiple layout variations with different region IDs
	private static final int LOBBY_REGION = 9565;
	
	// Floor 1 regions
	private static final int FLOOR_1_REGION_1 = 8797;
	private static final int FLOOR_1_REGION_2 = 9053;
	private static final int FLOOR_1_REGION_3 = 9309;
	
	// Floor 2 regions
	private static final int FLOOR_2_REGION_1 = 9054;
	private static final int FLOOR_2_REGION_2 = 9310;
	private static final int FLOOR_2_REGION_3 = 10077;  // User-verified
	
	// Floor 3 regions
	private static final int FLOOR_3_REGION_1 = 9311;
	private static final int FLOOR_3_REGION_2 = 9567;
	private static final int FLOOR_3_REGION_3 = 9563;  // User-verified
	
	// Floor 4 regions
	private static final int FLOOR_4_REGION_1 = 9823;
	private static final int FLOOR_4_REGION_2 = 10079;
	private static final int FLOOR_4_REGION_3 = 10075;  // User-verified
	
	// Floor 5 regions
	private static final int FLOOR_5_REGION_1 = 10335;
	private static final int FLOOR_5_REGION_2 = 10591;
	
	// Object IDs
	private static final int STAIRS_DOWN = 39526;
	private static final int STAIRS_UP = 39527;
	private static final int COFFIN = 39545;
	private static final int GRAND_HALLOWED_COFFIN = 39546;
	private static final int MAGICAL_OBELISK = 39558;
	
	@Inject
	private Client client;
	
	@Inject
	private ClientThread clientThread;
	
	@Inject
	private HallowedSepulchreConfig config;
	
	@Inject
	private OverlayManager overlayManager;
	
	@Inject
	private HallowedSepulchreOverlay overlay;
	
	@Inject
	private HallowedSepulchreInfoBox infoBox;
	
	@Inject
	private ClientToolbar clientToolbar;
	
	@Inject
	private ConfigManager configManager;
	
	@Getter
	private HallowedSepulchreSession session;
	
	@Getter
	private PersistentStats persistentStats;
	
	@Getter
	private SepulchreRun currentRun;
	
	@Getter
	private boolean inSepulchre;
	
	@Getter
	private int currentFloor;
	
	@Getter
	private Instant floorStartTime;
	
	private NavigationButton navButton;
	private HallowedSepulchrePanel panel;
	private int lastAgilityXp;
	private int lastRegionId;
	
	// Track transition state - don't immediately end run when in loading regions
	private int transitionTickCount;
	private static final int MAX_TRANSITION_TICKS = 30; // Allow up to 30 ticks (~18 seconds) for transitions
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Hallowed Sepulchre Tracker started!");
		
		session = loadSession();
		persistentStats = loadPersistentStats();
		currentRun = null;
		inSepulchre = false;
		currentFloor = 0;
		lastAgilityXp = -1;
		lastRegionId = -1;
		transitionTickCount = 0;
		
		overlayManager.add(overlay);
		overlayManager.add(infoBox);
		
		panel = new HallowedSepulchrePanel(this, config);
		
		// Use RuneLite's built-in agility skill icon
		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/skill_icons/agility.png");
		}
		catch (Exception e)
		{
			// Fallback to a simple colored icon if skill icon not available
			icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = icon.createGraphics();
			g.setColor(new java.awt.Color(170, 130, 255));
			g.fillOval(2, 2, 12, 12);
			g.dispose();
		}
		
		navButton = NavigationButton.builder()
			.tooltip("Hallowed Sepulchre")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		
		clientToolbar.addNavigation(navButton);
	}
	
	@Override
	protected void shutDown() throws Exception
	{
		log.info("Hallowed Sepulchre Tracker stopped!");
		
		overlayManager.remove(overlay);
		overlayManager.remove(infoBox);
		clientToolbar.removeNavigation(navButton);
		
		saveSession();
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> {
				if (lastAgilityXp == -1)
				{
					lastAgilityXp = client.getSkillExperience(Skill.AGILITY);
				}
			});
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (inSepulchre && currentRun != null)
			{
				endRun(false);
			}
		}
	}
	
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		
		int regionId = getCurrentRegionId();
		
		
		if (regionId != lastRegionId)
		{
			handleRegionChange(regionId);
			lastRegionId = regionId;
		}
		
		// Update panel periodically
		if (panel != null)
		{
			panel.updateStats();
		}
	}
	
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.AGILITY)
		{
			return;
		}
		
		int currentXp = event.getXp();
		
		if (lastAgilityXp > 0 && currentXp > lastAgilityXp && inSepulchre && currentRun != null)
		{
			int xpGained = currentXp - lastAgilityXp;
			currentRun.addXp(xpGained);
			session.addXp(xpGained);
			
			log.debug("Agility XP gained in Sepulchre: {} (total run: {})", xpGained, currentRun.getTotalXp());
		}
		
		lastAgilityXp = currentXp;
	}
	
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		
		String message = event.getMessage();
		
		// Detect entering Floor 1
		if (message.contains("You venture down into the Hallowed Sepulchre") && !message.contains("further"))
		{
			log.info("Detected entry to Floor 1 via chat message!");
			inSepulchre = true;
			
			if (currentRun == null)
			{
				startRun();
				currentFloor = 1;
				floorStartTime = Instant.now();
				currentRun.startFloor(1);
				log.info("Started run from chat message trigger");
			}
		}
		// Detect moving to next floor (Floor 2-5)
		else if (message.contains("You venture further down into the Hallowed Sepulchre"))
		{
			int nextFloor = currentFloor + 1;
			if (nextFloor < 2) nextFloor = 2; // Safety: at least floor 2 if we missed floor 1
			
			log.info("Moving to floor {} via chat message", nextFloor);
			
			// If run was ended by region change, restart it
			if (currentRun == null)
			{
				log.info("Run was null, restarting for floor {}", nextFloor);
				startRun();
			}
			else if (currentFloor > 0)
			{
				completeFloor(currentFloor);
			}
			
			inSepulchre = true;
			currentFloor = nextFloor;
			floorStartTime = Instant.now();
			currentRun.startFloor(nextFloor);
		}
		// Detect floor completions - "You have completed Floor 4 of the Hallowed Sepulchre! Total completions: 113."
		else if (message.contains("You have completed Floor") && message.contains("Total completions:"))
		{
			// Extract floor number
			for (int f = 1; f <= 5; f++)
			{
				if (message.contains("Floor " + f))
				{
					log.info("Completed floor {} via chat message", f);
					if (currentRun != null && currentFloor == f)
					{
						completeFloor(f);
					}
					
					// Extract total completions from "Total completions: XXX"
					try
					{
						String totalStr = message.substring(message.indexOf("Total completions:") + 18);
						totalStr = totalStr.replaceAll("[^0-9]", "").trim();
						if (!totalStr.isEmpty())
						{
							int totalCompletions = Integer.parseInt(totalStr);
							persistentStats.setFloorCompletionsFromGame(f, totalCompletions);
							log.info("Floor {} total completions from game: {}", f, totalCompletions);
							savePersistentStats();
						}
					}
					catch (Exception e)
					{
						log.warn("Failed to parse total completions from: {}", message);
					}
					break;
				}
			}
		}
		// Detect floor time - "Floor 4 time: 2:05. Personal best: 1:38"
		else if (message.contains("Floor") && message.contains("time:") && message.contains("Personal best:"))
		{
			try
			{
				// Extract floor number
				int floorNum = 0;
				for (int f = 1; f <= 5; f++)
				{
					if (message.contains("Floor " + f + " time:"))
					{
						floorNum = f;
						break;
					}
				}
				
				if (floorNum > 0)
				{
					// Extract personal best time - "Personal best: 1:38"
					String pbStr = message.substring(message.indexOf("Personal best:") + 14).trim();
					pbStr = pbStr.replaceAll("[^0-9:]", "");
					String[] pbParts = pbStr.split(":");
					if (pbParts.length == 2)
					{
						long pbMs = (Long.parseLong(pbParts[0]) * 60 + Long.parseLong(pbParts[1])) * 1000;
						persistentStats.setPersonalBestFromGame(floorNum, pbMs);
						log.info("Floor {} personal best from game: {}:{}", floorNum, pbParts[0], pbParts[1]);
						savePersistentStats();
					}
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to parse floor time from: {}", message);
			}
		}
		// Detect returning to lobby
		else if (message.contains("teleports you back to the lobby"))
		{
			log.info("Detected return to lobby via chat message");
			if (currentRun != null)
			{
				endRun(true);
				currentFloor = 0;
			}
		}
	}
	
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!inSepulchre)
		{
			return;
		}
		
		int objectId = event.getId();
		String option = event.getMenuOption();
		
		if (objectId == COFFIN && "Search".equals(option))
		{
			handleCoffinLoot(currentFloor);
		}
		else if (objectId == GRAND_HALLOWED_COFFIN && "Search".equals(option))
		{
			handleGrandCoffinLoot();
		}
		else if (objectId == MAGICAL_OBELISK && "Activate".equals(option))
		{
			// Player is exiting via obelisk
			log.debug("Player activating magical obelisk to exit");
		}
	}
	
	private void handleRegionChange(int regionId)
	{
		int newFloor = getFloorFromRegion(regionId);
		boolean nowInSepulchre = isInSepulchreRegion(regionId);
		boolean isInLobby = regionId == LOBBY_REGION;
		
		// Always log region for debugging
		log.info("Region: {} | Floor: {} | InSep: {} | Lobby: {} | CurrentRun: {} | CurrentFloor: {}",
			regionId, newFloor, nowInSepulchre, isInLobby, 
			currentRun != null ? "ACTIVE" : "NULL", currentFloor);
		
		// If we're in a sepulchre region, reset transition counter
		if (nowInSepulchre)
		{
			transitionTickCount = 0;
		}
		
		// Entering Sepulchre area (lobby or floors) - from truly outside
		if (!inSepulchre && nowInSepulchre)
		{
			inSepulchre = true;
			
			// Only start a run when entering Floor 1, not when entering the lobby
			if (newFloor == 1)
			{
				startRun();
				currentFloor = 1;
				floorStartTime = Instant.now();
				currentRun.startFloor(1);
				log.debug("Started run - entered Floor 1 directly");
			}
			else if (isInLobby)
			{
				log.debug("Entered Sepulchre lobby");
			}
		}
		// In transition (not in a known sepulchre region but was recently)
		else if (inSepulchre && !nowInSepulchre)
		{
			transitionTickCount++;
			
			// Only truly leave if we've been out for too long
			if (transitionTickCount >= MAX_TRANSITION_TICKS)
			{
				log.debug("Left Hallowed Sepulchre");
				if (currentRun != null)
				{
					endRun(currentFloor > 0);
				}
				inSepulchre = false;
				currentFloor = 0;
				transitionTickCount = 0;
			}
			// Otherwise, we're probably just loading a new floor - stay patient
		}
		// Still in sepulchre (was in sepulchre, now in a known sepulchre region)
		else if (inSepulchre && nowInSepulchre)
		{
			// Moving from lobby to Floor 1 - START the run
			if (currentRun == null && newFloor == 1)
			{
				startRun();
				currentFloor = 1;
				floorStartTime = Instant.now();
				currentRun.startFloor(1);
				log.debug("Started run - entered Floor 1 from lobby");
			}
			// Floor change within Sepulchre (between floors, not lobby)
			else if (currentRun != null && newFloor != currentFloor && newFloor > 0)
			{
				log.debug("Entered floor {}", newFloor);
				if (currentFloor > 0)
				{
					completeFloor(currentFloor);
				}
				
				currentFloor = newFloor;
				floorStartTime = Instant.now();
				currentRun.startFloor(newFloor);
			}
			// Returned to lobby from a floor (run ended via obelisk or death)
			else if (isInLobby && currentRun != null && currentFloor > 0)
			{
				log.debug("Returned to lobby - run completed");
				endRun(true);
				currentFloor = 0;
			}
		}
	}
	
	private boolean isInSepulchreRegion(int regionId)
	{
		return regionId == LOBBY_REGION ||
			regionId == FLOOR_1_REGION_1 || regionId == FLOOR_1_REGION_2 || regionId == FLOOR_1_REGION_3 ||
			regionId == FLOOR_2_REGION_1 || regionId == FLOOR_2_REGION_2 || regionId == FLOOR_2_REGION_3 ||
			regionId == FLOOR_3_REGION_1 || regionId == FLOOR_3_REGION_2 || regionId == FLOOR_3_REGION_3 ||
			regionId == FLOOR_4_REGION_1 || regionId == FLOOR_4_REGION_2 || regionId == FLOOR_4_REGION_3 ||
			regionId == FLOOR_5_REGION_1 || regionId == FLOOR_5_REGION_2;
	}
	
	private int getFloorFromRegion(int regionId)
	{
		switch (regionId)
		{
			case FLOOR_1_REGION_1:
			case FLOOR_1_REGION_2:
			case FLOOR_1_REGION_3:
				return 1;
			case FLOOR_2_REGION_1:
			case FLOOR_2_REGION_2:
			case FLOOR_2_REGION_3:
				return 2;
			case FLOOR_3_REGION_1:
			case FLOOR_3_REGION_2:
			case FLOOR_3_REGION_3:
				return 3;
			case FLOOR_4_REGION_1:
			case FLOOR_4_REGION_2:
			case FLOOR_4_REGION_3:
				return 4;
			case FLOOR_5_REGION_1:
			case FLOOR_5_REGION_2:
				return 5;
			case LOBBY_REGION:
				return 0;
			default:
				return -1;
		}
	}
	
	private int getCurrentRegionId()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return -1;
		}
		
		return player.getWorldLocation().getRegionID();
	}
	
	private void startRun()
	{
		currentRun = new SepulchreRun();
		currentRun.setStartTime(Instant.now());
		currentRun.setStartXp(client.getSkillExperience(Skill.AGILITY));
		floorStartTime = Instant.now();
		
		log.debug("Started new Sepulchre run");
	}
	
	private void completeFloor(int floor)
	{
		if (currentRun == null)
		{
			return;
		}
		
		Duration floorTime = Duration.between(floorStartTime, Instant.now());
		currentRun.completeFloor(floor, floorTime);
		session.incrementFloorCompletion(floor);
		
		log.debug("Completed floor {} in {}", floor, formatDuration(floorTime));
	}
	
	private void endRun(boolean completed)
	{
		if (currentRun == null)
		{
			return;
		}
		
		// Complete the current floor if we're on one
		if (currentFloor > 0)
		{
			completeFloor(currentFloor);
		}
		
		currentRun.setEndTime(Instant.now());
		currentRun.setCompleted(completed);
		currentRun.setHighestFloor(currentFloor);
		
		// Add to session stats
		session.addRun(currentRun);
		
		// Add to persistent stats (saved across sessions)
		persistentStats.recordRun(currentRun);
		
		log.info("=== RUN RECORDED ===");
		log.info("  Floors reached: {}", currentRun.getHighestFloor());
		log.info("  XP gained: {}", currentRun.getTotalXp());
		log.info("  Duration: {}", formatDuration(currentRun.getDuration()));
		log.info("  Session runs: {}", session.getTotalRuns());
		log.info("  Today runs: {}", persistentStats.getToday().getRuns());
		log.info("  Today XP: {}", persistentStats.getToday().getTotalXp());
		log.info("===================");
		
		saveSession();
		savePersistentStats();
		currentRun = null;
	}
	
	private void handleCoffinLoot(int floor)
	{
		if (currentRun != null)
		{
			currentRun.incrementChestsLooted(floor);
			session.incrementChestsLooted(floor);
			log.debug("Looted coffin on floor {}", floor);
		}
	}
	
	private void handleGrandCoffinLoot()
	{
		if (currentRun != null)
		{
			currentRun.setLootedGrandCoffin(true);
			session.incrementGrandCoffinLooted();
			log.debug("Looted Grand Hallowed Coffin");
		}
	}
	
	private HallowedSepulchreSession loadSession()
	{
		String json = configManager.getConfiguration("hallowedsep", "session");
		if (json != null && !json.isEmpty())
		{
			try
			{
				return HallowedSepulchreSession.fromJson(json);
			}
			catch (Exception e)
			{
				log.warn("Failed to load session data", e);
			}
		}
		return new HallowedSepulchreSession();
	}
	
	private void saveSession()
	{
		if (session != null)
		{
			String json = session.toJson();
			configManager.setConfiguration("hallowedsep", "session", json);
		}
	}
	
	private PersistentStats loadPersistentStats()
	{
		String json = configManager.getConfiguration("hallowedsep", "persistent");
		if (json != null && !json.isEmpty())
		{
			try
			{
				return PersistentStats.fromJson(json);
			}
			catch (Exception e)
			{
				log.warn("Failed to load persistent stats", e);
			}
		}
		return new PersistentStats();
	}
	
	private void savePersistentStats()
	{
		if (persistentStats != null)
		{
			String json = persistentStats.toJson();
			configManager.setConfiguration("hallowedsep", "persistent", json);
		}
	}
	
	public void resetSession()
	{
		session = new HallowedSepulchreSession();
		saveSession();
		if (panel != null)
		{
			panel.updateStats();
		}
	}
	
	public void resetAllStats()
	{
		// Reset session
		session = new HallowedSepulchreSession();
		saveSession();
		
		// Reset all persistent stats
		persistentStats = new PersistentStats();
		savePersistentStats();
		
		log.info("All stats have been reset");
		
		if (panel != null)
		{
			panel.updateStats();
		}
	}
	
	public boolean isInSepulchre()
	{
		return inSepulchre;
	}
	
	/**
	 * Check if currently in a Sepulchre region (real-time check)
	 */
	public boolean isCurrentlyInSepulchreRegion()
	{
		int regionId = getCurrentRegionId();
		return isInSepulchreRegion(regionId);
	}
	
	public SepulchreRun getCurrentRun()
	{
		return currentRun;
	}
	
	public int getCurrentFloor()
	{
		return currentFloor;
	}
	
	public int getLastRegionId()
	{
		return lastRegionId;
	}
	
	public HallowedSepulchreSession getSession()
	{
		return session;
	}
	
	public PersistentStats getPersistentStats()
	{
		return persistentStats;
	}
	
	public int getCurrentAgilityLevel()
	{
		return client.getRealSkillLevel(Skill.AGILITY);
	}
	
	public int getCurrentAgilityXp()
	{
		return client.getSkillExperience(Skill.AGILITY);
	}
	
	public int getXpToLevel(int targetLevel)
	{
		int currentXp = getCurrentAgilityXp();
		int targetXp = Experience.getXpForLevel(targetLevel);
		return Math.max(0, targetXp - currentXp);
	}
	
	public int getNextLevel()
	{
		int currentLevel = getCurrentAgilityLevel();
		return Math.min(currentLevel + 1, 99);
	}
	
	public int getRunsToNextLevel()
	{
		int nextLevel = getNextLevel();
		if (getCurrentAgilityLevel() >= 99)
		{
			return 0;
		}
		return getRunsRemaining(nextLevel);
	}
	
	public int getRunsRemaining(int targetLevel)
	{
		int xpRemaining = getXpToLevel(targetLevel);
		
		// Use actual average XP per run from all-time stats first, then session, then config
		double xpPerRun = 0;
		
		if (persistentStats != null && persistentStats.getAllTimeRuns() > 0)
		{
			xpPerRun = persistentStats.getAllTimeAvgXpPerRun();
		}
		else if (session.getTotalRuns() > 0)
		{
			xpPerRun = session.getAverageXpPerRun();
		}
		
		if (xpPerRun <= 0)
		{
			// Fallback to config estimate
			xpPerRun = config.estimatedXpPerRun();
		}
		
		return (int) Math.ceil(xpRemaining / xpPerRun);
	}
	
	/**
	 * Get live XP per hour including current run time and XP
	 */
	public double getXpPerHour()
	{
		// Get completed session XP and time
		int completedXp = session.getTotalXp();
		long completedTimeMs = session.getTotalTimeMs();
		
		// Add current run XP and time if in a run
		int currentXp = 0;
		long currentTimeMs = 0;
		if (currentRun != null)
		{
			currentXp = currentRun.getTotalXp();
			currentTimeMs = currentRun.getDuration().toMillis();
		}
		
		int totalXp = completedXp + currentXp;
		long totalTimeMs = completedTimeMs + currentTimeMs;
		
		if (totalTimeMs == 0)
		{
			return 0;
		}
		
		double hours = totalTimeMs / 3_600_000.0;
		return totalXp / hours;
	}
	
	public static String formatDuration(Duration duration)
	{
		if (duration == null)
		{
			return "0:00";
		}
		
		long seconds = duration.getSeconds();
		long minutes = seconds / 60;
		long hours = minutes / 60;
		
		seconds %= 60;
		minutes %= 60;
		
		if (hours > 0)
		{
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%d:%02d", minutes, seconds);
	}
	
	public static String formatNumber(int number)
	{
		if (number >= 1_000_000)
		{
			return String.format("%.1fM", number / 1_000_000.0);
		}
		else if (number >= 1_000)
		{
			return String.format("%.1fK", number / 1_000.0);
		}
		return String.valueOf(number);
	}
	
	@Provides
	HallowedSepulchreConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HallowedSepulchreConfig.class);
	}
}
