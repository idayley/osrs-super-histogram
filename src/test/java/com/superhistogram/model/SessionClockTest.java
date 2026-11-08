package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionClockTest
{
	@Test
	public void hopKeepsLoginTime()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		c.onHopping();
		c.onLoggedIn(5000);
		assertEquals(10000, c.onlineMs(10000));
	}

	@Test
	public void loginScreenResets()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		c.onLoginScreen(10000);
		c.onLoggedIn(20000);
		assertEquals(1000, c.onlineMs(21000));
	}

	@Test
	public void formatsHms()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		assertEquals("2:14:07", c.format(8_047_000));
	}

	@Test
	public void zeroBeforeLogin()
	{
		assertEquals(0, new SessionClock().onlineMs(5000));
	}

	@Test
	public void sessionTimeSumsLoginsAcrossRelogs()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		c.onLoginScreen(7_200_000);
		assertEquals(7_200_000, c.sessionMs(7_250_000));
		c.onLoggedIn(7_300_000);
		assertEquals(7_260_000, c.sessionMs(7_360_000));
		// The ONLINE clock still means this login.
		assertEquals(60_000, c.onlineMs(7_360_000));
	}

	@Test
	public void hopCountsSessionTimeOnce()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		c.onHopping();
		c.onLoggedIn(5000);
		assertEquals(10000, c.sessionMs(10000));
	}

	@Test
	public void resetSessionRestartsSessionTimeButNotLoginTime()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		c.onLoginScreen(1000);
		c.onLoggedIn(2000);
		c.resetSession(2500);
		assertEquals(1000, c.sessionMs(3500));
		assertEquals(1500, c.onlineMs(3500));
		SessionClock out = new SessionClock();
		out.resetSession(100);
		assertEquals(0, out.sessionMs(5000));
	}

	@Test
	public void ratesNeedAMinuteOfSessionTime()
	{
		SessionClock c = new SessionClock();
		c.onLoggedIn(0);
		assertFalse(c.ratesReady(59_999));
		assertTrue(c.ratesReady(60_000));
		SessionClock relog = new SessionClock();
		relog.onLoggedIn(0);
		relog.onLoginScreen(40_000);
		relog.onLoggedIn(100_000);
		assertFalse(relog.ratesReady(119_999));
		assertTrue(relog.ratesReady(120_000));
		assertFalse(new SessionClock().ratesReady(1_000_000));
	}
}
