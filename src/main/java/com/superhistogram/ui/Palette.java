package com.superhistogram.ui;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Every colour the bar uses, from design/spec.md § Colours (and the mockups where the spec is silent). */
public final class Palette
{
	public static final Color GROUND = new Color(20, 23, 27, Math.round(0.92f * 255));
	public static final Color TEXT = hex(0xe9ecef);
	public static final Color DIM = hex(0x7d8590);
	public static final Color WHITE = hex(0xffffff);

	public static final Color PRAYER_MELEE = hex(0xd0313a);
	public static final Color PRAYER_RANGED = hex(0x64ab31);
	public static final Color PRAYER_MAGIC = hex(0x1499dc);
	public static final Color PRAYER_OTHER = hex(0x9150d0);

	public static final Color DAMAGE = hex(0xffd23f);
	public static final Color BLOCKED = hex(0x5c636b);
	public static final Color MINT = hex(0x5ee0c8);
	public static final Color RUN_ENERGY = hex(0xe8c86a);

	// From the mockups (always-on-baseline.html), not named in the spec.
	public static final Color PILL_LABEL_FAINT = hex(0xaab1b9);
	public static final Color PILL_LABEL_SOLID = hex(0x14171b);
	public static final Color ONLINE_LABEL = hex(0x545b63);
	/** Solid pill for an animation whose kind is unknown (the mockup's "talk" colour). */
	public static final Color ACTION = hex(0x9fb4d9);
	public static final Color RULER = new Color(255, 255, 255, Math.round(0.14f * 255));
	public static final Color NOW_LINE = new Color(233, 236, 239, Math.round(0.5f * 255));
	public static final Color GAUGE_TRACK = new Color(255, 255, 255, Math.round(0.12f * 255));

	/** The lazy-flick mockup's .lost: "+N" labels and food icons. */
	public static final Color LOST = hex(0xc9ced4);
	public static final Color TRAIL_LINE = new Color(255, 255, 255, Math.round(0.12f * 255));
	public static final Color NEXT_BEAT = new Color(255, 255, 255, Math.round(0.35f * 255));

	/** Production view (cooking.html): the cooking pill, a burnt item's label, the "wasted ticks" line. */
	public static final Color COOK_PILL = hex(0xd9a35b);
	public static final Color BURNT = hex(0x6b737c);
	public static final Color WASTED_LINE = new Color(255, 255, 255, Math.round(0.55f * 255));

	/** The coin's ring (high-alch.html). */
	public static final Color COIN_RING = hex(0xb8901d);

	/** Alpha of walk / run / idle pills. */
	public static final float FAINT_ALPHA = 0.22f;

	private static final Map<String, Color> SKILLS = new HashMap<>();

	static
	{
		// From the spec.
		skill("Strength", 0x8fd694);
		skill("Hitpoints", 0xf2a7bd);
		skill("Slayer", 0xc7a3ff);
		skill("Magic", 0x8fc3ff);
		skill("Fletching", 0x7fd6c2);
		skill("Fishing", 0x8fd0e8);
		skill("Cooking", 0xf0b97a);
		skill("Agility", 0x7fa7c9);
		skill("Thieving", 0xc58fd6);
		skill("Attack", 0xe08a7a);
		skill("Defence", 0x9fb4d9);
		// Other skills: distinct pastels.
		skill("Ranged", 0xb9e08a);
		skill("Prayer", 0xf4e7a6);
		skill("Runecraft", 0xf5a98f);
		skill("Construction", 0xd8c3a0);
		skill("Crafting", 0xe6a8c8);
		skill("Mining", 0xb4bfcc);
		skill("Smithing", 0xc9b29a);
		skill("Herblore", 0xa6dba0);
		skill("Woodcutting", 0xc4d98c);
		skill("Farming", 0x9ee0b4);
		skill("Firemaking", 0xffb380);
		skill("Hunter", 0xd9c98c);
		skill("Sailing", 0xa3d3f0);
	}

	private Palette()
	{
	}

	/** Colour for a skill by its RuneLite display name; text colour if unknown. */
	public static Color skill(String name)
	{
		Color c = name == null ? null : SKILLS.get(name.toLowerCase());
		return c == null ? TEXT : c;
	}

	public static Color withAlpha(Color c, float alpha)
	{
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * c.getAlpha());
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	private static void skill(String name, int rgb)
	{
		SKILLS.put(name.toLowerCase(), hex(rgb));
	}

	private static Color hex(int rgb)
	{
		return new Color(rgb);
	}
}
