package com.superhistogram;

import com.superhistogram.model.BaselineSetting;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.PrayerMode;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("superhistogram")
public interface SuperHistogramConfig extends Config
{
	enum HullFlashMisses
	{
		HIDE,
		FAINT
	}

	enum Metronome
	{
		OFF,
		EVERY_TICK,
		/** Bright pulse on beat ticks only. */
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

	@ConfigItem(
		position = 10,
		keyName = "beatKey",
		name = "Beat hotkey",
		description = "Press during combat to start the beat metronome from your own attack rhythm"
	)
	default Keybind beatKey()
	{
		return new Keybind(KeyEvent.VK_F, 0);
	}

	@ConfigItem(
		position = 11,
		keyName = "prayerMode",
		name = "Prayer mode",
		description = "How you protect in combat"
	)
	default PrayerMode prayerMode()
	{
		return PrayerMode.LAZY_FLICK;
	}

	@ConfigItem(
		position = 12,
		keyName = "actionsRow",
		name = "Actions row",
		description = "What the combat view shows about your own actions"
	)
	default ActionsRow actionsRow()
	{
		return ActionsRow.ATTACKS;
	}

	@ConfigItem(
		position = 13,
		keyName = "showEatCosts",
		name = "Show eat costs",
		description = "Show how many attack ticks each eat cost you"
	)
	default boolean showEatCosts()
	{
		return true;
	}

	@ConfigItem(
		position = 14,
		keyName = "hullFlash",
		name = "Flash enemy on hit",
		description = "Briefly outline the enemy whose hitsplat just landed on you"
	)
	default boolean hullFlash()
	{
		return true;
	}

	@ConfigItem(
		position = 15,
		keyName = "hullFlashAtBosses",
		name = "Also at bosses",
		description = "Also flash bosses"
	)
	default boolean hullFlashAtBosses()
	{
		return false;
	}

	@ConfigItem(
		position = 16,
		keyName = "hullFlashMisses",
		name = "Misses",
		description = "Whether a 0 hit flashes the enemy faintly grey"
	)
	default HullFlashMisses hullFlashMisses()
	{
		return HullFlashMisses.FAINT;
	}
}
