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
	/** Production input used with no matching XP (e.g. a burnt fish). */
	public boolean failedAction;
	/** Bank opened right after a production run finished. */
	public boolean bankClick;
	/** Ticks between the run's finish and the bank click; set with bankClick. */
	public int wasted;
	/** A confirmed high-alch cast landed this tick. */
	public boolean cast;
	/** Coins (High Alchemy value) of that cast (null = unknown value). */
	public Long castCoins;
	/** Ticks lost since the previous cast was ready (0 when on time or after a pause). */
	public int castLost;

	/** The player animated while a gathering pill was open. */
	public boolean roll;
	/** Item id gained by a gathering success this tick; 0 when none. */
	public int gathered;
	/** The gathered-from resource ran out this tick. */
	public boolean depleted;
	/** Ticks from a resource running out to the click on the next one; 0 when none. */
	public int reclick;
	/** Bank click while the inventory was full of gathered items. */
	public boolean gatherBank;

	public TickRecord(int tick)
	{
		this.tick = tick;
	}
}
