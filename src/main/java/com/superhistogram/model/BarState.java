package com.superhistogram.model;

import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.ThieveRun;
import com.superhistogram.model.skill.TrapTracker;
import com.superhistogram.model.skill.SpellRun;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.model.skill.InventoryRun;

/**
 * Read-only snapshot of everything the painters need for one frame. Any
 * reference may be null (logged out, not yet initialised); painters skip
 * what is missing.
 */
public final class BarState
{
	public final Timeline timeline;
	/** Newest game tick; -1 before the first tick. */
	public final int nowTick;
	public final XpTracker xp;
	public final IdleTracker idle;
	public final SessionClock clock;
	public final int runEnergyPct;
	public final String modeName, modeSub;
	public final boolean showLoginTime;
	public final Mode mode;
	/** Wall-clock time the snapshot was taken, for time-based readouts. */
	public final long nowMs;
	/** Wall-clock time the newest tick arrived; 0 when unknown. */
	public final long tickMs;
	/** Glide the timeline between ticks instead of stepping once per tick. */
	public final boolean smooth;
	/** Where the "now" line sits across the timeline, 10-100 %; 0 = the default spot. */
	public final int nowLinePct;
	/** Lazy-flick metronome; null when none. */
	public final Beat beat;
	/** 1-tick flick classifier; null when none. */
	public final FlickClassifier flicks;
	public final int weaponSpeed;
	/** The tick your weapon is ready to attack again (in a fight, ahead of now); -1 when none. */
	public final int nextAttackTick;
	/** The next-attack tick comes from your swing rhythm, not a known weapon speed. */
	public final boolean nextAttackLearned;
	/** 1-tick flick: half-width of the clean-click band either side of each tick line, in ms; 0 hides it. */
	public final int clickWindowMs;
	/** Coins alched today (kept across logins); -1 when unknown. */
	public final long alchToday;
	public final ActionsRow actionsRow;
	public final boolean showEatCosts;
	/** Production run; null when none. */
	public final InventoryRun run;
	public final boolean timeToFinish, wastedTicks;
	public final XpDrops xpDrops;
	/** Frequent skills (5+ drop ticks in the last 10) draw as dots. */
	public final boolean xpDots;
	/** High-alch tracker; null when none. */
	public final AlchTracker alch;
	public final AlchTopRow alchTopRow;
	/** Gathering run; null when none. */
	public final GatherRun gather;
	public final RightSide rightSide;
	/** Agility course; null when none. */
	public final AgilityCourse agility;
	/** Pickpocketing or stall thieving; null when none. */
	public final ThieveRun thieve;
	/** The loop on show (Runecraft, farm run, bird houses); null when none. */
	public final com.superhistogram.model.skill.LoopRun loop;
	/** Minigame rounds; null before startUp. */
	public final com.superhistogram.model.skill.RoundTracker rounds;
	/** Your slayer task; null before startUp. */
	public final com.superhistogram.model.skill.SlayerTask slayer;
	/** Hunter traps; null when none. */
	public final TrapTracker hunt;
	/** A skilling spell other than high alch; null when none. */
	public final SpellRun spell;

