package com.superhistogram.model;

import java.util.HashMap;
import java.util.Map;

/** Turns total-XP stat reports into per-tick XP drops, ignoring login/hop baselines. */
public final class XpTracker
{
	private Timeline timeline;
	private final Map<String, Integer> lastTotal = new HashMap<>();
	private final Map<String, Long> gained = new HashMap<>();
	private long sessionXp;
	private XpDrop lastDrop;

	public XpTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	public void onStat(String skill, int totalXp, int tick)
	{
		Integer prev = lastTotal.put(skill, totalXp);
		if (prev == null || totalXp <= prev)
		{
			return;
		}
		int delta = totalXp - prev;
		XpDrop drop = new XpDrop(skill, delta);
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.xp.add(drop);
		}
		lastDrop = drop;
		sessionXp += delta;
		gained.merge(skill, (long) delta, Long::sum);
	}

	/** Points drops at a fresh timeline (after logout); session totals are kept. */
	public void rebind(Timeline newTimeline)
	{
		timeline = newTimeline;
	}

	/** Next stat per skill becomes a baseline again; session totals are kept. */
	public void onLoginScreen()
	{
		lastTotal.clear();
	}

	public long sessionXp()
	{
		return sessionXp;
	}

	public XpDrop lastDrop()
	{
		return lastDrop;
	}

	public long xpPerHour(String skill, long sessionMs)
	{
		if (sessionMs <= 0)
		{
			return 0;
		}
		return gained.getOrDefault(skill, 0L) * 3_600_000L / sessionMs;
	}

	/** Skill with the most XP gained this session, or null if none. */
	public String topSkill()
	{
		String best = null;
		long bestXp = 0;
		for (Map.Entry<String, Long> e : gained.entrySet())
		{
			if (e.getValue() > bestXp)
			{
				best = e.getKey();
				bestXp = e.getValue();
			}
		}
		return best;
	}

	/** Last reported total XP for the skill, or -1 if unknown. */
	public long currentXp(String skill)
	{
		Integer v = lastTotal.get(skill);
		return v == null ? -1 : v;
	}
}
