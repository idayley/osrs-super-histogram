package com.superhistogram.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines an enemy for half a second after its hitsplat landed on the player. It draws only from
 * a target set by a landed hitsplat; it never marks who an NPC is attacking.
 */
public final class HullFlashOverlay extends Overlay
{
	public static final long FADE_MS = 500;
	public static final float MISS_PEAK = 0.5f;
	public static final int WIDTH = 4, FEATHER = 4;

	public static final class Target
	{
		public final NPC npc;
		public final long startMs;
		public final boolean miss;

		public Target(NPC npc, long startMs, boolean miss)
		{
			this.npc = npc;
			this.startMs = startMs;
			this.miss = miss;
		}
	}

	private final ModelOutlineRenderer renderer;
	private final Supplier<Target> target;
	private volatile Colours colours = Colours.DEFAULT;

	public HullFlashOverlay(ModelOutlineRenderer renderer, Supplier<Target> target)
	{
		this.renderer = renderer;
		this.target = target;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	public void setColours(Colours c)
	{
		this.colours = c == null ? Colours.DEFAULT : c;
	}

	/** 1 at the hit, falling linearly to 0 at 500 ms; 0 for negative ages. */
	public static float alpha(long ageMs)
	{
		if (ageMs < 0 || ageMs >= FADE_MS)
		{
			return 0f;
		}
		return 1f - (float) ageMs / FADE_MS;
	}

	/** Opacity of the flash at this age: a miss peaks at {@link #MISS_PEAK}, a hit at 1. */
	public static float opacity(boolean miss, long ageMs)
	{
		return alpha(ageMs) * (miss ? MISS_PEAK : 1f);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Target t = target.get();
		if (t == null || t.npc == null)
		{
			return null;
		}
		float a = opacity(t.miss, System.currentTimeMillis() - t.startMs);
		if (a <= 0f)
		{
			return null;
		}
		Color base = t.miss ? Palette.BLOCKED : colours.damage;
		Color c = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(255 * a));
		renderer.drawOutline(t.npc, WIDTH, c, FEATHER);
		return null;
	}
}
