package com.superhistogram.model;

import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.FoodKind;
import java.util.ArrayList;
import java.util.List;

public final class TickRecord
{
	public final int tick;
	public final List<XpDrop> xp = new ArrayList<>();
	public final List<Hit> hits = new ArrayList<>();
	/** In-tick click fraction 0..1 when known, else 0. */
	public final List<Float> clicks = new ArrayList<>();
	/** In-tick fractions of prayer-toggle clicks. */
	public final List<Float> prayerClicks = new ArrayList<>();
	/** 1-tick flick verdict for this tick; null until classified. */
	public FlickClassifier.Kind flick;
	public float flickPhase;
	/** Late ticks of an attack this tick; -1 when no attack. */
	public int attackLate = -1;
	public FoodKind eat;
	public int eatCost;

	public TickRecord(int tick)
	{
		this.tick = tick;
	}
}
