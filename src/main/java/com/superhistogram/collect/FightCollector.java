package com.superhistogram.collect;

import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;

/** Turns "the player is in combat with this NPC" into a "fight" span labelled with the NPC's name. */
public final class FightCollector
{
	public static final String KIND = "fight";

	private final Timeline timeline;
	private String current;

	public FightCollector(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** targetName is the NPC the player is fighting, or null when not in combat. */
	public void onTick(int tick, String targetName)
	{
		if (targetName == null ? current == null : targetName.equals(current))
		{
			return;
		}
		if (current != null)
		{
			timeline.closeSpan(KIND, Math.max(0, tick - 1));
		}
		if (targetName != null)
		{
			Span s = timeline.openSpan(KIND, tick);
			s.label = targetName;
		}
		current = targetName;
	}
}
