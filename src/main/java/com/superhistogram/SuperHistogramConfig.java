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
import java.util.EnumSet;
import java.util.Set;
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

	/** Activities the metronome may run in. */
	enum MetronomeActivity
	{
		COMBAT("Combat"),
		AGILITY("Agility"),
		MAGIC("High alch and spells"),
		GATHERING("Gathering"),
		PRODUCTION("Production"),
		THIEVING("Thieving"),
		HUNTER("Hunter"),
		RUNS("Runecraft and runs"),
		MINIGAMES("Minigames"),
		OTHER("Walking and idle");

		private final String label;

		MetronomeActivity(String label)
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

	@ConfigSection(
		name = "Prayer colours",
		description = "Prayer colours",
		position = 6,
		closedByDefault = true
	)
	String PRAYER_COLOURS = "prayerColours";

	@ConfigSection(
		name = "Combat colours",
		description = "Combat colours",
		position = 7,
		closedByDefault = true
	)
	String COMBAT_COLOURS = "combatColours";

	@ConfigSection(
		name = "Metronome colours",
		description = "Metronome colours",
		position = 8,
		closedByDefault = true
	)
	String METRONOME_COLOURS = "metronomeColours";

	@ConfigSection(
		name = "Bar line colours",
		description = "Bar line colours",
		position = 9,
		closedByDefault = true
	)
	String LINE_COLOURS = "lineColours";

	@ConfigSection(
		name = "Skill colours",
		description = "Skill colours",
		position = 10,
		closedByDefault = true
	)
	String SKILL_COLOURS = "skillColours";

	@ConfigItem(
		position = 1,
		keyName = "position",
		name = "Position",
		description = "Replace chat: over the chat area. Above chat: just above it. Floating: drag it anywhere",
		section = BAR
	)
	default BarPosition position()
	{
		return BarPosition.FLOATING;
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
		position = 11,
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
		position = 12,
		keyName = "metronomeIn",
		name = "Metronome in",
		description = "The activities the metronome pulses in; untick one to keep the outline still there",
		section = FEEDBACK
	)
	default Set<MetronomeActivity> metronomeIn()
	{
		return EnumSet.allOf(MetronomeActivity.class);
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

	@Alpha
	@ConfigItem(
		position = 37,
		keyName = "prayerMeleeColour",
		name = "Protect from Melee",
		description = "Pills for melee prayers",
		section = PRAYER_COLOURS
	)
	default Color prayerMeleeColour()
	{
		return new Color(0xd0313a);
	}

	@Alpha
	@ConfigItem(
		position = 38,
		keyName = "prayerRangedColour",
		name = "Protect from Missiles",
		description = "Pills for ranged prayers",
		section = PRAYER_COLOURS
	)
	default Color prayerRangedColour()
	{
		return new Color(0x64ab31);
	}

	@Alpha
	@ConfigItem(
		position = 39,
		keyName = "prayerMagicColour",
		name = "Protect from Magic",
		description = "Pills for magic prayers, and spell casting",
		section = PRAYER_COLOURS
	)
	default Color prayerMagicColour()
	{
		return new Color(0x1499dc);
	}

	@Alpha
	@ConfigItem(
		position = 40,
		keyName = "prayerOtherColour",
		name = "Other prayers",
		description = "Pills for every other prayer",
		section = PRAYER_COLOURS
	)
	default Color prayerOtherColour()
	{
		return new Color(0x9150d0);
	}

	@Alpha
	@ConfigItem(
		position = 41,
		keyName = "fightColour",
		name = "Fight pill",
		description = "The pill while you are in a fight",
		section = COMBAT_COLOURS
	)
	default Color fightColour()
	{
		return new Color(0x6b1f2c);
	}

	@Alpha
	@ConfigItem(
		position = 42,
		keyName = "missFlashColour",
		name = "Misses and blocks",
		description = "Hits blocked or missed, and the enemy flash on a miss",
		section = COMBAT_COLOURS
	)
	default Color missFlashColour()
	{
		return new Color(0x6cc4ff);
	}

	@Alpha
	@ConfigItem(
		position = 43,
		keyName = "rimTickColour",
		name = "Tick",
		description = "The metronome's first beat colour",
		section = METRONOME_COLOURS
	)
	default Color rimTickColour()
	{
		return new Color(0xf0b24a);
	}

	@Alpha
	@ConfigItem(
		position = 44,
		keyName = "rimTockColour",
		name = "Tock",
		description = "The metronome's second beat colour (alternate style)",
		section = METRONOME_COLOURS
	)
	default Color rimTockColour()
	{
		return new Color(0xb48cff);
	}

	@Alpha
	@ConfigItem(
		position = 45,
		keyName = "nowLineColour",
		name = "Now line",
		description = "The line at the current tick",
		section = LINE_COLOURS
	)
	default Color nowLineColour()
	{
		return new Color(233, 236, 239, 128);
	}

	@Alpha
	@ConfigItem(
		position = 46,
		keyName = "actionColour",
		name = "Action pill",
		description = "Pills for actions the bar has no colour for",
		section = LINE_COLOURS
	)
	default Color actionColour()
	{
		return new Color(0x9fb4d9);
	}

	@Alpha
	@ConfigItem(
		position = 47,
		keyName = "lostColour",
		name = "Lost ticks",
		description = "Lost-tick labels and food icons",
		section = LINE_COLOURS
	)
	default Color lostColour()
	{
		return new Color(0xc9ced4);
	}

	@ConfigItem(
		position = 48,
		keyName = "attackColour",
		name = "Attack",
		description = "Attack pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color attackColour()
	{
		return new Color(0xe08a7a);
	}

	@ConfigItem(
		position = 49,
		keyName = "strengthColour",
		name = "Strength",
		description = "Strength pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color strengthColour()
	{
		return new Color(0x8fd694);
	}

	@ConfigItem(
		position = 50,
		keyName = "defenceColour",
		name = "Defence",
		description = "Defence pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color defenceColour()
	{
		return new Color(0x9fb4d9);
	}

	@ConfigItem(
		position = 51,
		keyName = "rangedColour",
		name = "Ranged",
		description = "Ranged pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color rangedColour()
	{
		return new Color(0xb9e08a);
	}

	@ConfigItem(
		position = 52,
		keyName = "prayerColour",
		name = "Prayer",
		description = "Prayer pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color prayerColour()
	{
		return new Color(0xf4e7a6);
	}

	@ConfigItem(
		position = 53,
		keyName = "magicColour",
		name = "Magic",
		description = "Magic pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color magicColour()
	{
		return new Color(0x8fc3ff);
	}

	@ConfigItem(
		position = 54,
		keyName = "runecraftColour",
		name = "Runecraft",
		description = "Runecraft pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color runecraftColour()
	{
		return new Color(0xf5a98f);
	}

	@ConfigItem(
		position = 55,
		keyName = "constructionColour",
		name = "Construction",
		description = "Construction pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color constructionColour()
	{
		return new Color(0xd8c3a0);
	}

	@ConfigItem(
		position = 56,
		keyName = "hitpointsColour",
		name = "Hitpoints",
		description = "Hitpoints pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color hitpointsColour()
	{
		return new Color(0xf2a7bd);
	}

	@ConfigItem(
		position = 57,
		keyName = "agilityColour",
		name = "Agility",
		description = "Agility pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color agilityColour()
	{
		return new Color(0x7fa7c9);
	}

	@ConfigItem(
		position = 58,
		keyName = "herbloreColour",
		name = "Herblore",
		description = "Herblore pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color herbloreColour()
	{
		return new Color(0xa6dba0);
	}

	@ConfigItem(
		position = 59,
		keyName = "thievingColour",
		name = "Thieving",
		description = "Thieving pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color thievingColour()
	{
		return new Color(0xc58fd6);
	}

	@ConfigItem(
		position = 60,
		keyName = "craftingColour",
		name = "Crafting",
		description = "Crafting pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color craftingColour()
	{
		return new Color(0xe6a8c8);
	}

	@ConfigItem(
		position = 61,
		keyName = "fletchingColour",
		name = "Fletching",
		description = "Fletching pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color fletchingColour()
	{
		return new Color(0x7fd6c2);
	}

	@ConfigItem(
		position = 62,
		keyName = "slayerColour",
		name = "Slayer",
		description = "Slayer pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color slayerColour()
	{
		return new Color(0xc7a3ff);
	}

	@ConfigItem(
		position = 63,
		keyName = "hunterColour",
		name = "Hunter",
		description = "Hunter pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color hunterColour()
	{
		return new Color(0xd9c98c);
	}

	@ConfigItem(
		position = 64,
		keyName = "miningColour",
		name = "Mining",
		description = "Mining pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color miningColour()
	{
		return new Color(0xb4bfcc);
	}

	@ConfigItem(
		position = 65,
		keyName = "smithingColour",
		name = "Smithing",
		description = "Smithing pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color smithingColour()
	{
		return new Color(0xc9b29a);
	}

	@ConfigItem(
		position = 66,
		keyName = "fishingColour",
		name = "Fishing",
		description = "Fishing pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color fishingColour()
	{
		return new Color(0x8fd0e8);
	}

	@ConfigItem(
		position = 67,
		keyName = "cookingColour",
		name = "Cooking",
		description = "Cooking pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color cookingColour()
	{
		return new Color(0xf0b97a);
	}

	@ConfigItem(
		position = 68,
		keyName = "firemakingColour",
		name = "Firemaking",
		description = "Firemaking pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color firemakingColour()
	{
		return new Color(0xffb380);
	}

	@ConfigItem(
		position = 69,
		keyName = "woodcuttingColour",
		name = "Woodcutting",
		description = "Woodcutting pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color woodcuttingColour()
	{
		return new Color(0xc4d98c);
	}

	@ConfigItem(
		position = 70,
		keyName = "farmingColour",
		name = "Farming",
		description = "Farming pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color farmingColour()
	{
		return new Color(0x9ee0b4);
	}

	@ConfigItem(
		position = 71,
		keyName = "sailingColour",
		name = "Sailing",
		description = "Sailing pills, XP drops and icons",
		section = SKILL_COLOURS
	)
	default Color sailingColour()
	{
		return new Color(0xa3d3f0);
	}
}
