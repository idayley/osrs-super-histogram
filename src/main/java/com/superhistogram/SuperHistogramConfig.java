package com.superhistogram;

import com.superhistogram.model.BaselineSetting;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("superhistogram")
public interface SuperHistogramConfig extends Config
{
	enum Metronome
	{
		OFF,
		EVERY_TICK,
		/** Behaves like OFF until Phase 2. */
		CUE_TICK
	}


	@ConfigItem(
		position = 1,
		keyName = "baseline",
		name = "Baseline view",
		description = "The view shown when no special mode (such as AFK) is detected"
	)
	default BaselineSetting baseline()
	{
		return BaselineSetting.GENERAL;
	}

	@Range(min = 10, max = 290)
	@ConfigItem(
		position = 2,
		keyName = "afkAfterSeconds",
		name = "AFK after (seconds)",
		description = "Seconds without input before the bar switches to the AFK view"
	)
	default int afkAfterSeconds()
	{
		return 60;
	}

	@ConfigItem(
		position = 3,
		keyName = "afkWarning",
		name = "AFK logout warning",
		description = "Warn at 4:30 idle, 30 seconds before the idle logout"
	)
	default boolean afkWarning()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "showRunEnergy",
		name = "Show run energy",
		description = "Show the run energy gauge in the left readout"
	)
	default boolean showRunEnergy()
	{
		return true;
	}

	@ConfigItem(
		position = 5,
		keyName = "showLoginTime",
		name = "Show login time",
		description = "Show the time online at the right end of the bar"
	)
	default boolean showLoginTime()
	{
		return true;
	}

	@ConfigItem(
		position = 6,
		keyName = "metronome",
		name = "Metronome",
		description = "Pulse the bar outline on game ticks"
	)
	default Metronome metronome()
	{
		return Metronome.OFF;
	}

	@ConfigItem(
		position = 7,
		keyName = "blinkWhenDone",
		name = "Blink when done",
		description = "Flash the bar mint when the AFK logout warning fires"
	)
	default boolean blinkWhenDone()
	{
		return true;
	}
}
