package com.superhistogram.ui;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The user-overridable colours, as one immutable set: the four core ones as fields, the rest (prayers, metronome,
 * bar lines, skills) by key, each falling back to its {@link Palette} default when unset.
 */
public final class Colours
{
	public static final String PRAYER_MELEE = "prayerMelee", PRAYER_RANGED = "prayerRanged",
		PRAYER_MAGIC = "prayerMagic", PRAYER_OTHER = "prayerOther", FIGHT = "fight", MISS_FLASH = "missFlash",
		RIM_TICK = "rimTick", RIM_TOCK = "rimTock", NOW_LINE = "nowLine", ACTION = "action", LOST = "lost";

	public static final Colours DEFAULT = new Colours(Palette.GROUND, Palette.DAMAGE, Palette.MINT, Palette.RUN_ENERGY);

	public final Color ground, damage, mint, runEnergy;
	private final Map<String, Color> extra;

	public Colours(Color ground, Color damage, Color mint, Color runEnergy)
	{
		this(ground, damage, mint, runEnergy, Collections.emptyMap());
	}

	private Colours(Color ground, Color damage, Color mint, Color runEnergy, Map<String, Color> extra)
	{
		this.ground = ground;
		this.damage = damage;
		this.mint = mint;
		this.runEnergy = runEnergy;
		this.extra = extra;
	}

	/** A set where any null colour falls back to its default. */
	public static Colours orDefaults(Color ground, Color damage, Color mint, Color runEnergy)
	{
		return new Colours(ground != null ? ground : Palette.GROUND, damage != null ? damage : Palette.DAMAGE,
			mint != null ? mint : Palette.MINT, runEnergy != null ? runEnergy : Palette.RUN_ENERGY);
	}

	/** This set with {@code key} (one of the constants above, or {@link #skillKey}) set to {@code c}; null keeps the default. */
	public Colours with(String key, Color c)
	{
		if (c == null)
		{
			return this;
		}
		Map<String, Color> m = new HashMap<>(extra);
		m.put(key, c);
		return new Colours(ground, damage, mint, runEnergy, Collections.unmodifiableMap(m));
	}

	/** The colour set for {@code key}, or {@code def}. */
	public Color get(String key, Color def)
	{
		Color c = extra.get(key);
		return c == null ? def : c;
	}

	/** Key of a skill's colour, by its display name. */
	public static String skillKey(String skill)
	{
		return "skill:" + skill.toLowerCase();
	}
}
