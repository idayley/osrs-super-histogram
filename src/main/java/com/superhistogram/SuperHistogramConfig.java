package com.superhistogram;

import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import com.superhistogram.model.BaselineSetting;
import com.superhistogram.model.ModeSetting;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.LapCompare;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.PrayerMode;
import java.awt.event.KeyEvent;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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

	@ConfigSection(
		name = "Bar",
		description = "Bar settings",
		position = 0
	)
	String BAR = "bar";

	@ConfigSection(
		name = "Feedback",
		description = "Feedback settings",
		position = 1
	)
	String FEEDBACK = "feedback";

	@ConfigSection(
		name = "Combat",
		description = "Combat settings",
		position = 2
	)
	String COMBAT = "combat";

	@ConfigSection(
		name = "XP & gold",
		description = "XP & gold settings",
		position = 3
	)
	String XP_GOLD = "xpGold";

	@ConfigSection(
		name = "Skilling",
		description = "Skilling settings",
		position = 4
	)
	String SKILLING = "skilling";

	@ConfigSection(
		name = "Colours",
		description = "Colours settings",
		position = 5,
		closedByDefault = true
	)
	String COLOURS = "colours";

	@ConfigItem(
		position = 1,
		keyName = "position",
		name = "Position",
		description = "Replace chat: over the chat area. Above chat: just above it. Floating: drag it anywhere",
		section = BAR
	)
	default BarPosition position()
	{
		return BarPosition.REPLACE_CHAT;
	}

	@ConfigItem(
		position = 2,
		keyName = "size",
		name = "Size",
		description = "Bar height: small 40, normal 62, large 80. Resizing by hand wins until you change this again",
		section = BAR
	)
	default BarSize size()
	{
		return BarSize.NORMAL;
	}

	@ConfigItem(
		position = 3,
		keyName = "mode",
		name = "Mode",
		description = "Auto-detect the view, or pin one (it overrides detection, including AFK)",
		section = BAR
	)
	default ModeSetting mode()
	{
		return ModeSetting.AUTO;
	}

	@ConfigItem(
		position = 4,
		keyName = "baseline",
		name = "Baseline when idle",
		description = "The view shown when no special mode (such as AFK) is detected",
		section = BAR
	)
	default BaselineSetting baseline()
	{
		return BaselineSetting.GENERAL;
	}

	@Range(min = 10, max = 290)
	@ConfigItem(
		position = 5,
		keyName = "afkAfterSeconds",
		name = "Switch to AFK after",
		description = "Seconds without input before the bar switches to the AFK view",
		section = BAR
	)
	default int afkAfterSeconds()
	{
		return 60;
	}

	@ConfigItem(
		position = 6,
		keyName = "afkWarning",
		name = "AFK logout warning",
		description = "Warn at 4:30 idle, 30 seconds before the idle logout",
		section = BAR
	)
	default boolean afkWarning()
	{
		return true;
	}

	@ConfigItem(
		position = 7,
		keyName = "showRunEnergy",
		name = "Show run energy",
		description = "Show the run energy gauge in the left readout",
		section = BAR
	)
	default boolean showRunEnergy()
	{
		return true;
	}

	@ConfigItem(
		position = 8,
		keyName = "showLoginTime",
		name = "Show login time",
		description = "Show the time online at the right end of the bar",
		section = BAR
	)
	default boolean showLoginTime()
	{
		return true;
	}

	@ConfigItem(
		position = 9,
		keyName = "metronome",
		name = "Metronome",
		description = "Pulse the bar outline on game ticks",
		section = FEEDBACK
	)
	default Metronome metronome()
	{
		return Metronome.OFF;
	}

	@ConfigItem(
		position = 10,
		keyName = "blinkWhenDone",
		name = "Blink when done",
		description = "Flash the bar mint when an inventory finishes or fills, an agility lap ends, or the AFK logout warning fires",
		section = FEEDBACK
	)
	default boolean blinkWhenDone()
	{
		return true;
	}

	@ConfigItem(
		position = 11,
		keyName = "prayerMode",
		name = "Prayer mode",
		description = "How you protect in combat",
		section = COMBAT
	)
	default PrayerMode prayerMode()
	{
		return PrayerMode.LAZY_FLICK;
	}

	@ConfigItem(
		position = 12,
		keyName = "beatKey",
		name = "Beat hotkey",
		description = "Press during combat to start the beat metronome from your own attack rhythm",
		section = COMBAT
	)
	default Keybind beatKey()
	{
		return new Keybind(KeyEvent.VK_F, 0);
	}

	@ConfigItem(
		position = 13,
		keyName = "actionsRow",
		name = "Actions row",
		description = "What the combat view shows about your own actions",
		section = COMBAT
	)
	default ActionsRow actionsRow()
	{
		return ActionsRow.ATTACKS;
	}

	@ConfigItem(
		position = 14,
		keyName = "showEatCosts",
		name = "Show eat costs",
		description = "Show how many attack ticks each eat cost you",
		section = COMBAT
	)
	default boolean showEatCosts()
	{
		return true;
	}

	@ConfigItem(
		position = 15,
		keyName = "hullFlash",
		name = "Flash enemy on hit",
		description = "Briefly outline the enemy whose hitsplat just landed on you",
		section = COMBAT
	)
	default boolean hullFlash()
	{
		return true;
	}

	@ConfigItem(
		position = 16,
		keyName = "hullFlashAtBosses",
		name = "Also at bosses",
		description = "Also flash bosses",
		section = COMBAT
	)
	default boolean hullFlashAtBosses()
	{
		return false;
	}

	@ConfigItem(
		position = 17,
		keyName = "hullFlashMisses",
		name = "Misses",
		description = "Whether a 0 hit flashes the enemy faintly grey",
		section = COMBAT
	)
	default HullFlashMisses hullFlashMisses()
	{
		return HullFlashMisses.FAINT;
	}

	@ConfigItem(
		position = 18,
		keyName = "xpDrops",
		name = "XP drops",
		description = "Show XP drops as numbers, as dots only, or not at all",
		section = XP_GOLD
	)
	default XpDrops xpDrops()
	{
		return XpDrops.NUMBERS;
	}

	@ConfigItem(
		position = 19,
		keyName = "xpDots",
		name = "Frequent skills as dots",
		description = "Skills that drop XP on 5 of the last 10 ticks show as small dots instead of numbers",
		section = XP_GOLD
	)
	default boolean xpDots()
	{
		return true;
	}

	@ConfigItem(
		position = 20,
		keyName = "alchTopRow",
		name = "Alch top row",
		description = "What the top row shows for each high-alch cast: its coins, its Magic XP, or both",
		section = XP_GOLD
	)
	default AlchTopRow alchTopRow()
	{
		return AlchTopRow.GOLD;
	}

	@ConfigItem(
		position = 21,
		keyName = "rightSide",
		name = "Right side shows",
		description = "Whether the right readout's big number is the hourly rate or the session total",
		section = XP_GOLD
	)
	default RightSide rightSide()
	{
		return RightSide.PER_HOUR;
	}

	@ConfigItem(
		position = 22,
		keyName = "timeToFinish",
		name = "Time to finish",
		description = "Show the finish flag and the time left while an inventory is being made",
		section = SKILLING
	)
	default boolean timeToFinish()
	{
		return true;
	}

	@ConfigItem(
		position = 23,
		keyName = "wastedTicks",
		name = "Wasted ticks (+N)",
		description = "Show how many ticks passed between the last item and your bank click",
		section = SKILLING
	)
	default boolean wastedTicks()
	{
		return true;
	}

	@ConfigItem(
		position = 24,
		keyName = "lapCompare",
		name = "Agility: compare lap to",
		description = "Compare this lap's time with your best lap or your last lap",
		section = SKILLING
	)
	default LapCompare lapCompare()
	{
		return LapCompare.BEST;
	}

	@Alpha
	@ConfigItem(
		position = 25,
		keyName = "damageColour",
		name = "Damage",
		description = "Damage hits, coins and the enemy flash",
		section = COLOURS
	)
	default Color damageColour()
	{
		return new Color(0xffd23f);
	}

	@Alpha
	@ConfigItem(
		position = 26,
		keyName = "mintColour",
		name = "Good / done (mint)",
		description = "Done blinks, good results and the AFK countdown",
		section = COLOURS
	)
	default Color mintColour()
	{
		return new Color(0x5ee0c8);
	}

	@Alpha
	@ConfigItem(
		position = 27,
		keyName = "runEnergyColour",
		name = "Run energy",
		description = "Run energy gauge and running pills",
		section = COLOURS
	)
	default Color runEnergyColour()
	{
		return new Color(0xe8c86a);
	}

	@Alpha
	@ConfigItem(
		position = 28,
		keyName = "backgroundColour",
		name = "Bar background",
		description = "Bar background and its opacity",
		section = COLOURS
	)
	default Color backgroundColour()
	{
		return new Color(20, 23, 27, 235);
	}
}
