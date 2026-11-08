package com.superhistogram;

import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import com.superhistogram.model.BaselineSetting;
import com.superhistogram.model.ModeSetting;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.skill.AlchTopRow;
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
import net.runelite.client.config.Units;

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
		OFF("Off"),
		EVERY_TICK("Every tick"),
		/** A bright pulse on your beat (Beat hotkey and Beat every). */
		CUE_TICK("On beat");

		private final String label;

		Metronome(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum BeatRuns
	{
		IN_COMBAT("Only in combat"),
		ALWAYS("Always");

		private final String label;

		BeatRuns(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum PingShift
	{
		AUTO("Auto (measured)"),
		FIXED("Fixed"),
		OFF("Off");

		private final String label;

		PingShift(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum MetronomeStyle
	{
		/** The outline flashes white on each beat and fades. */
		FLASH,
		/** The outline stays lit and swaps between two colours on each beat (tick, tock). */
		ALTERNATE
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

	@Range(min = 22, max = 160)
	@Units(Units.PIXELS)
	@ConfigItem(
		position = 2,
		keyName = "barHeight",
		name = "Height",
		description = "Bar height in pixels; text, pills and icons all scale with it. 62 is normal, 40 small, 80 large."
			+ " Resizing by hand wins until you change this again",
		section = BAR
	)
	default int barHeight()
	{
		return BarSize.NORMAL.height();
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

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		position = 7,
		keyName = "nowLinePercent",
		name = "Now line position",
		description = "How far across the timeline the current-tick line sits: 50 is the middle, 100 the right edge."
			+ " Room to its right shows what's coming (like a cooking run's finish)",
		section = BAR
	)
	default int nowLinePercent()
	{
		return 80;
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
		keyName = "smoothScroll",
		name = "Smooth scrolling",
		description = "Glide the timeline continuously. Off: it steps once per game tick",
		section = BAR
	)
	default boolean smoothScroll()
	{
		return true;
	}

	@ConfigItem(
		position = 10,
		keyName = "pingShift",
		name = "Shift clicks by ping",
		description = "A click made just before a tick ends reaches the server on the next tick. Auto measures your"
			+ " ping to the world and moves such clicks to the tick the server got them (a faint mark stays where"
			+ " you clicked). Fixed uses the delay below",
		section = BAR
	)
	default PingShift pingShift()
	{
		return PingShift.AUTO;
	}

	@Range(min = 0, max = 500)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		position = 11,
		keyName = "pingFixedMs",
		name = "Fixed ping",
		description = "The delay used when Shift clicks by ping is Fixed",
		section = BAR
	)
	default int pingFixedMs()
	{
		return 50;
	}

	@ConfigItem(
		position = 12,
		keyName = "metronome",
		name = "Metronome",
		description = "Pulse the bar outline on every game tick, or only on your beat (set with the Beat hotkey)",
		section = FEEDBACK
	)
	default Metronome metronome()
	{
		return Metronome.OFF;
	}

	@ConfigItem(
		position = 13,
		keyName = "metronomeStyle",
		name = "Metronome style",
		description = "Flash: the outline flashes on each beat. Alternate: the outline stays lit and swaps colour"
			+ " each beat, so you can tell beats apart without a flash",
		section = FEEDBACK
	)
	default MetronomeStyle metronomeStyle()
	{
		return MetronomeStyle.FLASH;
	}

	@ConfigItem(
		position = 14,
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
		position = 15,
		keyName = "prayerMode",
		name = "Prayer mode",
		description = "How you protect in combat",
		section = COMBAT
	)
	default PrayerMode prayerMode()
	{
		return PrayerMode.LAZY_FLICK;
	}

	@Range(min = 0, max = 300)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		position = 16,
		keyName = "clickWindowMs",
		name = "Click window",
		description = "1-tick flick: a faint band either side of each tick line showing where a flick lands clean."
			+ " 0 hides it",
		section = COMBAT
	)
	default int clickWindowMs()
	{
		return 80;
	}

	@Range(min = 0, max = 60)
	@ConfigItem(
		position = 17,
		keyName = "combatHoldSeconds",
		name = "Stay in combat view for",
		description = "Seconds the combat view stays up after your last attack or hit, before switching back",
		section = COMBAT
	)
	default int combatHoldSeconds()
	{
		return 10;
	}

	@ConfigItem(
		position = 18,
		keyName = "beatKey",
		name = "Beat hotkey",
		description = "Press in a fight on the tick you want to track (say, when the enemy attacks): beat triangles"
			+ " then repeat every 'Beat every' ticks",
		section = COMBAT
	)
	default Keybind beatKey()
	{
		return new Keybind(KeyEvent.VK_F, 0);
	}

	@Range(min = 1, max = 20)
	@Units(Units.TICKS)
	@ConfigItem(
		position = 19,
		keyName = "beatTicks",
		name = "Beat every",
		description = "Ticks between your beat triangles, e.g. the enemy's attack speed",
		section = COMBAT
	)
	default int beatTicks()
	{
		return 4;
	}

	@ConfigItem(
		position = 20,
		keyName = "beatRuns",
		name = "Beat runs",
		description = "Only in combat: the hotkey works in a fight and the beat stops when it ends."
			+ " Always: the hotkey works anywhere and the beat keeps going (press again to line it up anew)",
		section = COMBAT
	)
	default BeatRuns beatRuns()
	{
		return BeatRuns.IN_COMBAT;
	}

	@ConfigItem(
		position = 21,
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
		position = 22,
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
		position = 23,
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
		position = 24,
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
		position = 25,
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
		position = 26,
		keyName = "ignoreEnemies",
		name = "Ignore enemies",
		description = "Monsters the bar ignores: no hits, no flash, never the fight. Names separated by commas,"
			+ " * matches anything (Giant rat, *imp*). Shift-right-click a monster to add or remove it",
		section = COMBAT
	)
	default String ignoreEnemies()
	{
		return "";
	}

	@ConfigItem(
		position = 27,
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
		position = 28,
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
		position = 29,
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
		position = 30,
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
		position = 31,
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
		position = 32,
		keyName = "wastedTicks",
		name = "Wasted ticks (+N)",
		description = "Show how many ticks passed between the last item and your bank click",
		section = SKILLING
	)
	default boolean wastedTicks()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		position = 33,
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
		position = 34,
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
		position = 35,
		keyName = "runEnergyColour",
		name = "Run energy",
		description = "Colour of the running pills",
		section = COLOURS
	)
	default Color runEnergyColour()
	{
		return new Color(0xe8c86a);
	}

	@Alpha
	@ConfigItem(
		position = 36,
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
