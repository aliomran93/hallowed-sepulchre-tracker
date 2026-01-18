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
		return Duration.between(startTime, end);
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
