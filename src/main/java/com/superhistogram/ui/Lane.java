package com.superhistogram.ui;

/** The timeline's fixed lanes, top to bottom (see {@link BarLayout#laneY(Lane)}). */
public enum Lane
{
	/** Hits on you, XP drops, gold. */
	TOP,
	/** What you are doing: movement, the activity pill, the generic action pill. */
	DOING,
	/** Prayer pills with prayer points used. */
	PRAYER,
	/** Clicks, attacks, eats, bank clicks, depletion marks. Only on a 3-row bar. */
	INPUTS,
	/** Tick ruler. */
	RULER
}
