package com.superhistogram.model;

/** Picks the bar's view: AFK wins; otherwise the baseline setting. */
public final class ModeDetector
{
	public Mode detect(boolean afk, BaselineSetting baseline)
	{
		if (afk)
		{
			return Mode.AFK;
		}
		return baseline == BaselineSetting.MINIMAL ? Mode.MINIMAL : Mode.GENERAL;
	}
}
