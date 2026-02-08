package com.hallowedsep;

import com.google.gson.Gson;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Hallowed Sepulchre Tracker",
	description = "Track your Hallowed Sepulchre runs, XP, floor completions, and progress to 99 Agility",
	tags = {"agility", "hallowed", "sepulchre", "xp", "tracker", "darkmeyer"}
)
public class HallowedSepulchrePlugin extends Plugin
{
	// Hallowed Sepulchre Region IDs
	private static final int LOBBY_REGION = 9565;
	
	// Floor regions (used to detect if player is still in Sepulchre area)
	// These are checked via getMapRegions() which returns all loaded regions
	private static final int[] SEPULCHRE_REGIONS = {
		9565,  // Lobby
		9309, 8797, 9052, 9054,  // Floor 1 variations
		9053, 9310, 10077,       // Floor 2 variations  
		9311, 9567, 9563,        // Floor 3 variations
		9823, 10079, 10075,      // Floor 4 variations
		10335, 10591             // Floor 5 variations
	};
	
	// Object IDs
	private static final int STAIRS_DOWN = 39526;
	private static final int STAIRS_UP = 39527;
	private static final int COFFIN = 39545;
	private static final int GRAND_HALLOWED_COFFIN = 39546;
	private static final int MAGICAL_OBELISK = 39558;

	private static final Pattern FLOOR_TIME_PATTERN = Pattern.compile("Floor\\s+(\\d+)\\s+time:\\s*([0-9]+:[0-9]{1,2})");
	
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
	
	@Inject
	private Gson gson;
	
	private Gson configuredGson;
	
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
	private boolean hidePluginTabOutsideSepulchre;
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Hallowed Sepulchre Tracker started!");
		
		// Configure Gson with custom type adapters using the injected Gson
		configuredGson = gson.newBuilder()
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.registerTypeAdapter(Duration.class, new DurationTypeAdapter())
			.create();
		
		session = loadSession();
		persistentStats = loadPersistentStats();
		currentRun = null;
		inSepulchre = false;
		currentFloor = 0;
		lastAgilityXp = -1;
		lastRegionId = -1;
		hidePluginTabOutsideSepulchre = config.hidePluginTabOutsideSepulchre();
		
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

