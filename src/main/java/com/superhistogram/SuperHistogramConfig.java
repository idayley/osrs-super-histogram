package com.superhistogram;

import com.superhistogram.model.BaselineSetting;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.skill.AlchTopRow;
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
		description = "Flash the bar mint when an inventory finishes or fills, or the AFK logout warning fires"
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

	@ConfigItem(
		position = 24,
		keyName = "timeToFinish",
		name = "Time to finish",
		description = "Show the finish flag and the time left while an inventory is being made"
	)
	default boolean timeToFinish()
	{
		return true;
	}

	@ConfigItem(
		position = 25,
		keyName = "wastedTicks",
		name = "Wasted ticks (+N)",
		description = "Show how many ticks passed between the last item and your bank click"
	)
	default boolean wastedTicks()
	{
		return true;
	}

	@ConfigItem(
		position = 20,
		keyName = "xpDrops",
		name = "XP drops",
		description = "Show XP drops as numbers, as dots only, or not at all"
	)
	default XpDrops xpDrops()
	{
		return XpDrops.NUMBERS;
	}

	@ConfigItem(
		position = 21,
		keyName = "xpDots",
		name = "Frequent skills as dots",
		description = "Skills that drop XP on 5 of the last 10 ticks show as small dots instead of numbers"
	)
	default boolean xpDots()
	{
		return true;
	}

	@ConfigItem(
		position = 22,
		keyName = "alchTopRow",
		name = "Alch top row",
		description = "What the top row shows for each high-alch cast: its coins, its Magic XP, or both"
	)
	default AlchTopRow alchTopRow()
	{
		return AlchTopRow.GOLD;
	}

	@ConfigItem(
		position = 23,
		keyName = "rightSide",
		name = "Right side shows",
		description = "Whether the right readout's big number is the hourly rate or the session total"
	)
	default RightSide rightSide()
	{
		return RightSide.PER_HOUR;
	}
}
