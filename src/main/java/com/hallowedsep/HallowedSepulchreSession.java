package com.hallowedsep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HallowedSepulchreSession
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
		.registerTypeAdapter(Duration.class, new DurationTypeAdapter())
		.create();
	
	private Instant sessionStartTime;
	private int totalRuns;
	private int totalXp;
	private long totalTimeMs;
	
	// Floor completion counts
	private Map<Integer, Integer> floorCompletions = new HashMap<>();
	
	// Chest loot counts per floor
	private Map<Integer, Integer> chestsLootedPerFloor = new HashMap<>();
	
	// Grand coffin loot count
	private int grandCoffinLooted;
	
	// Best times per floor (in milliseconds)
	private Map<Integer, Long> bestFloorTimes = new HashMap<>();
	
	// Best full run time (in milliseconds)
	private long bestRunTimeMs;
	
	// Recent runs for averaging (limited to last 50)
	private List<RunSummary> recentRuns = new ArrayList<>();
	
	// XP at session start for calculating session XP
	private int sessionStartXp;
	
	public HallowedSepulchreSession()
	{
		this.sessionStartTime = Instant.now();
		for (int i = 1; i <= 5; i++)
		{
			floorCompletions.put(i, 0);
			chestsLootedPerFloor.put(i, 0);
			bestFloorTimes.put(i, Long.MAX_VALUE);
		}
	}
	
	public void addRun(SepulchreRun run)
	{
		totalRuns++;
		totalXp += run.getTotalXp();
		totalTimeMs += run.getDuration().toMillis();
		
		// Update best run time if this was a full run
		if (run.isCompleted() && run.getDuration().toMillis() < bestRunTimeMs || bestRunTimeMs == 0)
		{
			bestRunTimeMs = run.getDuration().toMillis();
		}
		
		// Update best floor times
		for (Map.Entry<Integer, SepulchreRun.FloorData> entry : run.getFloorData().entrySet())
		{
			int floor = entry.getKey();
			SepulchreRun.FloorData floorData = entry.getValue();
			
			if (floorData.isCompleted() && floorData.getDuration() != null)
			{
				long floorTime = floorData.getDuration().toMillis();
				Long currentBest = bestFloorTimes.get(floor);
				
				if (currentBest == null || floorTime < currentBest)
				{
					bestFloorTimes.put(floor, floorTime);
				}
			}
		}
		
		// Add to recent runs (keep last 50)
		RunSummary summary = new RunSummary();
		summary.setXp(run.getTotalXp());
		summary.setDurationMs(run.getDuration().toMillis());
		summary.setHighestFloor(run.getHighestFloor());
		summary.setCompleted(run.isCompleted());
		summary.setTimestamp(Instant.now().toEpochMilli());
		
		recentRuns.add(summary);
		if (recentRuns.size() > 50)
		{
			recentRuns.remove(0);
		}
	}
	
	public void addXp(int xp)
	{
		// This is called per XP drop, but we track via addRun for totals
	}
	
	public void incrementFloorCompletion(int floor)
	{
		floorCompletions.merge(floor, 1, Integer::sum);
	}
	
	public void incrementChestsLooted(int floor)
	{
		chestsLootedPerFloor.merge(floor, 1, Integer::sum);
	}
	
	public void incrementGrandCoffinLooted()
	{
		grandCoffinLooted++;
	}
	
	public int getFloorCompletions(int floor)
	{
		return floorCompletions.getOrDefault(floor, 0);
	}
	
	public int getChestsLooted(int floor)
	{
		return chestsLootedPerFloor.getOrDefault(floor, 0);
	}
	
	public int getTotalChestsLooted()
	{
		return chestsLootedPerFloor.values().stream().mapToInt(Integer::intValue).sum();
	}
	
	public Duration getBestFloorTime(int floor)
	{
		Long timeMs = bestFloorTimes.get(floor);
		if (timeMs == null || timeMs == Long.MAX_VALUE)
		{
			return null;
		}
		return Duration.ofMillis(timeMs);
	}
	
	public Duration getBestRunTime()
	{
		if (bestRunTimeMs == 0)
		{
			return null;
		}
		return Duration.ofMillis(bestRunTimeMs);
	}
	
	public Duration getTotalTime()
	{
		return Duration.ofMillis(totalTimeMs);
	}
	
	public Duration getSessionDuration()
	{
		return Duration.between(sessionStartTime, Instant.now());
	}
	
	public double getAverageXpPerRun()
	{
		if (totalRuns == 0)
		{
			return 0;
		}
		return (double) totalXp / totalRuns;
	}
	
	public double getAverageTimePerRun()
	{
		if (totalRuns == 0)
		{
			return 0;
		}
		return (double) totalTimeMs / totalRuns;
	}
	
	public double getXpPerHour()
	{
		if (totalTimeMs == 0)
		{
			return 0;
		}
		
		double hours = totalTimeMs / 3_600_000.0;
		return totalXp / hours;
	}
	
	public double getRunsPerHour()
	{
		if (totalTimeMs == 0)
		{
			return 0;
		}
		
		double hours = totalTimeMs / 3_600_000.0;
		return totalRuns / hours;
	}
	
	public int getRecentAverageXp(int count)
	{
		if (recentRuns.isEmpty())
		{
			return 0;
		}
		
		int limit = Math.min(count, recentRuns.size());
		int startIndex = recentRuns.size() - limit;
		
		int sum = 0;
		for (int i = startIndex; i < recentRuns.size(); i++)
		{
			sum += recentRuns.get(i).getXp();
		}
		
		return sum / limit;
	}
	
	public String toJson()
	{
		return GSON.toJson(this);
	}
	
	public static HallowedSepulchreSession fromJson(String json)
	{
		HallowedSepulchreSession session = GSON.fromJson(json, HallowedSepulchreSession.class);
		
		// Initialize any null maps
		if (session.floorCompletions == null)
		{
			session.floorCompletions = new HashMap<>();
		}
		if (session.chestsLootedPerFloor == null)
		{
			session.chestsLootedPerFloor = new HashMap<>();
		}
		if (session.bestFloorTimes == null)
		{
			session.bestFloorTimes = new HashMap<>();
		}
		if (session.recentRuns == null)
		{
			session.recentRuns = new ArrayList<>();
		}
		
		// Ensure all floors have entries
		for (int i = 1; i <= 5; i++)
		{
			session.floorCompletions.putIfAbsent(i, 0);
			session.chestsLootedPerFloor.putIfAbsent(i, 0);
			session.bestFloorTimes.putIfAbsent(i, Long.MAX_VALUE);
		}
		
		return session;
	}
	
	@Data
	public static class RunSummary
	{
		private int xp;
		private long durationMs;
		private int highestFloor;
		private boolean completed;
		private long timestamp;
	}
}
