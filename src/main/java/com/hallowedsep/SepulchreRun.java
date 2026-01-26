package com.hallowedsep;

import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
public class SepulchreRun
{
	private Instant startTime;
	private Instant endTime;
	private Instant pausedAt;
	private long pausedDurationMs;
	private int startXp;
	private int totalXp;
	private int highestFloor;
	private boolean completed;
	private boolean lootedGrandCoffin;
	
	private Map<Integer, FloorData> floorData = new HashMap<>();
	private Map<Integer, Integer> chestsLootedPerFloor = new HashMap<>();
	
	public void startFloor(int floor)
	{
		FloorData data = new FloorData();
		data.setFloorNumber(floor);
		data.setStartTime(Instant.now());
		floorData.put(floor, data);
	}
	
	public void completeFloor(int floor, Duration duration)
	{
		FloorData data = floorData.get(floor);
		if (data != null)
		{
			data.setEndTime(Instant.now());
			data.setDuration(duration);
			data.setCompleted(true);
		}
		else
		{
			data = new FloorData();
			data.setFloorNumber(floor);
			data.setDuration(duration);
			data.setCompleted(true);
			floorData.put(floor, data);
		}
	}
	
	public void addXp(int xp)
	{
		this.totalXp += xp;
	}
	
	public void incrementChestsLooted(int floor)
	{
		chestsLootedPerFloor.merge(floor, 1, Integer::sum);
	}
	
	public int getChestsLooted(int floor)
	{
		return chestsLootedPerFloor.getOrDefault(floor, 0);
	}
	
	public int getTotalChestsLooted()
	{
		return chestsLootedPerFloor.values().stream().mapToInt(Integer::intValue).sum();
	}
	
	public Duration getDuration()
	{
		if (startTime == null)
		{
			return Duration.ZERO;
		}
		
		Instant end = endTime != null ? endTime : Instant.now();
		long totalMs = Duration.between(startTime, end).toMillis();
		long pausedMs = pausedDurationMs;
		if (pausedAt != null)
		{
			pausedMs += Duration.between(pausedAt, end).toMillis();
		}
		long effectiveMs = Math.max(0, totalMs - pausedMs);
		return Duration.ofMillis(effectiveMs);
	}
	
	public Duration getFloorDuration(int floor)
	{
		FloorData data = floorData.get(floor);
		if (data != null && data.getDuration() != null)
		{
			return data.getDuration();
		}
		return Duration.ZERO;
	}
	
	public int getFloorsCompleted()
	{
		return (int) floorData.values().stream().filter(FloorData::isCompleted).count();
	}

	public boolean isPaused()
	{
		return pausedAt != null;
	}

	public void pause()
	{
		if (pausedAt == null)
		{
			pausedAt = Instant.now();
		}
	}

	public void resume()
	{
		resumeAt(Instant.now());
	}

	public void resumeAt(Instant time)
	{
		if (pausedAt != null)
		{
			pausedDurationMs += Duration.between(pausedAt, time).toMillis();
			pausedAt = null;
		}
	}
	
	@Data
	public static class FloorData
	{
		private int floorNumber;
		private Instant startTime;
		private Instant endTime;
		private Duration duration;
		private boolean completed;
		private int xpGained;
		private int chestsLooted;
		private int deaths;
	}
}