		updatePluginTabVisibility();
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
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// Reset state on logout/hop to prevent stale inSepulchre flag
			if (currentRun != null)
			{
				endRun(false);
			}
			inSepulchre = false;
			currentFloor = 0;
			lastRegionId = -1;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("hallowedsep"))
		{
			hidePluginTabOutsideSepulchre = config.hidePluginTabOutsideSepulchre();
			updatePluginTabVisibility();
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
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"hallowedsep".equals(event.getGroup()))
		{
			return;
		}
		
		if ("trackBetweenFloorIdle".equals(event.getKey()) && !config.trackBetweenFloorIdle())
		{
			if (currentRun != null && currentRun.isPaused())
			{
				currentRun.resume();
			}
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
				if (!currentRun.isPaused() && floorStartTime != null)
				{
					completeFloor(currentFloor);
				}
			}
			
			resumeRunForNextFloor();
			inSepulchre = true;
			currentFloor = nextFloor;
			floorStartTime = Instant.now();
			currentRun.startFloor(nextFloor);
		}
		// Detect between-floor idle starts
		else if (config.trackBetweenFloorIdle() && (message.contains("You jump across the platform.") || message.contains("You squeeze through the gate")))
		{
			if (currentRun != null && currentFloor > 0)
			{
				log.info("Detected between-floor idle on floor {} via chat", currentFloor);
				enterIdleBetweenFloors();
			}
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
		else if (message.contains("Floor") && message.contains("time:"))
		{
			try
			{
				String cleanMessage = message.replaceAll("<[^>]+>", "");
				int floorNum = 0;
				Matcher matcher = FLOOR_TIME_PATTERN.matcher(cleanMessage);
				if (matcher.find())
				{
					floorNum = Integer.parseInt(matcher.group(1));
					long floorTimeMs = parseTimeMs(matcher.group(2));
					if (currentRun != null && floorTimeMs > 0)
					{
						currentRun.setFloorTimeFromGame(floorNum, Duration.ofMillis(floorTimeMs));
					}
				}
				else
				{
					for (int f = 1; f <= 5; f++)
					{
						if (message.contains("Floor " + f + " time:"))
						{
							floorNum = f;
							break;
						}
					}
				}
				
				if (floorNum > 0 && message.contains("Personal best:"))
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
		else if (message.contains("teleports you back to the lobby") || message.contains("make your way back to the lobby"))
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
		boolean isInLobby = regionId == LOBBY_REGION;
		boolean stillInSepulchreArea = isInAnySepulchreRegion();
		
		// Track if we're in the lobby for overlay visibility
		if (isInLobby)
		{
			inSepulchre = true;
			
			// If we returned to lobby with an active run, end it
			// (backup for when obelisk chat message is missed)
			if (currentRun != null && currentFloor > 0)
			{
				log.info("Returned to lobby - ending run");
				endRun(true);
				currentFloor = 0;
			}
		}
		// Check if player teleported out of Sepulchre entirely
		else if (currentRun != null && !stillInSepulchreArea)
		{
			log.info("Teleported out of Sepulchre - ending run");
			endRun(false);
			inSepulchre = false;
			currentFloor = 0;
		}

		updatePluginTabVisibility();
	}
	
	/**
	 * Check if any of the currently loaded map regions are Sepulchre regions.
	 * Uses getMapRegions() which returns all loaded regions (not just player's exact region).
	 */
	private boolean isInAnySepulchreRegion()
	{
		int[] loadedRegions = client.getMapRegions();
		if (loadedRegions == null)
		{
			return false;
		}
		
		for (int loaded : loadedRegions)
		{
			for (int sepRegion : SEPULCHRE_REGIONS)
			{
				if (loaded == sepRegion)
				{
					return true;
				}
			}
		}
		return false;
	}
	
	
	private int getCurrentRegionId()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return -1;
		}
		
		// Use simple world location - lobby region works with this
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

	private void enterIdleBetweenFloors()
	{
		if (currentRun != null)
		{
			if (!currentRun.isPaused() && floorStartTime != null && currentFloor > 0)
			{
				completeFloor(currentFloor);
			}
			currentRun.pause();
			floorStartTime = null;
		}
	}

	private void resumeRunForNextFloor()
	{
		if (currentRun != null)
		{
			currentRun.resume();
		}
	}
	
	private void completeFloor(int floor)
	{
		if (currentRun == null || floorStartTime == null)
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
		
		Instant endTime = Instant.now();
		if (currentRun.isPaused())
		{
			currentRun.resumeAt(endTime);
		}
		
		// Complete the current floor if we're on one
		if (currentFloor > 0)
		{
			completeFloor(currentFloor);
		}
		
		currentRun.setEndTime(endTime);
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
				HallowedSepulchreSession loaded = configuredGson.fromJson(json, HallowedSepulchreSession.class);
				if (loaded != null)
				{
					loaded.initializeAfterLoad();
					return loaded;
				}
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
			String json = configuredGson.toJson(session);
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
				PersistentStats loaded = configuredGson.fromJson(json, PersistentStats.class);
				if (loaded != null)
				{
					loaded.initializeAfterLoad();
					return loaded;
				}
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
			String json = configuredGson.toJson(persistentStats);
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
	 * Check if currently in the Sepulchre (for overlay visibility)
	 */
	public boolean isCurrentlyInSepulchreRegion()
	{
		// Show overlay if in any Sepulchre region OR if we have an active run
		return isInAnySepulchreRegion() || currentRun != null;
	}
	
	public SepulchreRun getCurrentRun()
	{
		return currentRun;
	}
	
	public boolean isRunIdle()
	{
		return currentRun != null && currentRun.isPaused();
	}
	
	public int getCurrentFloor()
	{
		return currentFloor;
	}

	public Instant getFloorStartTime()
	{
		return floorStartTime;
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

	private static long parseTimeMs(String timeStr)
	{
		if (timeStr == null)
		{
			return 0;
		}
		String[] parts = timeStr.trim().split(":");
		if (parts.length != 2)
		{
			return 0;
		}
		try
		{
			long minutes = Long.parseLong(parts[0]);
			long seconds = Long.parseLong(parts[1]);
			return (minutes * 60 + seconds) * 1000;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
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

	private void updatePluginTabVisibility()
	{
		if (hidePluginTabOutsideSepulchre && !isInAnySepulchreRegion() && currentRun == null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		else
		{
			clientToolbar.addNavigation(navButton);
		}
	}
	
	@Provides
	HallowedSepulchreConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HallowedSepulchreConfig.class);
	}
}
