package com.superhistogram.model;

import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.model.skill.LapCompare;

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
	public final boolean showRunEnergy, showLoginTime;
	public final Mode mode;
	/** Wall-clock time the snapshot was taken, for time-based readouts. */
	public final long nowMs;
	/** Lazy-flick metronome; null when none. */
	public final Beat beat;
	/** 1-tick flick classifier; null when none. */
	public final FlickClassifier flicks;
	public final int weaponSpeed;
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
	public final LapCompare lapCompare;

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
		showRunEnergy = b.showRunEnergy;
		showLoginTime = b.showLoginTime;
		mode = b.mode;
		nowMs = b.nowMs;
		beat = b.beat;
		flicks = b.flicks;
		weaponSpeed = b.weaponSpeed;
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
		lapCompare = b.lapCompare;
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
		private boolean showRunEnergy = true, showLoginTime = true;
		private Mode mode = Mode.GENERAL;
		private long nowMs;
		private Beat beat;
		private FlickClassifier flicks;
		private int weaponSpeed = 4;
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
		private LapCompare lapCompare = LapCompare.BEST;

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

		public Builder showRunEnergy(boolean v)
		{
			showRunEnergy = v;
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

		/** A null keeps the default (BEST). */
		public Builder lapCompare(LapCompare v)
		{
			if (v != null)
			{
				lapCompare = v;
			}
			return this;
		}

		public BarState build()
		{
			return new BarState(this);
		}
	}
}
