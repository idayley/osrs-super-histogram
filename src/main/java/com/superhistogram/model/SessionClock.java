package com.superhistogram.model;

/** Online time that survives world hops but resets on the login screen. */
public final class SessionClock
{
	private long loginMs = -1;

	public void onLoggedIn(long nowMs)
	{
		if (loginMs < 0)
		{
			loginMs = nowMs;
		}
	}

	public void onHopping()
	{
		// Intentionally keeps loginMs.
	}

	public void onLoginScreen()
	{
		loginMs = -1;
	}

	public long onlineMs(long nowMs)
	{
		return loginMs < 0 ? 0 : Math.max(0, nowMs - loginMs);
	}

	public String format(long nowMs)
	{
		long s = onlineMs(nowMs) / 1000;
		return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}
}
