package com.superhistogram.model;

import java.util.ArrayList;
import java.util.List;

public final class TickRecord
{
	public final int tick;
	public final List<XpDrop> xp = new ArrayList<>();
	public final List<Hit> hits = new ArrayList<>();
	/** In-tick click fraction 0..1 when known, else 0. */
	public final List<Float> clicks = new ArrayList<>();

	public TickRecord(int tick)
	{
		this.tick = tick;
	}
}
