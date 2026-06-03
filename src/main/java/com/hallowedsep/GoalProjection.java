package com.hallowedsep;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class GoalProjection
{
	private GoalProjection()
	{
	}

	static List<Integer> parseMilestoneLevels(String configuredLevels, int currentLevel, int targetLevel)
	{
		Set<Integer> levels = new TreeSet<>();
		if (configuredLevels == null || configuredLevels.trim().isEmpty())
		{
			return new ArrayList<>(levels);
		}

		String[] parts = configuredLevels.split("[,\\s]+");
		for (String part : parts)
		{
			try
			{
				int level = Integer.parseInt(part.trim());
				if (level > currentLevel && level < targetLevel && level >= 1 && level <= 99)
				{
					levels.add(level);
				}
			}
			catch (NumberFormatException ignored)
			{
				// Ignore malformed entries so one typo does not hide valid milestones.
			}
		}
		return new ArrayList<>(levels);
	}

	static int daysRemaining(int runsRemaining, double runsPerDay)
	{
		if (runsRemaining <= 0 || runsPerDay <= 0)
		{
			return 0;
		}
		return (int) Math.ceil(runsRemaining / runsPerDay);
	}
}
