package com.superhistogram.ui;

/** Pure timing for the per-tick metronome outline and the "done" mint double-blink. */
public final class Pulse
{
	private static final long OUTLINE_MS = 500;
	private static final long CUE_MS = 550;
	private static final float OUTLINE_PEAK = 0.32f;
	private static final float CUE_PEAK = 0.95f;

	private static final float MINT_PEAK = 0.55f;
	private static final long MINT_MS = 1000;
	/** Keyframe times (ms) and alphas, matching the mockup's 0 / 18% / 32% / 100% of 1 s. */
	private static final long[] MINT_T = {0, 180, 320, 1000};
	private static final float[] MINT_A = {MINT_PEAK, 0f, MINT_PEAK, 0f};

	public static final float OUTLINE_WIDTH = 1.5f;
	public static final float CUE_WIDTH = 2.5f;

	private static final long NONE = Long.MIN_VALUE;

	private long tickAt = NONE;
	private boolean tickCue;
	private boolean tickSuppressed;
	private long doneAt = NONE;

	public void tick(long nowMs, boolean cue)
	{
		tickAt = nowMs;
		tickCue = cue;
		tickSuppressed = doneAt == nowMs;
	}

	public void done(long nowMs)
	{
		doneAt = nowMs;
		if (tickAt == nowMs)
		{
			tickSuppressed = true;
		}
	}

	/** 0..1. */
	public float outlineAlpha(long nowMs)
	{
		if (tickAt == NONE || tickSuppressed)
		{
			return 0f;
		}
		long dur = tickCue ? CUE_MS : OUTLINE_MS;
		long age = nowMs - tickAt;
		if (age < 0 || age >= dur)
		{
			return 0f;
		}
		float peak = tickCue ? CUE_PEAK : OUTLINE_PEAK;
		return peak * (1f - (float) age / dur);
	}

	/** Stroke width of the pulse currently showing, or 0 when none is. */
	public float outlineWidth(long nowMs)
	{
		if (outlineAlpha(nowMs) <= 0f)
		{
			return 0f;
		}
		return tickCue ? CUE_WIDTH : OUTLINE_WIDTH;
	}

	/** 0..0.55. */
	public float mintAlpha(long nowMs)
	{
		if (doneAt == NONE)
		{
			return 0f;
		}
		long age = nowMs - doneAt;
		if (age < 0 || age >= MINT_MS)
		{
			return 0f;
		}
		for (int i = 1; i < MINT_T.length; i++)
		{
			if (age <= MINT_T[i])
			{
				float f = (float) (age - MINT_T[i - 1]) / (MINT_T[i] - MINT_T[i - 1]);
				return MINT_A[i - 1] + (MINT_A[i] - MINT_A[i - 1]) * f;
			}
		}
		return 0f;
	}
}
