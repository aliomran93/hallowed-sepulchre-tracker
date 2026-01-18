package com.hallowedsep;

import lombok.Data;

import java.time.LocalDate;
import java.util.*;

@Data
public class PersistentStats
{
	// All-time stats
	private int allTimeRuns;
	private int allTimeXp;
	private long allTimeMs;
	private Map<Integer, Integer> allTimeFloorCompletions = new HashMap<>();
	private int allTimeChestsLooted;
	private int allTimeGrandCoffins;
	
	// Best times (in milliseconds)
	private long bestRunTimeMs;
	private Map<Integer, Long> bestFloorTimes = new HashMap<>();
	
	// Daily stats - keyed by date string (YYYY-MM-DD)
	private Map<String, DailyStats> dailyHistory = new LinkedHashMap<>();
	
	// Starting XP when we first started tracking
	private int startingXp;
	private String startDate;
	
	public PersistentStats()
	{
		for (int i = 1; i <= 5; i++)
		{
			allTimeFloorCompletions.put(i, 0);
			bestFloorTimes.put(i, Long.MAX_VALUE);
		}
		this.startDate = LocalDate.now().toString();
	}
	
	public DailyStats getToday()
	{
		String today = LocalDate.now().toString();
		return dailyHistory.computeIfAbsent(today, DailyStats::new);
	}
	
	public void recordRun(SepulchreRun run)
	{
		if (run == null) return;
		
		int xp = run.getTotalXp();
		long timeMs = run.getDuration().toMillis();
		
		// Update all-time stats
		allTimeRuns++;
		allTimeXp += xp;
		allTimeMs += timeMs;
		
		// Update best run time
		if (run.isCompleted() && (bestRunTimeMs == 0 || timeMs < bestRunTimeMs))
		{
			bestRunTimeMs = timeMs;
		}
		
		// Update floor completions and best times
		for (Map.Entry<Integer, SepulchreRun.FloorData> entry : run.getFloorData().entrySet())
		{
			int floor = entry.getKey();
			SepulchreRun.FloorData floorData = entry.getValue();
			
			if (floorData.isCompleted())
			{
				allTimeFloorCompletions.merge(floor, 1, Integer::sum);
				
				if (floorData.getDuration() != null)
				{
					long floorTime = floorData.getDuration().toMillis();
					Long currentBest = bestFloorTimes.get(floor);
					if (currentBest == null || floorTime < currentBest)
					{
						bestFloorTimes.put(floor, floorTime);
					}
				}
			}
		}
		
		// Update chest counts
		allTimeChestsLooted += run.getTotalChestsLooted();
		if (run.isLootedGrandCoffin())
		{
			allTimeGrandCoffins++;
		}
		
		// Update daily stats
		DailyStats today = getToday();
		today.addRun(xp, timeMs);
		today.setChestsLooted(today.getChestsLooted() + run.getTotalChestsLooted());
		if (run.isLootedGrandCoffin())
		{
			today.setGrandCoffinsLooted(today.getGrandCoffinsLooted() + 1);
		}
		
		// Update floor completions for today
		for (Map.Entry<Integer, SepulchreRun.FloorData> entry : run.getFloorData().entrySet())
		{
			if (entry.getValue().isCompleted())
			{
				today.incrementFloor(entry.getKey());
			}
		}
	}
	
	public double getAllTimeHours()
	{
		return allTimeMs / 3_600_000.0;
	}
	
	public double getAllTimeXpPerHour()
	{
		if (allTimeMs == 0) return 0;
		return allTimeXp / (allTimeMs / 3_600_000.0);
	}
	
	public double getAllTimeAvgXpPerRun()
	{
		if (allTimeRuns == 0) return 0;
		return (double) allTimeXp / allTimeRuns;
	}
	
	public int getAllTimeFloorCompletions(int floor)
	{
		return allTimeFloorCompletions.getOrDefault(floor, 0);
	}
	
	public int getFloorCompletions(int floor)
	{
		return getAllTimeFloorCompletions(floor);
	}
	
	public long getBestFloorTimeMs(int floor)
	{
		Long time = bestFloorTimes.get(floor);
		return (time == null || time == Long.MAX_VALUE) ? 0 : time;
	}
	
	/**
	 * Set floor completions directly from the game's chat message.
	 * This uses the game's own tracking which is more accurate.
	 */
	public void setFloorCompletionsFromGame(int floor, int totalCompletions)
	{
		if (floor >= 1 && floor <= 5 && totalCompletions >= 0)
		{
			allTimeFloorCompletions.put(floor, totalCompletions);
		}
	}
	
	/**
	 * Set personal best time directly from the game's chat message.
	 */
	public void setPersonalBestFromGame(int floor, long timeMs)
	{
		if (floor >= 1 && floor <= 5 && timeMs > 0)
		{
			bestFloorTimes.put(floor, timeMs);
		}
	}
	
	public List<DailyStats> getRecentDays(int count)
	{
		List<DailyStats> days = new ArrayList<>(dailyHistory.values());
		Collections.reverse(days);
		return days.subList(0, Math.min(count, days.size()));
	}
	
	public int getDaysTracked()
	{
		return dailyHistory.size();
	}
	
	public double getAverageRunsPerDay()
	{
		if (dailyHistory.isEmpty()) return 0;
		return (double) allTimeRuns / dailyHistory.size();
	}
	
	public double getAverageHoursPerDay()
	{
		if (dailyHistory.isEmpty()) return 0;
		return getAllTimeHours() / dailyHistory.size();
	}

	/**
	 * Initialize maps after deserialization
	 */
	public void initializeAfterLoad()
	{
		if (allTimeFloorCompletions == null)
		{
			allTimeFloorCompletions = new HashMap<>();
		}
		if (bestFloorTimes == null)
		{
			bestFloorTimes = new HashMap<>();
		}
		if (dailyHistory == null)
		{
			dailyHistory = new LinkedHashMap<>();
		}
		
		// Ensure all floors have entries
		for (int i = 1; i <= 5; i++)
		{
			allTimeFloorCompletions.putIfAbsent(i, 0);
			bestFloorTimes.putIfAbsent(i, Long.MAX_VALUE);
		}
	}
}
