package com.superhistogram.model;

/** Tracks idle seconds against the AFK threshold and the 300 s logout. */
public final class IdleTracker
{
	private static final int LOGOUT_SECONDS = 300;
	private static final int WARNING_SECONDS = 270;

	private int afkAfterSeconds;
	private int idle;
	private boolean warning;

	public IdleTracker(int afkAfterSeconds)
	{
		this.afkAfterSeconds = afkAfterSeconds;
	}

	/** Changes the AFK threshold without touching idle/warning state. */
	public void setAfkAfterSeconds(int seconds)
	{
		afkAfterSeconds = seconds;
	}

	public void update(int idleSeconds, long nowMs)
	{
		warning = idle < WARNING_SECONDS && idleSeconds >= WARNING_SECONDS;
		idle = idleSeconds;
	}

	public boolean isAfk()
	{
		return idle >= afkAfterSeconds;
	}

	public int secondsToLogout()
	{
		return Math.max(0, LOGOUT_SECONDS - idle);
	}

	/** True only for the update that crossed 270 s idle. */
	public boolean warningFired()
	{
		return warning;
	}
}
