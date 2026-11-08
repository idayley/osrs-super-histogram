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

	public void reset()
	{
		lastSwing.clear();
	}
}
