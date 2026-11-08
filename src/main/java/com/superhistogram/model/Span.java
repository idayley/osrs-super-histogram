package com.superhistogram.model;

public final class Span
{
	public final String kind;
	public final int startTick;
	/** Inclusive; -1 while the span is open. */
	public int endTick = -1;
	/** Newest tick when the span was closed (it can end earlier than that); -1 while open. */
	public int closedAt = -1;
	public String label;

	public Span(String kind, int startTick)
	{
		this.kind = kind;
		this.startTick = startTick;
	}
}
