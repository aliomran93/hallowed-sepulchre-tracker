package com.hallowedsep;

import lombok.Data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Data
public class PersistentStats
{
	// All-time stats
	private int allTimeRuns;
	private int allTimeXp;
	private int allTimeFails;
	private long allTimeMs;
	private Map<Integer, Integer> allTimeFloorCompletions = new HashMap<>();
	private int allTimeChestsLooted;
	private int allTimeGrandCoffins;
	
	// Best times (in milliseconds)
	private long bestRunTimeMs;
	private Map<Integer, Long> bestFloorTimes = new HashMap<>();
	private Map<Integer, Long> bestRunTimesByFloors = new HashMap<>();
	
	// Daily stats - keyed by date string (YYYY-MM-DD)
	private Map<String, DailyStats> dailyHistory = new LinkedHashMap<>();
	
	// Starting XP when we first started tracking
	private int startingXp;
	private String startDate;

	// Current goal phase stats. These are reset when the main goal changes.
	private String phaseStartDate;
	private int phaseStartLevel;
	private int phaseStartXp;
	private int phaseTargetLevel = 99;
	private int phaseRuns;
	private int phaseXp;
	private int phaseFails;
	private long phaseMs;
	
	public PersistentStats()
	{
		for (int i = 1; i <= 5; i++)
		{
			allTimeFloorCompletions.put(i, 0);
			bestFloorTimes.put(i, Long.MAX_VALUE);
			bestRunTimesByFloors.put(i, Long.MAX_VALUE);
		}
		this.startDate = LocalDate.now().toString();
		this.phaseStartDate = this.startDate;
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
		int fails = run.getFails();
		long timeMs = run.getDuration().toMillis();
		
		// Update all-time stats
		allTimeRuns++;
		allTimeXp += xp;
		allTimeFails += fails;
		allTimeMs += timeMs;

		// Update current goal phase stats
		phaseRuns++;
		phaseXp += xp;
		phaseFails += fails;
		phaseMs += timeMs;
		
		// Update best times only when the floor time came from the game's chat message
		for (Map.Entry<Integer, SepulchreRun.FloorData> entry : run.getFloorData().entrySet())
		{
			int floor = entry.getKey();
			SepulchreRun.FloorData floorData = entry.getValue();
			
			if (floorData.isCompleted() && floorData.isTimeFromGame() && floorData.getDuration() != null)
			{
				long floorTime = floorData.getDuration().toMillis();
				Long currentBest = bestFloorTimes.get(floor);
				if (currentBest == null || floorTime < currentBest)
				{
					bestFloorTimes.put(floor, floorTime);
				}
			}
		}
		
		// Update best total times using summed official floor splits
		if (run.isCompleted())
		{
			long totalSplitMs = 0;
			int floorsWithSplits = 0;
			for (int floor = 1; floor <= 5; floor++)
			{
				SepulchreRun.FloorData data = run.getFloorData().get(floor);
				if (data != null && data.isTimeFromGame() && data.getDuration() != null && data.getDuration().toMillis() > 0)
				{
					totalSplitMs += data.getDuration().toMillis();
					floorsWithSplits++;
				}
				else
				{
					break;
				}
			}

			if (floorsWithSplits > 0)
			{
				Long currentBest = bestRunTimesByFloors.get(floorsWithSplits);
				if (currentBest == null || currentBest == Long.MAX_VALUE || totalSplitMs < currentBest)
				{
					bestRunTimesByFloors.put(floorsWithSplits, totalSplitMs);
				}

				// Keep legacy full-run PB in sync when we have all five floors
				if (floorsWithSplits == 5 && (bestRunTimeMs == 0 || totalSplitMs < bestRunTimeMs))
				{
					bestRunTimeMs = totalSplitMs;
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
		today.addRun(xp, timeMs, fails);
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

	public double getAllTimeAvgFailsPerRun()
	{
		if (allTimeRuns == 0) return 0;
		return (double) allTimeFails / allTimeRuns;
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

	public long getBestRunTimeForFloorsMs(int floors)
	{
		Long time = bestRunTimesByFloors.get(floors);
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
		LocalDate start = getTrackingStartDate();
		LocalDate today = LocalDate.now();
		if (start.isAfter(today))
		{
			return 1;
		}
		return (int) ChronoUnit.DAYS.between(start, today) + 1;
	}
	
	public double getAverageRunsPerDay()
	{
		if (allTimeRuns == 0) return 0;
		return (double) allTimeRuns / getDaysTracked();
	}
	
	public double getAverageHoursPerDay()
	{
		if (allTimeMs == 0) return 0;
		return getAllTimeHours() / getDaysTracked();
	}

	public void resetGoalPhase(LocalDate phaseStart, int currentLevel, int currentXp, int targetLevel)
	{
		LocalDate start = phaseStart != null ? phaseStart : LocalDate.now();
		phaseStartDate = start.toString();
		phaseStartLevel = currentLevel;
		phaseStartXp = currentXp;
		phaseTargetLevel = targetLevel;
		phaseRuns = 0;
		phaseXp = 0;
		phaseFails = 0;
		phaseMs = 0;
	}

	public boolean ensureGoalPhase(int currentLevel, int currentXp, int targetLevel)
	{
		if (parseDate(phaseStartDate) == null || phaseTargetLevel != targetLevel)
		{
			resetGoalPhase(LocalDate.now(), currentLevel, currentXp, targetLevel);
			return true;
		}
		return false;
	}

	public int getPhaseDaysTracked()
	{
		LocalDate start = getPhaseTrackingStartDate();
		LocalDate today = LocalDate.now();
		if (start.isAfter(today))
		{
			return 1;
		}
		return (int) ChronoUnit.DAYS.between(start, today) + 1;
	}

	public int getPhaseActiveDays()
	{
		if (dailyHistory == null || dailyHistory.isEmpty())
		{
			return 0;
		}

		LocalDate start = getPhaseTrackingStartDate();
		LocalDate today = LocalDate.now();
		int activeDays = 0;
		for (DailyStats day : dailyHistory.values())
		{
			if (day == null || day.getRuns() <= 0)
			{
				continue;
			}

			LocalDate date = parseDate(day.getDate());
			if (date != null && !date.isBefore(start) && !date.isAfter(today))
			{
				activeDays++;
			}
		}
		return activeDays;
	}

	public int getPhaseMissedDays()
	{
		return Math.max(0, getPhaseDaysTracked() - getPhaseActiveDays());
	}

	public double getPhaseAverageRunsPerDay()
	{
		if (phaseRuns == 0) return 0;
		return (double) phaseRuns / getPhaseDaysTracked();
	}

	public double getPhaseAverageHoursPerDay()
	{
		if (phaseMs == 0) return 0;
		return (phaseMs / 3_600_000.0) / getPhaseDaysTracked();
	}

	public double getPhaseAverageFailsPerRun()
	{
		if (phaseRuns == 0) return 0;
		return (double) phaseFails / phaseRuns;
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
		if (bestRunTimesByFloors == null)
		{
			bestRunTimesByFloors = new HashMap<>();
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
			bestRunTimesByFloors.putIfAbsent(i, Long.MAX_VALUE);
		}

		if (parseDate(startDate) == null)
		{
			startDate = getEarliestHistoryDate().orElse(LocalDate.now()).toString();
		}
		if (parseDate(phaseStartDate) == null)
		{
			phaseStartDate = getTrackingStartDate().toString();
			phaseTargetLevel = phaseTargetLevel > 0 ? phaseTargetLevel : 99;
			if (phaseRuns == 0 && allTimeRuns > 0)
			{
				phaseRuns = allTimeRuns;
				phaseXp = allTimeXp;
				phaseFails = allTimeFails;
				phaseMs = allTimeMs;
			}
		}
	}

	private LocalDate getTrackingStartDate()
	{
		LocalDate parsedStartDate = parseDate(startDate);
		if (parsedStartDate != null)
		{
			return parsedStartDate;
		}
		return getEarliestHistoryDate().orElse(LocalDate.now());
	}

	private Optional<LocalDate> getEarliestHistoryDate()
	{
		if (dailyHistory == null || dailyHistory.isEmpty())
		{
			return Optional.empty();
		}

		LocalDate earliest = null;
		for (String date : dailyHistory.keySet())
		{
			LocalDate parsedDate = parseDate(date);
			if (parsedDate != null && (earliest == null || parsedDate.isBefore(earliest)))
			{
				earliest = parsedDate;
			}
		}
		return Optional.ofNullable(earliest);
	}

	private LocalDate getPhaseTrackingStartDate()
	{
		LocalDate parsedStartDate = parseDate(phaseStartDate);
		if (parsedStartDate != null)
		{
			return parsedStartDate;
		}
		return getTrackingStartDate();
	}

	private LocalDate parseDate(String date)
	{
		if (date == null || date.isEmpty())
		{
			return null;
		}

		try
		{
			return LocalDate.parse(date);
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
