package com.superhistogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.BarPainter;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

public class SuperHistogramPluginHandlersTest
{
	private Client client;
	private Player me;
	private SuperHistogramConfig config;
	private SuperHistogramPlugin plugin;
	private ItemManager itemManager;
	private NPC npc;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		me = mock(Player.class);
		config = mock(SuperHistogramConfig.class);
		when(config.afkAfterSeconds()).thenReturn(60);
		when(config.baseline()).thenReturn(com.superhistogram.model.BaselineSetting.GENERAL);
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.OFF);
		when(config.showRunEnergy()).thenReturn(true);
		when(config.showLoginTime()).thenReturn(true);
		when(config.afkWarning()).thenReturn(true);
		when(config.blinkWhenDone()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(me);
		when(client.getEnergy()).thenReturn(7100);
		when(me.getAnimation()).thenReturn(-1);
		plugin = new SuperHistogramPlugin();
		plugin.client = client;
		itemManager = mock(ItemManager.class);
		npc = mock(NPC.class);
		plugin.itemManager = itemManager;
		plugin.config = config;
		plugin.initModel();
		plugin.handleGameState(GameState.LOGGED_IN, 0);
	}

	private void at(int x, int y)
	{
		when(me.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
	}

	@Test
	public void hitsplatOnLocalPlayerBecomesHit()
	{
		plugin.handleTick(1000);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 4);
		TickRecord r = plugin.buildState().timeline.peek(0);
		assertEquals(1, r.hits.size());
		assertEquals(4, r.hits.get(0).amount);
		assertEquals(false, r.hits.get(0).blocked);
	}

	@Test
	public void zeroHitsplatIsBlocked()
	{
		plugin.handleTick(1000);
		plugin.handleHit(me, HitsplatID.BLOCK_ME, 0);
		assertTrue(plugin.buildState().timeline.peek(0).hits.get(0).blocked);
	}

	@Test
	public void hitsplatOnOtherActorIsIgnored()
	{
		plugin.handleTick(1000);
		plugin.handleHit(mock(NPC.class), HitsplatID.DAMAGE_ME, 9);
		plugin.handleHit(null, HitsplatID.DAMAGE_ME, 9);
		assertTrue(plugin.buildState().timeline.peek(0).hits.isEmpty());
	}

	@Test
	public void tickDerivesWalkRunAndEnergy()
	{
		at(100, 100);
		plugin.handleTick(1000);
		at(101, 100);
		plugin.handleTick(1600);
		at(103, 100);
		plugin.handleTick(2200);
		BarState s = plugin.buildState();
		assertEquals(71, s.runEnergyPct);
		List<Span> spans = s.timeline.spansBetween(0, 10);
		assertEquals("walk", spans.get(0).kind);
		assertEquals("run", spans.get(1).kind);
	}

	@Test
	public void teleportIsNotRunning()
	{
		at(100, 100);
		plugin.handleTick(1000);
		at(300, 400);
		plugin.handleTick(1600);
		assertTrue(plugin.buildState().timeline.spansBetween(0, 10).isEmpty());
	}

	@Test
	public void gameStateTransitionsKeepOrResetClock()
	{
		plugin.handleGameState(GameState.HOPPING, 5_000);
		plugin.handleGameState(GameState.LOGGED_IN, 6_000);
		assertEquals(10_000, plugin.clock.onlineMs(10_000));
		plugin.handleGameState(GameState.LOGIN_SCREEN, 11_000);
		assertEquals(0, plugin.clock.onlineMs(12_000));
	}

	@Test
	public void hopAndLogoutCloseOpenMovementSpan()
	{
		at(100, 100);
		plugin.handleTick(0);
		at(101, 100);
		plugin.handleTick(600);
		at(102, 100);
		plugin.handleTick(1200);
		plugin.handleGameState(GameState.HOPPING, 1300);
		assertEquals(2, plugin.timeline.spansBetween(0, 10).get(0).endTick);
		at(110, 100);
		plugin.handleTick(1800);
		at(111, 100);
		plugin.handleTick(2400);
		List<Span> before = plugin.timeline.spansBetween(0, 10);
		plugin.handleGameState(GameState.LOGIN_SCREEN, 2500);
		assertEquals(2, before.size());
		assertEquals(4, before.get(1).endTick);
	}

	@Test
	public void firstStatsAfterLoginAreBaselineNotDrops()
	{
		plugin.handleTick(0);
		plugin.handleStat("Attack", 1000);
		plugin.handleStat("Attack", 1100);
		assertEquals(100, plugin.buildState().xp.sessionXp());
		plugin.handleGameState(GameState.LOGIN_SCREEN, 10);
		plugin.handleGameState(GameState.LOGGED_IN, 20);
		plugin.handleStat("Attack", 5000);
		assertEquals(100, plugin.buildState().xp.sessionXp());
	}

	@Test
	public void clickIsRecordedOnCurrentTick()
	{
		plugin.handleTick(1000);
		plugin.handleClick(1300);
		assertEquals(1, plugin.buildState().timeline.peek(0).clicks.size());
	}

	@Test
	public void idleBeyondThresholdSwitchesToAfk()
	{
		when(client.getMouseIdleTicks()).thenReturn(50 * 90);
		when(client.getKeyboardIdleTicks()).thenReturn(50 * 120);
		plugin.handleTick(1000);
		assertEquals(Mode.AFK, plugin.buildState().mode);
	}

	@Test
	public void afkWarningTriggersMintBlinkOnlyWhenBlinkEnabled()
	{
		when(client.getMouseIdleTicks()).thenReturn(50 * 270);
		when(client.getKeyboardIdleTicks()).thenReturn(50 * 270);
		plugin.handleTick(1000);
		assertTrue(plugin.pulse.mintAlpha(1000) > 0f);
		when(config.blinkWhenDone()).thenReturn(false);
		plugin.pulse = new com.superhistogram.ui.Pulse();
		when(client.getMouseIdleTicks()).thenReturn(50 * 100);
		when(client.getKeyboardIdleTicks()).thenReturn(50 * 100);
		plugin.handleTick(2000);
		when(client.getMouseIdleTicks()).thenReturn(50 * 270);
		when(client.getKeyboardIdleTicks()).thenReturn(50 * 270);
		plugin.handleTick(3000);
		assertEquals(0f, plugin.pulse.mintAlpha(3000), 0f);
		assertEquals(Mode.AFK, plugin.buildState().mode);
	}

	@Test
	public void buildStateIsSafeBeforeStartAndLoggedOut()
	{
		SuperHistogramPlugin fresh = new SuperHistogramPlugin();
		fresh.config = config;
		assertNull(fresh.buildState());
		paint(null);

		when(client.getLocalPlayer()).thenReturn(null);
		plugin.handleTick(1000);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 3);
		paint(plugin.buildState());
	}

	@Test
	public void healAndPoisonSplatsAreNotHits()
	{
		plugin.handleTick(1000);
		plugin.handleHit(me, HitsplatID.HEAL, 5);
		plugin.handleHit(me, HitsplatID.POISON, 2);
		plugin.handleHit(me, HitsplatID.VENOM, 6);
		plugin.handleHit(me, HitsplatID.DISEASE, 1);
		assertTrue(plugin.buildState().timeline.peek(0).hits.isEmpty());
		plugin.handleHit(me, HitsplatID.DAMAGE_MAX_ME, 11);
		assertEquals(1, plugin.buildState().timeline.peek(0).hits.size());
	}

	@Test
	public void thresholdChangeKeepsWarningFromRefiring()
	{
		when(client.getMouseIdleTicks()).thenReturn(50 * 270);
		when(client.getKeyboardIdleTicks()).thenReturn(50 * 270);
		plugin.handleTick(1000);
		assertTrue(plugin.idle.warningFired());
		when(config.afkAfterSeconds()).thenReturn(120);
		plugin.handleTick(1600);
		assertEquals(false, plugin.idle.warningFired());
	}

	@Test
	public void logoutClearsBarAndRelogKeepsSessionXpOnly()
	{
		at(100, 100);
		plugin.handleTick(1000);
		at(101, 100);
		plugin.handleTick(1600);
		plugin.handleStat("Attack", 1000);
		plugin.handleStat("Attack", 1100);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 3);
		plugin.handleClick(1700);
		assertEquals(1, plugin.buildState().timeline.spansBetween(0, 10).size());

		plugin.handleGameState(GameState.LOGIN_SCREEN, 2000);
		assertNull(plugin.buildState());
		paint(plugin.buildState());

		plugin.handleGameState(GameState.LOGGED_IN, 3000);
		BarState s = plugin.buildState();
		assertTrue(s.timeline.spansBetween(0, 1000).isEmpty());
		assertEquals(-1, s.timeline.newestTick());
		for (int t = 0; t < 10; t++)
		{
			assertEquals(null, s.timeline.peek(t));
		}
		assertEquals(100, s.xp.sessionXp());

		// Baseline again after relog, and new drops land on the new timeline.
		plugin.handleTick(4000);
		plugin.handleStat("Attack", 5000);
		plugin.handleStat("Attack", 5050);
		assertEquals(150, plugin.buildState().xp.sessionXp());
		assertEquals(1, plugin.buildState().timeline.peek(0).xp.size());
	}

	@Test
	public void hopKeepsTimelineAndSessionXp()
	{
		plugin.handleTick(1000);
		plugin.handleStat("Attack", 1000);
		plugin.handleStat("Attack", 1100);
		plugin.handleGameState(GameState.HOPPING, 2000);
		plugin.handleGameState(GameState.LOGGED_IN, 2500);
		assertEquals(1, plugin.buildState().timeline.peek(0).xp.size());
		assertEquals(100, plugin.buildState().xp.sessionXp());
	}

	@Test
	public void ownAttackAnimationRecordsAttack()
	{
		when(me.getInteracting()).thenReturn(npc);
		when(me.getAnimation()).thenReturn(390);
		plugin.handleTick(1000);
		plugin.handleAnimation(me);
		assertEquals(0, plugin.timeline.peek(0).attackLate);
	}

	@Test
	public void eatAnimationIsNotAttack()
	{
		when(me.getInteracting()).thenReturn(npc);
		when(me.getAnimation()).thenReturn(829);
		plugin.handleTick(1000);
		plugin.handleAnimation(me);
		assertEquals(-1, plugin.timeline.peek(0).attackLate);
	}

	@Test
	public void animationWithoutNpcTargetIgnored()
	{
		when(me.getInteracting()).thenReturn(null);
		when(me.getAnimation()).thenReturn(390);
		plugin.handleTick(1000);
		plugin.handleAnimation(me);
		assertEquals(-1, plugin.timeline.peek(0).attackLate);
	}

	@Test
	public void weaponSpeedFromItemStats()
	{
		when(itemManager.getItemStats(4151)).thenReturn(
			new ItemStats(true, 0.5, 0, ItemEquipmentStats.builder().aspeed(6).build()));
		plugin.handleWeapon(4151);
		assertEquals(6, plugin.weaponSpeed);
	}

	@Test
	public void missingStatsFallBackTo4()
	{
		when(itemManager.getItemStats(1234)).thenReturn(null);
		plugin.handleWeapon(1234);
		assertEquals(4, plugin.weaponSpeed);
		plugin.handleWeapon(-1);
		assertEquals(4, plugin.weaponSpeed);
	}

	private static void paint(BarState s)
	{
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		new BarPainter(null).paint(g, l, s, System.currentTimeMillis());
		g.dispose();
	}
}
