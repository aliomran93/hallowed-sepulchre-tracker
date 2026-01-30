package com.hallowedsep;

import lombok.Data;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
public class DailyStats
{
	private String date; // YYYY-MM-DD format
	private int runs;
	private int totalXp;
	private long totalTimeMs;
	private Map<Integer, Integer> floorCompletions = new HashMap<>();
	private int chestsLooted;
	private int grandCoffinsLooted;
	private int failedRuns;
	
	public DailyStats()
	{
		this.date = LocalDate.now().toString();
		for (int i = 1; i <= 5; i++)
		{
			floorCompletions.put(i, 0);
		}
	}
	
	public DailyStats(String date)
	{
		this.date = date;
		for (int i = 1; i <= 5; i++)
		{
			floorCompletions.put(i, 0);
		}
	}
	
	public void addRun(int xp, long timeMs)
	{
		this.runs++;
		this.totalXp += xp;
		this.totalTimeMs += timeMs;
	}
	
	public void incrementFloor(int floor)
	{
		floorCompletions.merge(floor, 1, Integer::sum);
	}
	
	public int getFloorCompletions(int floor)
	{
		return floorCompletions.getOrDefault(floor, 0);
	}
	
	public double getHoursPlayed()
	{
		return totalTimeMs / 3_600_000.0;
	}
	
	public double getXpPerHour()
	{
		if (totalTimeMs == 0) return 0;
		return totalXp / (totalTimeMs / 3_600_000.0);
	}
	
	public double getAverageXpPerRun()
	{
		if (runs == 0) return 0;
		return (double) totalXp / runs;
	}
	
	public double getAverageFailsPerRun()
	{
		if (runs == 0) return 0;
		return (double) failedRuns / runs;
	}
	
	public void incrementFailedRuns()
	{
		this.failedRuns++;
	}
}
