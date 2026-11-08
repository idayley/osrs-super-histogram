package com.superhistogram.model;

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

		public BarState build()
		{
			return new BarState(this);
		}
	}
}
