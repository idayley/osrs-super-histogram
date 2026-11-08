package com.superhistogram.model;

/** Opens and closes walk / run / action spans from per-tick movement. */
public final class MovementTracker
{
	private final Timeline timeline;
	private String kind;
	private Span span;
	private int startPct;
	private int lastPct;
	private int lastTick;

	public MovementTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	public void onTick(int tick, int tilesMoved, boolean animating, int runEnergyPct)
	{
		String next = tilesMoved >= 2 ? "run" : tilesMoved == 1 ? "walk" : animating ? "action" : null;
		if (next == null ? kind != null : !next.equals(kind))
		{
			close();
			if (next != null)
			{
				span = timeline.openSpan(next, tick);
				startPct = runEnergyPct;
			}
			kind = next;
		}
		lastPct = runEnergyPct;
		lastTick = tick;
	}

	private void close()
	{
		if (kind == null)
		{
			return;
		}
		if (kind.equals("run"))
		{
			span.label = "running " + startPct + "→" + lastPct + "%";
		}
		timeline.closeSpan(kind, lastTick);
		span = null;
	}
}
