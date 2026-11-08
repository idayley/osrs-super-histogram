package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

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
		c.onLoginScreen();
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
}