	private BarState(Builder b)
	{
		timeline = b.timeline;
		nowTick = b.nowTick;
		xp = b.xp;
		idle = b.idle;
		clock = b.clock;
		runEnergyPct = b.runEnergyPct;
		modeName = b.modeName;
		modeSub = b.modeSub;
		showLoginTime = b.showLoginTime;
		mode = b.mode;
		nowMs = b.nowMs;
		tickMs = b.tickMs;
		smooth = b.smooth;
		nowLinePct = b.nowLinePct;
		beat = b.beat;
		flicks = b.flicks;
		weaponSpeed = b.weaponSpeed;
		nextAttackTick = b.nextAttackTick;
		nextAttackLearned = b.nextAttackLearned;
		clickWindowMs = b.clickWindowMs;
		alchToday = b.alchToday;
		actionsRow = b.actionsRow;
		showEatCosts = b.showEatCosts;
		run = b.run;
		timeToFinish = b.timeToFinish;
		wastedTicks = b.wastedTicks;
		xpDrops = b.xpDrops;
		xpDots = b.xpDots;
		alch = b.alch;
		alchTopRow = b.alchTopRow;
		gather = b.gather;
		rightSide = b.rightSide;
		agility = b.agility;
		thieve = b.thieve;
		loop = b.loop;
		rounds = b.rounds;
		slayer = b.slayer;
		hunt = b.hunt;
		spell = b.spell;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public static final class Builder
	{
		private Timeline timeline;
		private int nowTick = -1;
		private XpTracker xp;
		private IdleTracker idle;
		private SessionClock clock;
		private int runEnergyPct;
		private String modeName, modeSub;
		private boolean showLoginTime = true;
		private Mode mode = Mode.GENERAL;
		private long nowMs, tickMs;
		private boolean smooth;
		private int nowLinePct;
		private Beat beat;
		private FlickClassifier flicks;
		private int weaponSpeed = 4;
		private int nextAttackTick = -1;
		private boolean nextAttackLearned;
		private int clickWindowMs;
		private long alchToday = -1;
		private ActionsRow actionsRow = ActionsRow.ATTACKS;
		private boolean showEatCosts = true;
		private InventoryRun run;
		private boolean timeToFinish = true, wastedTicks = true;
		private XpDrops xpDrops = XpDrops.NUMBERS;
		private boolean xpDots = true;
		private AlchTracker alch;
		private AlchTopRow alchTopRow = AlchTopRow.GOLD;
		private GatherRun gather;
		private RightSide rightSide = RightSide.PER_HOUR;
		private AgilityCourse agility;
		private ThieveRun thieve;
		private com.superhistogram.model.skill.LoopRun loop;
		private com.superhistogram.model.skill.RoundTracker rounds;
		private com.superhistogram.model.skill.SlayerTask slayer;
		private TrapTracker hunt;
		private SpellRun spell;

		public Builder timeline(Timeline v)
		{
			timeline = v;
			return this;
		}

		public Builder nowTick(int v)
		{
			nowTick = v;
			return this;
		}

		public Builder xp(XpTracker v)
		{
			xp = v;
			return this;
		}

		public Builder idle(IdleTracker v)
		{
			idle = v;
			return this;
		}

		public Builder clock(SessionClock v)
		{
			clock = v;
			return this;
		}

		public Builder runEnergyPct(int v)
		{
			runEnergyPct = v;
			return this;
		}

		public Builder modeName(String v)
		{
			modeName = v;
			return this;
		}

		public Builder modeSub(String v)
		{
			modeSub = v;
			return this;
		}


		public Builder showLoginTime(boolean v)
		{
			showLoginTime = v;
			return this;
		}

		public Builder mode(Mode v)
		{
			mode = v;
			return this;
		}

		public Builder nowMs(long v)
		{
			nowMs = v;
			return this;
		}

		public Builder tickMs(long v)
		{
			tickMs = v;
			return this;
		}

		public Builder smooth(boolean v)
		{
			smooth = v;
			return this;
		}

		public Builder nextAttackTick(int v)
		{
			nextAttackTick = v;
			return this;
		}

		public Builder nextAttackLearned(boolean v)
		{
			nextAttackLearned = v;
			return this;
		}

		public Builder alchToday(long v)
		{
			alchToday = v;
			return this;
		}

		public Builder clickWindowMs(int v)
		{
			clickWindowMs = v;
			return this;
		}

		public Builder nowLinePct(int v)
		{
			nowLinePct = v;
			return this;
		}

		public Builder beat(Beat v)
		{
			beat = v;
			return this;
		}

		public Builder flicks(FlickClassifier v)
		{
			flicks = v;
			return this;
		}

		public Builder weaponSpeed(int v)
		{
			weaponSpeed = v;
			return this;
		}

		public Builder actionsRow(ActionsRow v)
		{
			actionsRow = v;
			return this;
		}

		public Builder showEatCosts(boolean v)
		{
			showEatCosts = v;
			return this;
		}

		public Builder run(InventoryRun v)
		{
			run = v;
			return this;
		}

		public Builder timeToFinish(boolean v)
		{
			timeToFinish = v;
			return this;
		}

		public Builder wastedTicks(boolean v)
		{
			wastedTicks = v;
			return this;
		}

		public Builder xpDrops(XpDrops v)
		{
			xpDrops = v;
			return this;
		}

		public Builder xpDots(boolean v)
		{
			xpDots = v;
			return this;
		}

		public Builder alch(AlchTracker v)
		{
			alch = v;
			return this;
		}

		public Builder gather(GatherRun v)
		{
			gather = v;
			return this;
		}

		/** A null keeps the default (GOLD). */
		public Builder alchTopRow(AlchTopRow v)
		{
			if (v != null)
			{
				alchTopRow = v;
			}
			return this;
		}

		/** A null keeps the default (PER_HOUR). */
		public Builder rightSide(RightSide v)
		{
			if (v != null)
			{
				rightSide = v;
			}
			return this;
		}

		public Builder agility(AgilityCourse v)
		{
			agility = v;
			return this;
		}

		public Builder slayer(com.superhistogram.model.skill.SlayerTask v)
		{
			slayer = v;
			return this;
		}

		public Builder rounds(com.superhistogram.model.skill.RoundTracker v)
		{
			rounds = v;
			return this;
		}

		public Builder loop(com.superhistogram.model.skill.LoopRun v)
		{
			loop = v;
			return this;
		}

		public Builder thieve(ThieveRun v)
		{
			thieve = v;
			return this;
		}

		public Builder hunt(TrapTracker v)
		{
			hunt = v;
			return this;
		}

		public Builder spell(SpellRun v)
		{
			spell = v;
			return this;
		}

		/** A null keeps the default (BEST). */
		public BarState build()
		{
			return new BarState(this);
		}
	}
}
