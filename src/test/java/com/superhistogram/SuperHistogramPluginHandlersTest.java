package com.superhistogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
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
		when(config.timeToFinish()).thenReturn(true);
		when(config.wastedTicks()).thenReturn(true);
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

	// ---- Task 11: beat, prayer spans, metronome ----

	private void fight()
	{
		when(me.getInteracting()).thenReturn(npc);
		when(client.getVarcStrValue(net.runelite.api.VarClientStr.CHATBOX_TYPED_TEXT)).thenReturn("");
	}

	private float alphaAtTick3;

	private void pressAfterTick0()
	{
		fight();
		plugin.handleTick(0);
		plugin.handleBeatKey();
		plugin.handleTick(600);
		plugin.handleTick(1200);
		plugin.handleTick(1800);
		// Pulse holds only the latest tick, so sample 1800 before tick 4 arrives.
		alphaAtTick3 = plugin.pulse.outlineAlpha(1800);
		plugin.handleTick(2400);
	}

	@Test
	public void cueTickPulsesOnlyOnBeat()
	{
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.CUE_TICK);
		pressAfterTick0();
		assertEquals(0f, alphaAtTick3, 0f);
		assertTrue(plugin.pulse.outlineAlpha(2400) > 0.9f);
	}

	@Test
	public void everyTickMetronomeBrightOnBeat()
	{
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.EVERY_TICK);
		pressAfterTick0();
		assertTrue(alphaAtTick3 > 0f && alphaAtTick3 < 0.5f);
		assertTrue(plugin.pulse.outlineAlpha(2400) > 0.9f);
	}

	@Test
	public void hotkeyIgnoredWhileTypingInChat()
	{
		when(me.getInteracting()).thenReturn(npc);
		when(client.getVarcStrValue(net.runelite.api.VarClientStr.CHATBOX_TYPED_TEXT)).thenReturn("f");
		plugin.handleTick(0);
		plugin.handleBeatKey();
		assertFalse(plugin.beat.active());
	}

	@Test
	public void hotkeyIgnoredOutOfCombatInPlugin()
	{
		when(me.getInteracting()).thenReturn(null);
		when(client.getVarcStrValue(net.runelite.api.VarClientStr.CHATBOX_TYPED_TEXT)).thenReturn("");
		plugin.handleTick(0);
		plugin.handleBeatKey();
		assertFalse(plugin.beat.active());
	}

	@Test
	public void logoutMidBeatClearsBeat()
	{
		fight();
		plugin.handleTick(0);
		plugin.handleBeatKey();
		assertTrue(plugin.beat.active());
		plugin.handleGameState(GameState.LOGIN_SCREEN, 1000);
		assertFalse(plugin.beat.active());
		assertNull(plugin.buildState());
		plugin.handleGameState(GameState.LOGGED_IN, 2000);
		for (int i = 0; i < 10; i++)
		{
			plugin.handleTick(3000 + i * 600L);
			assertFalse(plugin.beat.isBeat(i));
		}
	}

	@Test
	public void prayerObservedEachTick()
	{
		plugin.handleTick(0);
		when(client.isPrayerActive(net.runelite.api.Prayer.PROTECT_FROM_MAGIC)).thenReturn(true);
		plugin.handleTick(600);
		plugin.handleTick(1200);
		List<Span> spans = plugin.buildState().timeline.spansBetween(0, 10);
		assertEquals(1, spans.size());
		assertEquals("pray-magic", spans.get(0).kind);
		assertEquals(0, spans.get(0).startTick);
	}

	@Test
	public void prayerClicksClassifyThePreviousTick()
	{
		plugin.handleTick(1000);
		plugin.handlePrayerClick(1200);
		plugin.handlePrayerClick(1250);
		plugin.handleTick(1600);
		TickRecord r = plugin.buildState().timeline.peek(0);
		assertEquals(com.superhistogram.model.combat.FlickClassifier.Kind.OK, r.flick);
		assertEquals(0.333f, r.flickPhase, 0.01f);
		assertTrue(r.clicks.isEmpty());
	}

	// ---- hull flash ----

	private void hullSetup(String name)
	{
		when(config.hullFlash()).thenReturn(true);
		when(config.hullFlashAtBosses()).thenReturn(false);
		when(config.hullFlashMisses()).thenReturn(SuperHistogramConfig.HullFlashMisses.FAINT);
		when(npc.getIndex()).thenReturn(7);
		when(npc.getInteracting()).thenReturn(me);
		when(npc.getAnimation()).thenReturn(422);
		when(npc.getName()).thenReturn(name);
	}

	@Test
	public void hitFlashesSingleAttacker()
	{
		hullSetup("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertTrue(plugin.flashTarget().npc == npc);
		assertFalse(plugin.flashTarget().miss);
		assertEquals(1000, plugin.flashTarget().startMs);
	}

	@Test
	public void missFlashesFaint()
	{
		hullSetup("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleHit(me, HitsplatID.BLOCK_ME, 0, 1000);
		assertTrue(plugin.flashTarget().miss);
	}

	@Test
	public void swingAloneDoesNotFlash()
	{
		hullSetup("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		assertNull(plugin.flashTarget());
	}

	@Test
	public void twoAttackersSameTickFlashNothing()
	{
		hullSetup("Goblin");
		NPC other = mock(NPC.class);
		when(other.getIndex()).thenReturn(9);
		when(other.getInteracting()).thenReturn(me);
		when(other.getAnimation()).thenReturn(422);
		when(other.getName()).thenReturn("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleAnimation(other);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertNull(plugin.flashTarget());
	}

	@Test
	public void swingAtSomeoneElseIgnored()
	{
		hullSetup("Goblin");
		when(npc.getInteracting()).thenReturn(mock(Player.class));
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertNull(plugin.flashTarget());
	}

	@Test
	public void bossNotFlashedByDefault()
	{
		hullSetup("Vorkath");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertNull(plugin.flashTarget());
	}

	@Test
	public void despawnAndLogoutClearAttribution()
	{
		hullSetup("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleNpcDespawned(npc);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertNull(plugin.flashTarget());

		plugin.handleAnimation(npc);
		plugin.handleGameState(GameState.LOGIN_SCREEN, 0);
		plugin.handleGameState(GameState.LOGGED_IN, 0);
		plugin.handleTick(1100);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1200);
		assertNull(plugin.flashTarget());
	}

	@Test
	public void hopClearsAttribution()
	{
		hullSetup("Goblin");
		plugin.handleTick(900);
		plugin.handleAnimation(npc);
		plugin.handleGameState(GameState.HOPPING, 0);
		plugin.handleGameState(GameState.LOGGED_IN, 0);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 5, 1000);
		assertNull(plugin.flashTarget());
	}

	// ---- production ----

	/** Number of handleTick calls so far in the production tests (tick numbers start at 0). */
	private int ticks;

	private void tickOnly()
	{
		plugin.handleTick(ticks * 600L);
		ticks++;
	}

	/** The baseline inventory and stat, on tick 0. */
	private void cookingBaseline()
	{
		ticks = 0;
		tickOnly();
		plugin.handleInventory(java.util.Collections.singletonMap(383, 28));
		plugin.handleStat("Cooking", 1_000_000);
	}

	/** Raw sharks leave the inventory with Cooking XP on a new tick: the k-th (1-based) item. */
	private void cookAction(int k)
	{
		tickOnly();
		plugin.handleInventory(java.util.Collections.singletonMap(383, 28 - k));
		plugin.handleStat("Cooking", 1_000_000 + 210 * k);
	}

	/** Cooks items 1..n one per tick, then one more tick so the last one is processed. */
	private void cookRun(int n)
	{
		cookingBaseline();
		for (int k = 1; k <= n; k++)
		{
			cookAction(k);
		}
		tickOnly();
	}

	@Test
	public void productionDetectedAfterTwoActions()
	{
		cookingBaseline();
		cookAction(1);
		for (int i = 0; i < 4; i++)
		{
			tickOnly();
		}
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
		cookAction(2);
		tickOnly();
		BarState s = plugin.buildState();
		assertEquals(Mode.PRODUCTION, s.mode);
		assertEquals("Cooking", s.modeName);
		assertEquals("auto", s.modeSub);
	}

	@Test
	public void inventoryDoneBlinks()
	{
		cookRun(28);
		assertTrue(plugin.pulse.mintAlpha((ticks - 1) * 600L) > 0f);
	}

	@Test
	public void inventoryDoneDoesNotBlinkWhenDisabled()
	{
		when(config.blinkWhenDone()).thenReturn(false);
		cookRun(28);
		assertEquals(0f, plugin.pulse.mintAlpha((ticks - 1) * 600L), 0f);
	}

	@Test
	public void cueOnFinishTick()
	{
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.CUE_TICK);
		cookingBaseline();
		for (int k = 1; k <= 4; k++)
		{
			tickOnly();
			cookAction(k);
		}
		tickOnly();
		int finish = plugin.run.finishTick();
		assertTrue(plugin.run.running());
		while (ticks < finish - 1)
		{
			tickOnly();
		}
		long before = ticks * 600L;
		tickOnly();
		assertEquals(0f, plugin.pulse.outlineAlpha(before), 0f);
		long at = ticks * 600L;
		tickOnly();
		assertTrue(plugin.pulse.outlineAlpha(at) > 0.9f);
	}

	@Test
	public void bankClickRecordsWasted()
	{
		cookRun(28);
		assertEquals(28, plugin.run.finishTick());
		tickOnly();
		tickOnly();
		int now = ticks - 1;
		assertEquals(31, now);
		plugin.handleBankClick();
		assertEquals(3, plugin.timeline.peek(now).wasted);
	}

	@Test
	public void isBankClickMatches()
	{
		assertTrue(SuperHistogramPlugin.isBankClick("Bank", "<col=ffff>Banker"));
		assertTrue(SuperHistogramPlugin.isBankClick("Bank", null));
		assertTrue(SuperHistogramPlugin.isBankClick("Use", "<col=ffff>Bank chest"));
		assertFalse(SuperHistogramPlugin.isBankClick("Use", "Raw shark"));
		assertFalse(SuperHistogramPlugin.isBankClick("Use", null));
		assertFalse(SuperHistogramPlugin.isBankClick(null, "Bank"));
	}

	@Test
	public void logoutClearsRun()
	{
		cookRun(5);
		assertTrue(plugin.run.running());
		plugin.handleGameState(GameState.LOGIN_SCREEN, 100_000);
		assertFalse(plugin.run.running());
		assertNull(plugin.latestInventory);
	}

	@Test
	public void hoppingKeepsRun()
	{
		cookRun(5);
		plugin.handleGameState(GameState.HOPPING, 100_000);
		assertTrue(plugin.run.running());
	}

	// ---- high alch ----

	private static final int RUNE_PLATEBODY = 1127, RUNE_PLATEBODY_NOTED = 1128;

	private void setHaPrice(int haPrice)
	{
		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getHaPrice()).thenReturn(haPrice);
		when(itemManager.canonicalize(RUNE_PLATEBODY)).thenReturn(RUNE_PLATEBODY);
		when(itemManager.canonicalize(RUNE_PLATEBODY_NOTED)).thenReturn(RUNE_PLATEBODY);
		when(itemManager.getItemComposition(RUNE_PLATEBODY)).thenReturn(comp);
	}

	@Test
	public void alchCoinsIsHaValue()
	{
		setHaPrice(39000);
		assertEquals(Long.valueOf(39000), plugin.alchCoins(RUNE_PLATEBODY));
		verify(itemManager, never()).getItemPrice(anyInt());
	}

	@Test
	public void notedItemUsesBaseValue()
	{
		setHaPrice(39000);
		assertEquals(Long.valueOf(39000), plugin.alchCoins(RUNE_PLATEBODY_NOTED));
	}

	@Test
	public void alchCoinsUnknownWhenNoValue()
	{
		setHaPrice(0);
		assertNull(plugin.alchCoins(RUNE_PLATEBODY));
		setHaPrice(39000);
		assertNull(plugin.alchCoins(-1));
		when(itemManager.getItemComposition(RUNE_PLATEBODY)).thenReturn(null);
		assertNull(plugin.alchCoins(RUNE_PLATEBODY));
	}

	@Test
	public void highAlchClickDetected()
	{
		String target = "<col=00ff00>High Level Alchemy</col><col=ffffff> -> <col=ff9040>Rune platebody";
		assertTrue(SuperHistogramPlugin.isHighAlchClick(MenuAction.WIDGET_TARGET_ON_WIDGET, target));
		assertFalse(SuperHistogramPlugin.isHighAlchClick(MenuAction.WIDGET_TARGET_ON_WIDGET,
			"<col=00ff00>Low Level Alchemy</col><col=ffffff> -> <col=ff9040>Rune platebody"));
		assertFalse(SuperHistogramPlugin.isHighAlchClick(MenuAction.CC_OP, target));
		assertFalse(SuperHistogramPlugin.isHighAlchClick(MenuAction.WIDGET_TARGET_ON_WIDGET, null));
	}

	/** Baseline tick with a Magic stat, then a click on the item, the tick the cast lands on and its XP. */
	private void magicBaseline()
	{
		ticks = 0;
		tickOnly();
		plugin.handleStat("Magic", 1_000_000);
	}

	private void alchCast(int k)
	{
		plugin.handleCastClick(RUNE_PLATEBODY);
		tickOnly();
		plugin.handleStat("Magic", 1_000_000 + 65 * k);
	}

	private void twoCasts()
	{
		alchCast(1);
		for (int i = 0; i < 4; i++)
		{
			tickOnly();
		}
		alchCast(2);
		tickOnly();
	}

	@Test
	public void alchModeAfterTwoCasts()
	{
		setHaPrice(39000);
		magicBaseline();
		alchCast(1);
		for (int i = 0; i < 4; i++)
		{
			tickOnly();
		}
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
		alchCast(2);
		tickOnly();
		BarState s = plugin.buildState();
		assertEquals(Mode.ALCH, s.mode);
		assertEquals("High alch", s.modeName);
		assertEquals("auto \u00b7 5t", s.modeSub);
		assertEquals(78000, s.alch.sessionGold());
	}

	@Test
	public void alchBeatsProductionInPlugin()
	{
		setHaPrice(39000);
		cookingBaseline();
		plugin.handleStat("Magic", 1_000_000);
		cookAction(1);
		cookAction(2);
		tickOnly();
		assertEquals(Mode.PRODUCTION, plugin.buildState().mode);
		twoCasts();
		assertEquals(Mode.ALCH, plugin.buildState().mode);
	}

	@Test
	public void logoutKeepsGoldClearsCasts()
	{
		setHaPrice(39000);
		magicBaseline();
		twoCasts();
		assertEquals(78000, plugin.alch.sessionGold());
		plugin.handleGameState(GameState.LOGIN_SCREEN, 100_000);
		assertEquals(78000, plugin.alch.sessionGold());
		assertEquals(-1, plugin.alch.lastCastTick());
	}

	@Test
	public void unpricedClickCountsAsUnpricedCast()
	{
		setHaPrice(0);
		magicBaseline();
		alchCast(1);
		tickOnly();
		assertEquals(1, plugin.alch.unpricedCasts());
		assertEquals(0, plugin.alch.sessionGold());
	}

	// ---- gathering ----

	private static final int YEW = 1515;

	/** Baseline inventory (6 logs, 22 free), one tick, a click on tree (50, 50), then n ticks animating. */
	private void detectChopping(int rollTicks)
	{
		ticks = 0;
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 6), 22);
		plugin.handleStat("Woodcutting", 1_000_000);
		tickOnly();
		plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(50, 50), "Yew tree");
		when(me.getAnimation()).thenReturn(867);
		for (int i = 0; i < rollTicks; i++)
		{
			tickOnly();
		}
	}

	/** A log arrives on a new tick (n logs in the inventory in total, free slots accordingly). */
	private void logArrives(int n)
	{
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, n), 28 - n);
		plugin.handleStat("Woodcutting", 1_000_000 + 175 * (n - 6));
		tickOnly();
	}

	@Test
	public void gatherSkillFromMenu()
	{
		assertEquals("Woodcutting",
			SuperHistogramPlugin.gatherSkill(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", "<col=ffff>Yew tree"));
		assertEquals("Mining",
			SuperHistogramPlugin.gatherSkill(MenuAction.GAME_OBJECT_FIRST_OPTION, "Mine", "<col=ffff>Rocks"));
		assertEquals("Fishing",
			SuperHistogramPlugin.gatherSkill(MenuAction.NPC_FIRST_OPTION, "Lure", "<col=ffff>Fishing spot"));
		assertEquals("Fishing",
			SuperHistogramPlugin.gatherSkill(MenuAction.NPC_SECOND_OPTION, "Bait", "<col=ffff>Fishing spot"));
		assertEquals("Firemaking",
			SuperHistogramPlugin.gatherSkill(MenuAction.CC_OP, "Light", "<col=ff9040>Logs"));
		assertEquals("Firemaking", SuperHistogramPlugin.gatherSkill(MenuAction.WIDGET_TARGET_ON_WIDGET, "Use",
			"Tinderbox -> <col=ff9040>Logs"));
		assertEquals("Firemaking",
			SuperHistogramPlugin.gatherSkill(MenuAction.WIDGET_TARGET_ON_WIDGET, "Use", "Logs -> Tinderbox"));
		assertNull(SuperHistogramPlugin.gatherSkill(MenuAction.GAME_OBJECT_FIRST_OPTION, "Lure", "Tree"));
		assertNull(SuperHistogramPlugin.gatherSkill(MenuAction.WALK, "Walk here", null));
		assertNull(SuperHistogramPlugin.gatherSkill(MenuAction.GAME_OBJECT_FIRST_OPTION, null, "Tree"));
		assertNull(SuperHistogramPlugin.gatherSkill(MenuAction.WIDGET_TARGET_ON_WIDGET, "Use", null));
		assertNull(SuperHistogramPlugin.gatherSkill(null, "Mine", "Rocks"));
	}

	@Test
	public void keysDistinct()
	{
		assertTrue(SuperHistogramPlugin.objectKey(0, 0) > 0);
		assertTrue(SuperHistogramPlugin.npcKey(0) < 0);
		assertTrue(SuperHistogramPlugin.objectKey(1, 2) != SuperHistogramPlugin.objectKey(2, 1));
	}

	@Test
	public void gatheringDetectedAfterRolls()
	{
		detectChopping(3);
		BarState s = plugin.buildState();
		assertEquals(Mode.GATHERING, s.mode);
		assertEquals("Woodcutting", s.modeName);
		assertEquals("auto \u00b7 Yew tree", s.modeSub);
		assertTrue(s.gather == plugin.gather);
	}

	@Test
	public void oneRollIsNotGathering()
	{
		detectChopping(1);
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
	}

	@Test
	public void treeFallCuesNextTick()
	{
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.CUE_TICK);
		detectChopping(3);
		plugin.handleObjectDespawned(50, 50);
		long now = ticks * 600L;
		tickOnly();
		assertTrue(plugin.pulse.outlineAlpha(now) > 0.9f);
	}

	@Test
	public void despawnWhileLoadingIgnored()
	{
		detectChopping(3);
		plugin.handleGameState(GameState.LOADING, 5000);
		plugin.handleObjectDespawned(50, 50);
		tickOnly();
		assertTrue(plugin.gather.active());
		assertFalse(plugin.gather.pills().get(0).depleted);
	}

	@Test
	public void despawnOfOtherObjectIgnored()
	{
		detectChopping(3);
		plugin.handleObjectDespawned(51, 50);
		tickOnly();
		assertTrue(plugin.gather.active());
	}

	@Test
	public void inventoryFullBlinks()
	{
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 26), 2);
		ticks = 0;
		plugin.handleStat("Woodcutting", 1_000_000);
		tickOnly();
		plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(50, 50), "Yew tree");
		when(me.getAnimation()).thenReturn(867);
		tickOnly();
		tickOnly();
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 27), 1);
		plugin.handleStat("Woodcutting", 1_000_175);
		tickOnly();
		assertEquals(0f, plugin.pulse.mintAlpha((ticks - 1) * 600L), 0f);
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 28), 0);
		plugin.handleStat("Woodcutting", 1_000_350);
		tickOnly();
		assertTrue(plugin.pulse.mintAlpha((ticks - 1) * 600L) > 0f);
	}

	@Test
	public void inventoryFullDoesNotBlinkWhenDisabled()
	{
		when(config.blinkWhenDone()).thenReturn(false);
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 27), 1);
		ticks = 0;
		plugin.handleStat("Woodcutting", 1_000_000);
		tickOnly();
		plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(50, 50), "Yew tree");
		when(me.getAnimation()).thenReturn(867);
		tickOnly();
		tickOnly();
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 28), 0);
		plugin.handleStat("Woodcutting", 1_000_175);
		tickOnly();
		tickOnly();
		assertEquals(0f, plugin.pulse.mintAlpha((ticks - 1) * 600L), 0f);
	}

	@Test
	public void npcDespawnEndsFishing()
	{
		ticks = 0;
		tickOnly();
		plugin.handleGatherClick("Fishing", SuperHistogramPlugin.npcKey(7), "Rod Fishing spot");
		when(me.getAnimation()).thenReturn(622);
		tickOnly();
		tickOnly();
		tickOnly();
		when(npc.getIndex()).thenReturn(7);
		plugin.handleNpcDespawned(npc);
		tickOnly();
		assertTrue(plugin.gather.pills().get(0).depleted);
	}

	@Test
	public void bankClickEndsGatherPill()
	{
		detectChopping(3);
		assertTrue(plugin.gather.active());
		plugin.handleBankClick();
		assertFalse(plugin.gather.active());
	}

	@Test
	public void logoutClearsGather()
	{
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 6), 22);
		assertEquals(22, plugin.latestFreeSlots);
		detectChopping(3);
		plugin.handleGameState(GameState.LOGIN_SCREEN, 100_000);
		assertFalse(plugin.gather.active());
		assertTrue(plugin.gather.pills().isEmpty());
		assertEquals(-1, plugin.latestFreeSlots);
	}

	@Test
	public void hoppingKeepsGather()
	{
		detectChopping(3);
		plugin.handleGameState(GameState.HOPPING, 100_000);
		assertTrue(plugin.gather.active());
	}

	@Test
	public void oneArgumentInventoryKeepsFreeSlots()
	{
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 6), 22);
		plugin.handleInventory(java.util.Collections.singletonMap(YEW, 7));
		assertEquals(22, plugin.latestFreeSlots);
	}

	@Test
	public void despawnRightAfterLoadingIgnoredButLaterCounts()
	{
		detectChopping(3);
		plugin.handleGameState(GameState.LOADING, 5000);
		tickOnly();
		plugin.handleObjectDespawned(50, 50);
		tickOnly();
		assertFalse(plugin.gather.pills().get(0).depleted);
		tickOnly();
		tickOnly();
		plugin.handleObjectDespawned(50, 50);
		tickOnly();
		assertTrue(plugin.gather.pills().get(0).depleted);
	}
}
