package com.superhistogram.model;

/**
 * Two clocks. Online time ("ONLINE") is this login: it survives world hops but resets on the login screen.
 * Session time is the basis for every per-hour rate: logged-in time summed across relogs, reset only by
 * {@link #resetSession} (a RuneScape profile change).
 */
public final class SessionClock
{
	/** Per-hour readouts show "—" until this much session time has accumulated. */
	public static final long MIN_RATE_MS = 60_000;

	private long loginMs = -1;
	/** Where the current login's share of session time starts (the login, or a later session reset). */
	private long countFromMs = -1;
	/** Session time from logins that have ended. */
	private long closedMs;

	public void onLoggedIn(long nowMs)
	{
		if (loginMs < 0)
		{
			loginMs = nowMs;
			countFromMs = nowMs;
		}
	}

	public void onHopping()
	{
		// Intentionally keeps loginMs.
	}

	public void onLoginScreen(long nowMs)
	{
		if (loginMs >= 0)
		{
			closedMs += Math.max(0, nowMs - countFromMs);
		}
		loginMs = -1;
		countFromMs = -1;
	}

	/** Starts session time again from nowMs (a new account); the login time is kept. */
	public void resetSession(long nowMs)
	{
		closedMs = 0;
		if (loginMs >= 0)
		{
			countFromMs = nowMs;
		}
	}

	public long onlineMs(long nowMs)
	{
		return loginMs < 0 ? 0 : Math.max(0, nowMs - loginMs);
	}

	/** Logged-in time since the session began, summed across relogs: the divisor of every per-hour rate. */
	public long sessionMs(long nowMs)
	{
		return closedMs + (countFromMs < 0 ? 0 : Math.max(0, nowMs - countFromMs));
	}

	/** True once a minute of session time has accumulated; before that every /hr readout shows "—". */
	public boolean ratesReady(long nowMs)
	{
		return sessionMs(nowMs) >= MIN_RATE_MS;
	}

	public String format(long nowMs)
	{
		long s = onlineMs(nowMs) / 1000;
		return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}
}
