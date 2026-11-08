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
import com.superhistogram.ui.SampleStates;
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
import net.runelite.client.config.ConfigManager;
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
	private ConfigManager configManager;

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
		configManager = mock(ConfigManager.class);
		plugin.configManager = configManager;
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
		when(npc.getCombatLevel()).thenReturn(2);
		when(me.getAnimation()).thenReturn(390);
		plugin.handleTick(1000);
		plugin.handleHit(npc, HitsplatID.DAMAGE_ME, 2);
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

	/** A real fight: the player targets a combat NPC that has just been hit. */
	private void fight()
	{
		when(me.getInteracting()).thenReturn(npc);
		when(npc.getCombatLevel()).thenReturn(2);
		plugin.handleHit(npc, HitsplatID.DAMAGE_ME, 2);
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

	// ---- agility ----

	private static final int[] SEERS_IDS = SampleStates.SEERS_IDS, SEERS_DUR = SampleStates.SEERS_DUR;
	private static final int[] SEERS_XP = {30, 40, 25, 35, 60, 30};
	private static final String GROUP = "superhistogram";
	private int agTotal;
	private int lastLanding;

	private void onCourse()
	{
		ticks = 0;
		at(2710, 3470);
		agTotal = 1_000_000;
		plugin.handleStat("Agility", agTotal);
		tickOnly();
	}

	/** Click, DUR ticks, the XP, then the tick that processes the landing. */
	private void obstacle(int k)
	{
		plugin.handleObstacleClick(SEERS_IDS[k]);
		for (int i = 0; i < SEERS_DUR[k]; i++)
		{
			tickOnly();
		}
		agTotal += SEERS_XP[k];
		plugin.handleStat("Agility", agTotal);
		lastLanding = ticks - 1;
		tickOnly();
	}

	@Test
	public void isObstacleActionMatches()
	{
		assertTrue(SuperHistogramPlugin.isObstacleAction(MenuAction.GAME_OBJECT_FIRST_OPTION));
		assertTrue(SuperHistogramPlugin.isObstacleAction(MenuAction.GAME_OBJECT_THIRD_OPTION));
		assertTrue(SuperHistogramPlugin.isObstacleAction(MenuAction.GAME_OBJECT_FIFTH_OPTION));
		assertFalse(SuperHistogramPlugin.isObstacleAction(MenuAction.NPC_FIRST_OPTION));
		assertFalse(SuperHistogramPlugin.isObstacleAction(MenuAction.WALK));
		assertFalse(SuperHistogramPlugin.isObstacleAction(null));
	}

	@Test
	public void agilityDetectedOnCourse()
	{
		onCourse();
		obstacle(0);
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
		obstacle(1);
		BarState s = plugin.buildState();
		assertEquals(Mode.AGILITY, s.mode);
		assertEquals("Agility", s.modeName);
		assertEquals("auto \u00b7 Seers'", s.modeSub);
		assertTrue(s.agility == plugin.agility);
	}

	@Test
	public void obstacleClickIgnoredOffCourse()
	{
		at(3200, 3200);
		tickOnly();
		plugin.handleObstacleClick(14927);
		tickOnly();
		tickOnly();
		assertTrue(plugin.agility.obstacles().isEmpty());
		assertEquals(null, plugin.regionCourse);
	}

	@Test
	public void lapEndBlinksOnlyAtEndTile()
	{
		onCourse();
		for (int k = 0; k < 5; k++)
		{
			obstacle(k);
			assertEquals(0f, plugin.pulse.mintAlpha((ticks - 1) * 600L), 0f);
		}
		plugin.handleObstacleClick(SEERS_IDS[5]);
		for (int i = 0; i < SEERS_DUR[5]; i++)
		{
			tickOnly();
		}
		at(2704, 3464);
		agTotal += SEERS_XP[5];
		plugin.handleStat("Agility", agTotal);
		tickOnly();
		assertTrue(plugin.pulse.mintAlpha((ticks - 1) * 600L) > 0f);
	}

	@Test
	public void lapEndDoesNotBlinkWhenDisabled()
	{
		when(config.blinkWhenDone()).thenReturn(false);
		onCourse();
		for (int k = 0; k < 5; k++)
		{
			obstacle(k);
		}
		plugin.handleObstacleClick(SEERS_IDS[5]);
		for (int i = 0; i < SEERS_DUR[5]; i++)
		{
			tickOnly();
		}
		at(2704, 3464);
		plugin.handleStat("Agility", agTotal + 30);
		tickOnly();
		assertEquals(0f, plugin.pulse.mintAlpha((ticks - 1) * 600L), 0f);
	}

	@Test
	public void cueOnReadyTick()
	{
		when(config.metronome()).thenReturn(SuperHistogramConfig.Metronome.CUE_TICK);
		onCourse();
		obstacle(0);
		assertEquals(0f, plugin.pulse.outlineAlpha((ticks - 1) * 600L), 0f);
		plugin.handleObstacleClick(SEERS_IDS[1]);
		for (int i = 0; i < SEERS_DUR[1]; i++)
		{
			tickOnly();
		}
		agTotal += SEERS_XP[1];
		plugin.handleStat("Agility", agTotal);
		lastLanding = ticks - 1;
		tickOnly();
		assertEquals(lastLanding + 1, plugin.agility.readyTick());
		assertTrue(plugin.pulse.outlineAlpha((ticks - 1) * 600L) > 0.9f);
	}

	@Test
	public void fallDamageIsNotCombat()
	{
		onCourse();
		obstacle(0);
		obstacle(1);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 3);
		assertEquals(Mode.AGILITY, plugin.buildState().mode);
	}

	@Test
	public void hitAloneIsNotCombatWhenAgilityNotShowing()
	{
		onCourse();
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 3);
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
	}

	@Test
	public void bestLapLoadedFromProfile()
	{
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(80);
		onCourse();
		assertEquals(80, plugin.agility.bestTicks());
	}

	@Test
	public void missingBestLeavesMinusOne()
	{
		onCourse();
		assertEquals(-1, plugin.agility.bestTicks());
		assertEquals("SEERS", plugin.regionCourse);
	}

	@Test
	public void noConfigManagerIsFine()
	{
		plugin.configManager = null;
		onCourse();
		for (int k = 0; k < 6; k++)
		{
			obstacle(k);
		}
		assertEquals("SEERS", plugin.agility.course());
	}

	@Test
	public void newBestSavedToProfile()
	{
		onCourse();
		int start = ticks - 1;
		for (int k = 0; k < 5; k++)
		{
			obstacle(k);
		}
		plugin.handleObstacleClick(SEERS_IDS[5]);
		for (int i = 0; i < SEERS_DUR[5]; i++)
		{
			tickOnly();
		}
		at(2704, 3464);
		plugin.handleStat("Agility", agTotal + 30);
		int landing = ticks - 1;
		tickOnly();
		int lap1 = landing - start;
		plugin.handleObstacleClick(SEERS_IDS[0]);
		tickOnly();
		verify(configManager).setRSProfileConfiguration(GROUP, "bestLap.SEERS", lap1);
	}

	@Test
	public void markSpawnOnCourse()
	{
		onCourse();
		obstacle(0);
		plugin.handleObstacleClick(SEERS_IDS[1]);
		for (int i = 0; i < SEERS_DUR[1]; i++)
		{
			tickOnly();
		}
		plugin.handleStat("Agility", agTotal + 40);
		int landing = ticks - 1;
		plugin.handleItemSpawned(net.runelite.api.gameval.ItemID.GRACE, new WorldPoint(2706, 3466, 2));
		tickOnly();
		assertTrue(plugin.timeline.peek(landing).mark);
	}

	@Test
	public void markSpawnIgnoredOffCourseOtherItemOrLoading()
	{
		onCourse();
		obstacle(0);
		for (int variant = 0; variant < 3; variant++)
		{
			plugin.handleObstacleClick(SEERS_IDS[1]);
			for (int i = 0; i < SEERS_DUR[1]; i++)
			{
				tickOnly();
			}
			agTotal += 40;
			plugin.handleStat("Agility", agTotal);
			int landing = ticks - 1;
			if (variant == 0)
			{
				plugin.handleItemSpawned(net.runelite.api.gameval.ItemID.GRACE, new WorldPoint(3200, 3200, 0));
			}
			else if (variant == 1)
			{
				plugin.handleItemSpawned(1515, new WorldPoint(2706, 3466, 2));
			}
			else
			{
				plugin.handleGameState(GameState.LOADING, 0);
				plugin.handleItemSpawned(net.runelite.api.gameval.ItemID.GRACE, new WorldPoint(2706, 3466, 2));
			}
			tickOnly();
			assertFalse("variant " + variant, plugin.timeline.peek(landing).mark);
			plugin.handleObstacleClick(SEERS_IDS[0]);
			for (int i = 0; i < SEERS_DUR[0]; i++)
			{
				tickOnly();
			}
			agTotal += 30;
			plugin.handleStat("Agility", agTotal);
			tickOnly();
		}
	}

	@Test
	public void logoutMidLapKeepsBest()
	{
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(80);
		onCourse();
		obstacle(0);
		obstacle(1);
		plugin.handleObstacleClick(SEERS_IDS[2]);
		tickOnly();
		plugin.handleGameState(GameState.LOGIN_SCREEN, 99_000);
		assertEquals(-1, plugin.agility.lapStartTick());
		// The bests belong to the account: they are forgotten and reload when the account is back on course.
		assertEquals(-1, plugin.agility.bestTicks());
		assertEquals(null, plugin.regionCourse);
		plugin.handleGameState(GameState.LOGGED_IN, 100_000);
		onCourse();
		assertEquals(80, plugin.agility.bestTicks());
		assertEquals(Mode.GENERAL, plugin.buildState() == null ? Mode.GENERAL : plugin.buildState().mode);
	}

	@Test
	public void hoppingKeepsAgility()
	{
		onCourse();
		obstacle(0);
		obstacle(1);
		plugin.handleGameState(GameState.HOPPING, 99_000);
		plugin.handleGameState(GameState.LOGGED_IN, 99_100);
		assertEquals(2, plugin.agility.obstacles().size());
		assertEquals("SEERS", plugin.regionCourse);
	}

	@Test
	public void landingOnTheTickOfTheNextClickCounts()
	{
		onCourse();
		obstacle(0);
		plugin.handleObstacleClick(SEERS_IDS[1]);
		for (int i = 0; i < SEERS_DUR[1]; i++)
		{
			tickOnly();
		}
		plugin.handleStat("Agility", agTotal + 40);
		plugin.handleObstacleClick(SEERS_IDS[2]);
		tickOnly();
		tickOnly();
		java.util.List<com.superhistogram.model.skill.AgilityCourse.Obstacle> o = plugin.agility.obstacles();
		assertEquals(3, o.size());
		assertTrue(o.get(1).endTick >= 0);
		assertEquals(-1, o.get(2).endTick);
	}

	@Test
	public void finalObstacleIsAlwaysReported()
	{
		onCourse();
		for (int k = 0; k < 5; k++)
		{
			obstacle(k);
		}
		plugin.handleObstacleClick(SEERS_IDS[5]);
		for (int i = 0; i < SEERS_DUR[5]; i++)
		{
			tickOnly();
		}
		at(2704, 3464);
		plugin.handleStat("Agility", agTotal + 30);
		tickOnly();
		assertEquals(6, plugin.agility.obstacles().size());
		assertEquals(6, plugin.agility.obstacles().get(5).number);
	}

	/** One full lap from obstacle 0 on the end tile; returns the lap's ticks. */
	private int fullLap()
	{
		int start = ticks - 1;
		for (int k = 0; k < 5; k++)
		{
			obstacle(k);
		}
		plugin.handleObstacleClick(SEERS_IDS[5]);
		for (int i = 0; i < SEERS_DUR[5]; i++)
		{
			tickOnly();
		}
		at(2704, 3464);
		plugin.handleStat("Agility", agTotal + 30);
		int landing = ticks - 1;
		tickOnly();
		return landing - start;
	}

	@Test
	public void bestDoesNotLeakToAnotherAccount()
	{
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(50);
		onCourse();
		assertEquals(50, plugin.agility.bestTicks());
		plugin.handleGameState(GameState.LOGIN_SCREEN, 99_000);
		// Account B has a slower saved best.
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(90);
		plugin.handleGameState(GameState.LOGGED_IN, 100_000);
		onCourse();
		assertEquals(90, plugin.agility.bestTicks());
	}

	@Test
	public void anotherAccountWithoutBestStartsEmptyAndSavesItsFirstLap()
	{
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(40);
		onCourse();
		plugin.handleGameState(GameState.LOGIN_SCREEN, 99_000);
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(null);
		plugin.handleGameState(GameState.LOGGED_IN, 100_000);
		onCourse();
		assertEquals(-1, plugin.agility.bestTicks());
		int lap = fullLap();
		assertTrue(lap > 40);
		plugin.handleObstacleClick(SEERS_IDS[0]);
		tickOnly();
		verify(configManager).setRSProfileConfiguration(GROUP, "bestLap.SEERS", lap);
	}

	@Test
	public void profileChangeForgetsBests()
	{
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(50);
		onCourse();
		plugin.handleProfileChanged();
		assertEquals(-1, plugin.agility.bestTicks());
		assertEquals(null, plugin.agility.course());
		when(configManager.getRSProfileConfiguration(GROUP, "bestLap.SEERS", Integer.class)).thenReturn(70);
		tickOnly();
		assertEquals(70, plugin.agility.bestTicks());
	}

	@Test
	public void pinnedModeOverridesDetectionInBuildState()
	{
		when(config.mode()).thenReturn(com.superhistogram.model.ModeSetting.ALCH);
		assertEquals(Mode.ALCH, plugin.buildState().mode);
		when(config.mode()).thenReturn(com.superhistogram.model.ModeSetting.AUTO);
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
	}

	@Test
	public void pinnedModeStillBlankWhenLoggedOut()
	{
		when(config.mode()).thenReturn(com.superhistogram.model.ModeSetting.ALCH);
		plugin.handleGameState(GameState.LOGIN_SCREEN, 0);
		assertNull(plugin.buildState());
	}

	@Test
	public void configChangePushesColoursAndSize()
	{
		com.superhistogram.ui.BarOverlay o = new com.superhistogram.ui.BarOverlay(plugin::buildState, null);
		plugin.overlay = o;
		plugin.overlayManager = mock(net.runelite.client.ui.overlay.OverlayManager.class);
		when(config.size()).thenReturn(com.superhistogram.model.BarSize.LARGE);
		when(config.position()).thenReturn(com.superhistogram.model.BarPosition.FLOATING);
		when(config.damageColour()).thenReturn(java.awt.Color.RED);
		plugin.handleConfigChanged(SuperHistogramPlugin.CONFIG_GROUP, "size");
		assertEquals(new java.awt.Dimension(1026, 80), o.getPreferredSize());
		plugin.handleConfigChanged(SuperHistogramPlugin.CONFIG_GROUP, "position");
		assertTrue(o.isMovable());
		plugin.handleConfigChanged(SuperHistogramPlugin.CONFIG_GROUP, "damageColour");
		assertEquals(java.awt.Color.RED, o.colours().damage);
		// Other groups are ignored.
		when(config.size()).thenReturn(com.superhistogram.model.BarSize.SMALL);
		plugin.handleConfigChanged("other", "size");
		assertEquals(new java.awt.Dimension(1026, 80), o.getPreferredSize());
	}

	// ---- real combat: an NPC target with a combat level that took a hitsplat (final review C1, I3) ----

	/** The player interacts with npc (index 7) of the given name and combat level. */
	private void target(String name, int combatLevel)
	{
		when(npc.getIndex()).thenReturn(7);
		when(npc.getName()).thenReturn(name);
		when(npc.getCombatLevel()).thenReturn(combatLevel);
		when(me.getInteracting()).thenReturn(npc);
	}

	private boolean anyAttackRecorded()
	{
		for (int t = 0; t <= ticks; t++)
		{
			TickRecord r = plugin.timeline.peek(t);
			if (r != null && r.attackLate >= 0)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void fishingSpotIsNotCombat()
	{
		ticks = 0;
		target("Rod Fishing spot", 0);
		plugin.handleInventory(java.util.Collections.singletonMap(335, 6), 22);
		plugin.handleStat("Fishing", 1_000_000);
		tickOnly();
		plugin.handleGatherClick("Fishing", SuperHistogramPlugin.npcKey(7), "Rod Fishing spot");
		when(me.getAnimation()).thenReturn(622);
		for (int i = 0; i < 3; i++)
		{
			tickOnly();
			plugin.handleAnimation(me);
		}
		assertEquals(Mode.GATHERING, plugin.buildState().mode);
		assertFalse(anyAttackRecorded());
	}

	@Test
	public void npcDialogueIsNotCombat()
	{
		ticks = 0;
		target("Man", 2);
		for (int i = 0; i < 4; i++)
		{
			tickOnly();
		}
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
	}

	@Test
	public void bankerIsNotCombat()
	{
		cookRun(28);
		assertEquals(Mode.PRODUCTION, plugin.buildState().mode);
		target("Banker", 0);
		plugin.handleBankClick();
		tickOnly();
		assertEquals(Mode.PRODUCTION, plugin.buildState().mode);
	}

	@Test
	public void pickpocketIsNotCombat()
	{
		ticks = 0;
		target("Man", 2);
		when(me.getAnimation()).thenReturn(881);
		tickOnly();
		plugin.handleAnimation(me);
		tickOnly();
		// Caught: the stun hit lands on the player, not the NPC.
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 1);
		plugin.handleAnimation(me);
		tickOnly();
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
		assertFalse(anyAttackRecorded());
		assertEquals(1, plugin.timeline.peek(1).hits.size());
	}

	@Test
	public void realFightIsCombat()
	{
		ticks = 0;
		target("Goblin", 2);
		when(me.getAnimation()).thenReturn(390);
		tickOnly();
		plugin.handleHit(npc, HitsplatID.DAMAGE_ME, 3);
		assertEquals(Mode.LAZY_FLICK, plugin.buildState().mode);
		for (int i = 0; i < 4; i++)
		{
			tickOnly();
		}
		plugin.handleAnimation(me);
		assertTrue(anyAttackRecorded());
		// Target gone (it died): the recent attack keeps combat on.
		when(me.getInteracting()).thenReturn(null);
		tickOnly();
		assertEquals(Mode.LAZY_FLICK, plugin.buildState().mode);
	}

	@Test
	public void talkingToNpcSomeoneElseIsFightingIsNotCombat()
	{
		ticks = 0;
		target("Guard", 21);
		for (int i = 0; i < 3; i++)
		{
			tickOnly();
			// Another player's hits land on the NPC the player is talking to.
			plugin.handleHit(npc, HitsplatID.DAMAGE_OTHER, 4, i * 600L, false);
		}
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
		plugin.handleHit(npc, HitsplatID.DAMAGE_ME, 4, 1800, true);
		assertEquals(Mode.LAZY_FLICK, plugin.buildState().mode);
	}

	@Test
	public void hitOnTargetTooLongAgoIsNotCombat()
	{
		ticks = 0;
		target("Goblin", 2);
		tickOnly();
		plugin.handleHit(npc, HitsplatID.DAMAGE_ME, 3);
		for (int i = 0; i < 11; i++)
		{
			tickOnly();
		}
		assertEquals(Mode.GENERAL, plugin.buildState().mode);
	}

	@Test
	public void hitsOnPlayerWhileGatheringKeepGathering()
	{
		detectChopping(3);
		when(me.getInteracting()).thenReturn(null);
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 6);
		tickOnly();
		plugin.handleHit(me, HitsplatID.DAMAGE_ME, 4);
		BarState s = plugin.buildState();
		assertEquals(Mode.GATHERING, s.mode);
		assertEquals(1, s.timeline.peek(ticks - 2).hits.size());
		assertEquals(1, s.timeline.peek(ticks - 1).hits.size());
	}

	// ---- session rates over accumulated logged-in time (final review I2) ----

	@Test
	public void relogRateUsesAccumulatedLoggedInTime()
	{
		// setUp logged in at 0 ms. 200k Magic XP over 2 h, logout, then 1 min back in.
		plugin.handleTick(0);
		plugin.handleStat("Magic", 1_000_000);
		plugin.handleStat("Magic", 1_200_000);
		plugin.handleGameState(GameState.LOGIN_SCREEN, 7_200_000);
		plugin.handleGameState(GameState.LOGGED_IN, 7_300_000);
		plugin.handleStat("Magic", 1_200_000);
		BarState s = plugin.buildState(7_360_000);
		// 200,000 xp / (2 h + 1 min) = 99,173 xp/hr; dividing by the 1 min login alone would give 12M/hr.
		assertEquals("99.1k/hr", com.superhistogram.ui.modes.MinimalView.headline(s));
		assertEquals(60_000, s.clock.onlineMs(s.nowMs));
	}

	@Test
	public void profileChangeResetsSessionTotals()
	{
		setHaPrice(39000);
		magicBaseline();
		alchCast(1);
		tickOnly();
		assertTrue(plugin.alch.sessionGold() > 0);
		assertEquals(65, plugin.xp.sessionXp("Magic"));
		plugin.handleGameState(GameState.LOGIN_SCREEN, 7_200_000);
		plugin.handleGameState(GameState.LOGGED_IN, 7_300_000);
		plugin.handleProfileChanged(7_300_000);
		assertEquals(0, plugin.alch.sessionGold());
		assertEquals(0, plugin.xp.sessionXp());
		assertEquals(0, plugin.xp.sessionXp("Magic"));
		assertNull(plugin.xp.topSkill());
		assertEquals(30_000, plugin.clock.sessionMs(7_330_000));
		// The new account's first stats are still baselines.
		plugin.handleStat("Magic", 5_000_000);
		assertEquals(0, plugin.xp.sessionXp());
	}

	@Test
	public void perHourIsDashBeforeAMinute()
	{
		plugin.handleTick(0);
		plugin.handleStat("Magic", 1_000_000);
		plugin.handleStat("Magic", 1_000_500);
		assertEquals("\u2014/hr", com.superhistogram.ui.modes.MinimalView.headline(plugin.buildState(30_000)));
		assertEquals("Magic \u00b7 level 74", com.superhistogram.ui.modes.MinimalView.smallLine(plugin.buildState(30_000)));
		assertEquals("30k/hr", com.superhistogram.ui.modes.MinimalView.headline(plugin.buildState(60_000)));
	}
}
