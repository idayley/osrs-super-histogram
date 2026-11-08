package com.superhistogram.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Fixed-size ring of per-tick records plus the spans that cover them. */
public final class Timeline
{
	private final int capacity;
	private final TickRecord[] ring;
	private final List<Span> spans = new ArrayList<>();
	private int newest = -1;

	public Timeline(int capacity)
	{
		if (capacity < 1)
		{
			throw new IllegalArgumentException("capacity must be >= 1");
		}
		this.capacity = capacity;
		this.ring = new TickRecord[capacity];
	}

	/** Returns the record for the tick, creating it if absent; null if the tick is negative or too old. */
	public TickRecord at(int tick)
	{
		if (tick < 0 || (newest >= 0 && newest - tick >= capacity))
		{
			return null;
		}
		if (tick > newest)
		{
			newest = tick;
			pruneSpans();
		}
		int idx = tick % capacity;
		TickRecord r = ring[idx];
		if (r == null || r.tick != tick)
		{
			r = new TickRecord(tick);
			ring[idx] = r;
		}
		return r;
	}

	public TickRecord peek(int tick)
	{
		if (tick < 0)
		{
			return null;
		}
		TickRecord r = ring[tick % capacity];
		return r != null && r.tick == tick ? r : null;
	}

	public int newestTick()
	{
		return newest;
	}

	public Span openSpan(String kind, int startTick)
	{
		Span s = new Span(kind, startTick);
		spans.add(s);
		return s;
	}

	/** Closes the most recent open span of this kind, if any. */
	public void closeSpan(String kind, int endTick)
	{
		for (int i = spans.size() - 1; i >= 0; i--)
		{
			Span s = spans.get(i);
			if (s.endTick == -1 && s.kind.equals(kind))
			{
				s.endTick = endTick;
				s.closedAt = newest;
				return;
			}
		}
	}

	/** Spans overlapping [fromTick, toTick], oldest first. Open spans extend to infinity. */
	public List<Span> spansBetween(int fromTick, int toTick)
	{
		List<Span> out = new ArrayList<>();
		for (Span s : spans)
		{
			boolean endsAfterFrom = s.endTick == -1 || s.endTick >= fromTick;
			if (s.startTick <= toTick && endsAfterFrom)
			{
				out.add(s);
			}
		}
		return out;
	}

	private void pruneSpans()
	{
		int oldestRetained = newest - capacity + 1;
		for (Iterator<Span> it = spans.iterator(); it.hasNext(); )
		{
			Span s = it.next();
			if (s.endTick != -1 && s.endTick < oldestRetained)
			{
				it.remove();
			}
		}
	}
}
