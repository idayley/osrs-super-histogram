package com.superhistogram.model.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Works out which NPC a hitsplat on the player came from, so its hull can flash after the hit has
 * landed. Only used for that choice; never for display. Ambiguous means no answer.
 */
public final class HitAttribution
{
	public static final int WINDOW_TICKS = 5;

	private final Map<Integer, Integer> lastSwing = new HashMap<>();

	public void onSwing(int npcIndex, int tick)
	{
		lastSwing.put(npcIndex, tick);
	}

	public void forget(int npcIndex)
	{
		lastSwing.remove(npcIndex);
	}

	/** The one NPC whose latest swing is within the window, or -1 when none or several. */
	public int attribute(int hitTick)
	{
		int found = -1;
		for (Map.Entry<Integer, Integer> e : lastSwing.entrySet())
		{
			int t = e.getValue();
			if (t >= hitTick - WINDOW_TICKS && t <= hitTick)
			{
				if (found != -1)
				{
					return -1;
				}
				found = e.getKey();
			}
		}
		return found;
	}

	/**
	 * The NPC that swung at you most recently within the window, or -1 when none or when two swung on that same
	 * tick. Used to drop hits from ignored enemies, where several monsters are attacking at once.
	 */
	public int latest(int hitTick)
	{
		int found = -1, best = Integer.MIN_VALUE;
		for (Map.Entry<Integer, Integer> e : lastSwing.entrySet())
		{
			int t = e.getValue();
			if (t < hitTick - WINDOW_TICKS || t > hitTick || t < best)
			{
				continue;
			}
			found = t == best ? -1 : e.getKey();
			best = t;
		}
		return found;
	}

	/** Any NPC swung at you within the window (one or several). */
	public boolean anySwing(int hitTick)
	{
		for (int t : lastSwing.values())
		{
			if (t >= hitTick - WINDOW_TICKS && t <= hitTick)
			{
				return true;
			}
		}
		return false;
	}

	public void reset()
	{
		lastSwing.clear();
	}
}
