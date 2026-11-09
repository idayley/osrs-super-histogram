package com.superhistogram;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarState;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.PrayerMode;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.BarPainter;
import com.superhistogram.ui.HullFlashOverlay;
import com.superhistogram.ui.Pulse;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Plays scripted stretches of real play through the plugin's own handlers, tick by tick, and records the bar
 * the way the overlay draws it (with the operator's saved settings), 20 frames a second, plus a log of the
 * mode, combat and hull flash each tick. Needs -PrenderDir; writes one folder of frames per scenario.
 */
public class UsabilityReplay
{
	static final int W = Integer.getInteger("superhistogram.renderW", 583);
	static final int FRAMES_PER_TICK = 12;

	private Client client;
	private Player me;
	private NPC npc;
	private SuperHistogramPlugin plugin;
	private final Map<String, Object> overrides = new HashMap<>();
	private final Map<Prayer, Boolean> prayers = new HashMap<>();
	private int prayerPoints = 70;
	private int x = 3200, y = 3200;
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private long ms = 1_000_000;
	/** Extra ms the next tick arrives late (server lag). */
	private long lagMs;
	private int tick = -1;
	private File dir;
	private int frame;
	private int height;
	private BarPainter painter;
	private StringBuilder log;
	private final List<BufferedImage> strip = new ArrayList<>();

	private SuperHistogramConfig config()
	{
		// The operator's saved settings (~/.runelite profile), everything else at its default.
		overrides.put("position", BarPosition.FLOATING);
		overrides.put("metronome", SuperHistogramConfig.Metronome.EVERY_TICK);
		overrides.put("actionsRow", ActionsRow.ATTACKS);
		overrides.put("prayerMode", PrayerMode.LAZY_FLICK);
		overrides.put("combatHoldSeconds", 10);
		overrides.put("nowLinePercent", 80);
		return (SuperHistogramConfig) Proxy.newProxyInstance(SuperHistogramConfig.class.getClassLoader(),
			new Class<?>[]{SuperHistogramConfig.class}, (p, m, args) ->
			{
				if (overrides.containsKey(m.getName()))
				{
					return overrides.get(m.getName());
				}
				if (m.isDefault())
				{
					return MethodHandles.privateLookupIn(SuperHistogramConfig.class, MethodHandles.lookup())
						.unreflectSpecial(m, SuperHistogramConfig.class).bindTo(p).invokeWithArguments(args);
				}
				if (m.getName().equals("hashCode"))
				{
					return 0;
				}
				return null;
			});
	}

	private void setUp(String name, int h) throws Exception
	{
		String root = System.getProperty("superhistogram.renderDir");
		assumeTrue(root != null && !root.isEmpty());
		dir = new File(root, name + "-" + h);
		dir.mkdirs();
		for (File f : dir.listFiles())
		{
			f.delete();
		}
		height = Integer.getInteger("superhistogram.renderH", h);
		client = mock(Client.class);
		me = mock(Player.class);
		npc = mock(NPC.class);
		when(client.getLocalPlayer()).thenReturn(me);
		when(client.getEnergy()).thenReturn(8000);
		when(client.getMouseIdleTicks()).thenReturn(0);
		when(client.getKeyboardIdleTicks()).thenReturn(0);
		when(client.isPrayerActive(any(Prayer.class))).thenAnswer(i -> prayers.getOrDefault(i.getArgument(0), false));
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenAnswer(i -> prayerPoints);
		when(me.getAnimation()).thenReturn(-1);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((Integer) i.getArgument(0), 0));
		when(client.getVarpValue(anyInt())).thenAnswer(i -> varps.getOrDefault((Integer) i.getArgument(0), 0));
		when(me.getWorldLocation()).thenAnswer(i -> new WorldPoint(x, y, 0));
		when(npc.getIndex()).thenReturn(7);
		when(npc.getName()).thenReturn("Hill Giant");
		when(npc.getCombatLevel()).thenReturn(28);
		when(npc.getAnimation()).thenReturn(-1);
		plugin = new SuperHistogramPlugin();
		plugin.client = client;
		plugin.config = config();
		plugin.itemManager = mock(net.runelite.client.game.ItemManager.class);
		plugin.configManager = mock(net.runelite.client.config.ConfigManager.class);
		plugin.initModel();
		plugin.pulse = new Pulse();
		painter = new BarPainter(plugin.pulse);
		plugin.handleGameState(GameState.LOGGED_IN, ms);
		log = new StringBuilder();
		frame = 0;
		strip.clear();
	}

	/** One game tick: the tick event, then this tick's events, then 600 ms of frames. */
	private void tick(IntConsumer events)
	{
		tick++;
		ms += 600 + lagMs;
		lagMs = 0;
		plugin.handleTick(ms);
		if (events != null)
		{
			events.accept(tick);
		}
		BarState s = null;
		for (int f = 0; f < FRAMES_PER_TICK; f++)
		{
			long now = ms + f * 600L / FRAMES_PER_TICK;
			s = plugin.buildState(now);
			BufferedImage img = render(s, now);
			write(img, String.format("f%05d.png", frame++));
			if (f == FRAMES_PER_TICK - 1)
			{
				strip.add(img);
			}
		}
		HullFlashOverlay.Target t = plugin.flashTarget();
		log.append(String.format("tick %3d  mode=%-11s %s%n", tick, s == null ? "-" : s.mode,
			t != null && ms - t.startMs < 100 ? (t.miss ? "FLASH(miss)" : "FLASH(hit)") : ""));
	}

	/** Ticks that pass with nothing to draw (an earlier run, a long wait): no frames, no strip rows. */
	private void skip(int ticks, IntConsumer events)
	{
		for (int i = 0; i < ticks; i++)
		{
			tick++;
			ms += 600;
			plugin.handleTick(ms);
			if (events != null)
			{
				events.accept(tick);
			}
		}
	}

	private BufferedImage render(BarState s, long now)
	{
		BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0x3b3f2a)); // roughly the game behind a floating bar
		g.fillRect(0, 0, W, height);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		painter.paint(g, BarLayout.of(W, height), s, now);
		g.dispose();
		return img;
	}

	private void write(BufferedImage img, String name)
	{
		try
		{
			ImageIO.write(img, "png", new File(dir, name));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	/** Every tick's last frame, stacked, 2x, with the tick number, so a whole scenario reads at a glance. */
	private void finish() throws Exception
	{
		int per = 8;
		for (int part = 0; part * per * 2 < strip.size(); part++)
		{
			int from = part * per * 2, to = Math.min(strip.size(), from + per * 2);
			BufferedImage out = new BufferedImage(W * 2 + 60, (to - from) * (height * 2 + 6), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = out.createGraphics();
			g.setColor(new Color(0x202020));
			g.fillRect(0, 0, out.getWidth(), out.getHeight());
			for (int i = from; i < to; i++)
			{
				int yy = (i - from) * (height * 2 + 6);
				g.drawImage(strip.get(i), 60, yy, W * 2, height * 2, null);
				g.setColor(Color.WHITE);
				g.drawString("t" + i, 8, yy + height + 4);
			}
			g.dispose();
			ImageIO.write(out, "png", new File(dir.getParentFile(), dir.getName() + String.format("-strip%02d.png", part)));
		}
		java.nio.file.Files.write(new File(dir.getParentFile(), dir.getName() + "-log.txt").toPath(),
			log.toString().getBytes());
	}

	// ---- actions ----

	private void walk(int tiles)
	{
		x += tiles;
	}

	private void attackClick()
	{
		plugin.handleClick(ms + 100);
		plugin.handleAttackClick(7);
		when(me.getInteracting()).thenReturn(npc);
		when(npc.getInteracting()).thenReturn(me);
	}

	private void mySwing()
	{
		when(me.getAnimation()).thenReturn(422);
		plugin.handleAnimation(me);
	}

	private void myStop()
	{
		when(me.getAnimation()).thenReturn(-1);
	}

	private void npcSwing()
	{
		when(npc.getAnimation()).thenReturn(4652);
		plugin.handleAnimation(npc);
		when(npc.getAnimation()).thenReturn(-1);
	}

	private int strXp = 5_000_000;

	private void splatOnNpc(int amount)
	{
		plugin.handleHit(npc, amount == 0 ? HitsplatID.BLOCK_ME : HitsplatID.DAMAGE_ME, amount, ms, true);
		if (amount > 0)
		{
			strXp += amount * 4;
			plugin.handleStat("Strength", strXp);
		}
	}

	private void splatOnMe(int amount)
	{
		plugin.handleHit(me, amount == 0 ? HitsplatID.BLOCK_ME : HitsplatID.DAMAGE_ME, amount, ms, true);
	}

	private void overhead(Prayer p, String kind, boolean on)
	{
		plugin.handleClick(ms + 250);
		plugin.handlePrayerClick(ms + 250);
		plugin.handleOverheadClick(ms + 250, kind);
		prayers.put(p, on);
	}

	// ---- scenarios ----

	/**
	 * Walk up, click Attack on a Hill Giant, turn on Protect from Melee, trade blows with a 4-tick weapon; the
	 * giant swings every 4 ticks two ticks after you. Your hits: some, then ten misses in a row. Its hits: all
	 * 0s (praying). Eat once. It dies; walk away; stand still.
	 */
	private void fight() throws Exception
	{
		plugin.handleStat("Strength", strXp);
		plugin.handleStat("Hitpoints", 1_000_000);
		for (int i = 0; i < 5; i++)
		{
			tick(t -> walk(1));
		}
		tick(t -> attackClick());
		int[] mine = {12, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 14, 3};
		int first = tick + 1;
		int dies = first + 4 * mine.length + 1;
		while (tick < dies + 20)
		{
			tick(t ->
			{
				int k = t - first;
				if (t == first + 1)
				{
					overhead(Prayer.PROTECT_FROM_MELEE, "melee", true);
				}
				if (t < dies && k % 4 == 0)
				{
					mySwing();
				}
				else
				{
					myStop();
				}
				if (t < dies && k % 4 == 1 && k / 4 < mine.length)
				{
					splatOnNpc(mine[k / 4]);
				}
				if (t < dies && k % 4 == 2)
				{
					npcSwing();
				}
				if (k == 6)
				{
					// You mark the giant's swing with the Beat hotkey (Beat every 4): triangles track its attacks.
					plugin.handleBeatKey();
				}
				if (t < dies && k % 4 == 3)
				{
					splatOnMe(0);
				}
				if (t == first + 30)
				{
					plugin.handleClick(ms + 200);
					plugin.handleEatClick(379);
				}
				if (t % 6 == 0 && prayers.getOrDefault(Prayer.PROTECT_FROM_MELEE, false))
				{
					prayerPoints--;
				}
				if (t == dies)
				{
					when(me.getInteracting()).thenReturn(null);
					when(npc.getInteracting()).thenReturn(null);
					when(npc.isDead()).thenReturn(true);
					plugin.handleNpcDespawned(npc);
					overhead(Prayer.PROTECT_FROM_MELEE, "melee", false);
				}
				if (t > dies + 2 && t < dies + 8)
				{
					walk(2);
				}
			});
		}
		finish();
	}

	/** Pickpocketing a man: click, stun (he punches you, you take a 1), keep going. No Attack click. */
	private void pickpocket() throws Exception
	{
		for (int i = 0; i < 40; i++)
		{
			tick(t ->
			{
				if (t % 3 == 0)
				{
					plugin.handleClick(ms + 150);
					plugin.handlePickpocketClick(7);
					when(me.getInteracting()).thenReturn(npc);
					when(me.getAnimation()).thenReturn(881);
				}
				else
				{
					when(me.getAnimation()).thenReturn(-1);
				}
				if (t == 12 || t == 27)
				{
					when(npc.getInteracting()).thenReturn(me);
					npcSwing();
				}
				if (t == 13 || t == 28)
				{
					splatOnMe(1);
					when(npc.getInteracting()).thenReturn(null);
				}
			});
		}
		finish();
	}

	/**
	 * Standing in a slayer task, auto-retaliate on, no clicks at all: the monster walks up and swings; you hit
	 * back automatically, missing a lot. Mouse and keyboard stay untouched the whole time (idle grows).
	 */
	private void afkRetaliate() throws Exception
	{
		plugin.handleStat("Strength", strXp);
		final int[] idle = {0};
		when(client.getMouseIdleTicks()).thenAnswer(i -> idle[0]);
		when(client.getKeyboardIdleTicks()).thenAnswer(i -> idle[0]);
		int[] mine = {0, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 11, 0, 0, 0, 0, 0};
		for (int i = 0; i < 4 + mine.length * 4 + 8; i++)
		{
			tick(t ->
			{
				idle[0] += 30; // 0.6 s in client ticks (50 a second)
				if (t == 3)
				{
					when(npc.getInteracting()).thenReturn(me);
				}
				int k = t - 4;
				if (k >= 0 && k % 4 == 0)
				{
					npcSwing();
				}
				if (k >= 0 && k % 4 == 1)
				{
					splatOnMe(k % 12 == 1 ? 3 : 0);
					when(me.getInteracting()).thenReturn(npc);
				}
				if (k >= 1 && k % 4 == 2 && k / 4 < mine.length)
				{
					mySwing();
				}
				else
				{
					myStop();
				}
				if (k >= 1 && k % 4 == 3 && k / 4 < mine.length)
				{
					splatOnNpc(mine[k / 4]);
				}
			});
		}
		finish();
	}

	/**
	 * Chopping yews; a monster walks up and attacks; you auto-retaliate (no clicks) until it dies; then you click
	 * the tree again and keep chopping.
	 */
	private void chopFightChop() throws Exception
	{
		int yew = 1515;
		final int[] logs = {6};
		plugin.handleStat("Woodcutting", 1_000_000);
		plugin.handleStat("Strength", strXp);
		plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
		int fightFrom = 20, fightTo = 52;
		for (int i = 0; i < 80; i++)
		{
			tick(t ->
			{
				boolean fighting = t >= fightFrom && t < fightTo;
				if (t == 1 || t == fightTo + 2)
				{
					plugin.handleClick(ms + 120);
					plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(50, 50), "Yew tree");
					when(me.getInteracting()).thenReturn(null);
				}
				if (!fighting && t >= 2 && !(t >= fightTo && t < fightTo + 3))
				{
					when(me.getAnimation()).thenReturn(867);
					if (t % 7 == 0)
					{
						logs[0]++;
						plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
						plugin.handleStat("Woodcutting", 1_000_000 + 175 * (logs[0] - 6));
					}
				}
				if (fighting)
				{
					int k = t - fightFrom;
					if (k == 0)
					{
						when(npc.getInteracting()).thenReturn(me);
					}
					if (k % 4 == 0)
					{
						npcSwing();
					}
					if (k % 4 == 1)
					{
						splatOnMe(k % 8 == 1 ? 4 : 0);
						when(me.getInteracting()).thenReturn(npc);
					}
					if (k % 4 == 2)
					{
						mySwing();
					}
					else
					{
						myStop();
					}
					if (k % 4 == 3)
					{
						splatOnNpc(k % 12 == 3 ? 0 : 9);
					}
				}
				if (t == fightTo)
				{
					when(me.getInteracting()).thenReturn(null);
					when(npc.getInteracting()).thenReturn(null);
					when(npc.isDead()).thenReturn(true);
					plugin.handleNpcDespawned(npc);
					myStop();
				}
			});
		}
		finish();
	}

	@Test
	public void replayChopFightChop52() throws Exception
	{
		setUp("chop-fight-chop", 52);
		chopFightChop();
	}

	/**
	 * A monster walks up and attacks you every 4 ticks, mostly missing; you never swing back (auto-retaliate off).
	 * Metronome set to every 4 ticks so the next-beat marker shows.
	 */
	private void attackedOnly() throws Exception
	{
		for (int i = 0; i < 44; i++)
		{
			tick(t ->
			{
				if (t == 3)
				{
					when(npc.getInteracting()).thenReturn(me);
				}
				int k = t - 4;
				if (k >= 0 && k < 32 && k % 4 == 0)
				{
					npcSwing();
				}
				if (k >= 0 && k < 32 && k % 4 == 1)
				{
					splatOnMe(k % 12 == 1 ? 4 : 0);
				}
			});
		}
		finish();
	}

	/**
	 * Standing still: Protect from Melee clicked on, then clicked off late in a tick so the game only turns it off
	 * a tick later than you'd expect. The pill should stop where the game turned it off and never shrink back.
	 */
	private void prayerLateOff() throws Exception
	{
		for (int i = 0; i < 26; i++)
		{
			tick(t ->
			{
				if (t == 5)
				{
					overhead(Prayer.PROTECT_FROM_MELEE, "melee", true);
				}
				if (t == 14)
				{
					plugin.handleClick(ms + 560);
					plugin.handlePrayerClick(ms + 560);
					plugin.handleOverheadClick(ms + 560, "melee");
				}
				if (t == 15)
				{
					prayers.put(Prayer.PROTECT_FROM_MELEE, false);
				}
			});
		}
		finish();
	}

	/**
	 * The five ideas from other tick plugins in one fight: ping 60 ms (a late click moves to the next tick), a walk,
	 * a gear switch, a spec and a drink, a lag tick (930 ms), and a weapon with no known speed (3t, learned).
	 */
	private void fiveIdeas(boolean flicking) throws Exception
	{
		overrides.put("pingShift", SuperHistogramConfig.PingShift.FIXED);
		overrides.put("pingFixedMs", 60);
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.weaponSpeedKnown = false;
		plugin.handleStat("Strength", strXp);
		plugin.handleStat("Hitpoints", 1_000_000);
		int first = 6;
		while (tick < 30)
		{
			if (tick == 20)
			{
				lagMs = 330;
			}
			tick(t ->
			{
				if (t == 2)
				{
					plugin.handleClick(ms + 200);
					plugin.handleInput(ms + 200, com.superhistogram.model.InputMark.Kind.WALK);
					walk(1);
				}
				if (t == 5)
				{
					attackClick();
				}
				if (t == 5 && flicking)
				{
					overhead(Prayer.PROTECT_FROM_MELEE, "melee", true);
				}
				int k = t - first;
				if (k >= 0 && k % 3 == 0)
				{
					mySwing();
				}
				else
				{
					myStop();
				}
				if (k >= 0 && k % 3 == 1)
				{
					splatOnNpc(k % 2 == 0 ? 0 : 6);
				}
				if (flicking && t > 6)
				{
					// 1-tick flick: off and back on early in each tick.
					plugin.handleClick(ms + 60);
					plugin.handlePrayerClick(ms + 60);
					plugin.handleClick(ms + 140);
					plugin.handlePrayerClick(ms + 140);
				}
				if (t == 9)
				{
					plugin.handleClick(ms + 300);
					plugin.handleInput(ms + 300, com.superhistogram.model.InputMark.Kind.GEAR);
				}
				if (t == 12)
				{
					plugin.handleClick(ms + 150);
					plugin.handleInput(ms + 150, com.superhistogram.model.InputMark.Kind.SPEC);
				}
				if (t == 15)
				{
					// 570 ms in + 60 ms ping: the server gets it after the next tick.
					plugin.handleClick(ms + 570);
				}
				if (t == 17)
				{
					plugin.handleClick(ms + 250);
					plugin.handleInput(ms + 250, com.superhistogram.model.InputMark.Kind.DRINK);
				}
			});
		}
		finish();
	}

	/** Scurrius with two of his rats hitting you between his own attacks. */
	private void scurrius() throws Exception
	{
		when(npc.getName()).thenReturn("Scurrius");
		when(npc.getCombatLevel()).thenReturn(250);
		NPC rat = mock(NPC.class);
		when(rat.getIndex()).thenReturn(9);
		when(rat.getName()).thenReturn("Giant rat");
		when(rat.getCombatLevel()).thenReturn(3);
		when(rat.getInteracting()).thenReturn(me);
		when(rat.getAnimation()).thenReturn(-1);
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Strength", strXp);
		plugin.handleStat("Hitpoints", 1_000_000);
		int first = 3;
		while (tick < 15)
		{
			tick(t ->
			{
				if (t == 2)
				{
					attackClick();
				}
				int k = t - first;
				if (k >= 0 && k % 4 == 0)
				{
					mySwing();
					npcSwing();
				}
				else
				{
					myStop();
				}
				if (k >= 0 && k % 4 == 1)
				{
					splatOnNpc(k % 8 == 1 ? 9 : 14);
					splatOnMe(k % 8 == 1 ? 7 : 0);
				}
				if (k >= 0 && k % 4 == 2)
				{
					when(rat.getAnimation()).thenReturn(4933);
					plugin.handleAnimation(rat);
					when(rat.getAnimation()).thenReturn(-1);
				}
				if (k >= 0 && k % 4 == 3)
				{
					splatOnMe(k % 8 == 3 ? 2 : 1);
				}
			});
		}
		finish();
	}

	@Test
	public void replayScurrius52() throws Exception
	{
		setUp("scurrius", 52);
		scurrius();
	}

	@Test
	public void replayScurriusRatsIgnored52() throws Exception
	{
		setUp("scurrius-ignored", 52);
		overrides.put("ignoreEnemies", "Giant rat");
		scurrius();
	}

	/** Chopping yews: a tree falls, you re-click a few ticks late, the inventory fills, you bank. */
	private void chopToFull() throws Exception
	{
		int yew = 1515;
		final int[] logs = {21};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Woodcutting", 1_000_000);
		plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
		final int[] tree = {SuperHistogramPlugin.objectKey(50, 50)};
		while (tick < 47)
		{
			tick(t ->
			{
				boolean idle = t >= 17 && t < 21;
				if (t == 1)
				{
					plugin.handleClick(ms + 120);
					plugin.handleGatherClick("Woodcutting", tree[0], "Yew tree");
				}
				if (t == 16)
				{
					// The tree falls with the log; you notice and click the next one 4 ticks later.
					plugin.handleObjectDespawned(50, 50);
				}
				if (t == 20)
				{
					plugin.handleClick(ms + 300);
					plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(52, 50), "Yew tree");
				}
				boolean chopping = t >= 2 && !idle && logs[0] < 28;
				when(me.getAnimation()).thenReturn(chopping ? 867 : -1);
				if (chopping && (t % 5 == 1 || t == 16))
				{
					logs[0]++;
					plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
					plugin.handleStat("Woodcutting", 1_000_000 + 175 * (logs[0] - 21));
				}
				if (t == 44)
				{
					plugin.handleClick(ms + 200);
					plugin.handleBankClick();
				}
			});
		}
		finish();
	}

	@Test
	public void replayChopToFull52() throws Exception
	{
		setUp("chop-to-full", 52);
		chopToFull();
	}

	/** Cooking sharks on a range: 10 raw, one burns, the run ends, a slow bank, then the next inventory. */
	private void cookAndBank() throws Exception
	{
		final int raw = 383, cooked = 385, burnt = 387;
		final int[] inv = {10, 0, 0};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Cooking", 3_000_000);
		final int[] xp = {3_000_000};
		java.util.function.Consumer<Void> send = v ->
		{
			Map<Integer, Integer> m = new HashMap<>();
			if (inv[0] > 0) m.put(raw, inv[0]);
			if (inv[1] > 0) m.put(cooked, inv[1]);
			if (inv[2] > 0) m.put(burnt, inv[2]);
			plugin.handleInventory(m, 28 - inv[0] - inv[1] - inv[2]);
		};
		send.accept(null);
		final int[] startAt = {3};
		final int[] made = {0};
		while (tick < 63)
		{
			tick(t ->
			{
				if (t == 2 || t == 52)
				{
					plugin.handleClick(ms + 150);
					startAt[0] = t + 1;
					made[0] = 0;
				}
				int k = t - startAt[0];
				if (inv[0] > 0 && k > 0 && k % 4 == 0 && t < 47 || (t >= 53 && inv[0] > 0 && k > 0 && k % 4 == 0))
				{
					inv[0]--;
					made[0]++;
					if (t < 47 && made[0] == 5)
					{
						inv[2]++;
					}
					else
					{
						inv[1]++;
						xp[0] += 210;
						plugin.handleStat("Cooking", xp[0]);
					}
					send.accept(null);
				}
				if (t == 48)
				{
					// Four ticks after the last shark before you click the bank.
					plugin.handleClick(ms + 300);
					plugin.handleBankClick();
				}
				if (t == 50)
				{
					inv[0] = 28;
					inv[1] = 0;
					inv[2] = 0;
					send.accept(null);
				}
			});
		}
		finish();
	}

	@Test
	public void replayCookAndBank52() throws Exception
	{
		setUp("cook-and-bank", 52);
		cookAndBank();
	}

	/** High-alching rune platebodies: a steady 5-tick rhythm, one cast 3 ticks late, then a pause. */
	private void alchRuns() throws Exception
	{
		int body = 1127;
		net.runelite.api.ItemComposition comp = mock(net.runelite.api.ItemComposition.class);
		when(comp.getHaPrice()).thenReturn(38_400);
		when(plugin.itemManager.canonicalize(body)).thenReturn(body);
		when(plugin.itemManager.getItemComposition(body)).thenReturn(comp);
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		// Ten minutes into the session, so the per-hour rates are showing.
		plugin.handleGameState(GameState.LOGIN_SCREEN, ms - 600_000);
		plugin.handleGameState(GameState.LOGGED_IN, ms - 600_000);
		final int[] magic = {2_000_000};
		plugin.handleStat("Magic", magic[0]);
		final java.util.Set<Integer> casts = new java.util.HashSet<>(java.util.Arrays.asList(
			2, 7, 12, 17, 25, 30, 35));
		while (tick < 47)
		{
			tick(t ->
			{
				if (casts.contains(t))
				{
					plugin.handleClick(ms + 180);
					plugin.handleCastClick(body);
				}
				if (casts.contains(t - 1))
				{
					magic[0] += 65;
					plugin.handleStat("Magic", magic[0]);
				}
			});
		}
		finish();
	}

	/** Alch three bodies, chop yews for a while, then alch three more: the big number starts again. */
	private void alchChopAlch() throws Exception
	{
		int body = 1127;
		int yew = 1515;
		net.runelite.api.ItemComposition comp = mock(net.runelite.api.ItemComposition.class);
		when(comp.getHaPrice()).thenReturn(38_400);
		when(plugin.itemManager.canonicalize(body)).thenReturn(body);
		when(plugin.itemManager.getItemComposition(body)).thenReturn(comp);
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleGameState(GameState.LOGIN_SCREEN, ms - 600_000);
		plugin.handleGameState(GameState.LOGGED_IN, ms - 600_000);
		final int[] magic = {2_000_000};
		plugin.handleStat("Magic", magic[0]);
		plugin.handleStat("Woodcutting", 1_000_000);
		final int[] logs = {10};
		plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
		final java.util.Set<Integer> casts = new java.util.HashSet<>(java.util.Arrays.asList(
			2, 7, 12, 34, 39, 44));
		while (tick < 47)
		{
			tick(t ->
			{
				if (casts.contains(t))
				{
					plugin.handleClick(ms + 180);
					plugin.handleCastClick(body);
				}
				if (casts.contains(t - 1))
				{
					magic[0] += 65;
					plugin.handleStat("Magic", magic[0]);
				}
				if (t == 15)
				{
					plugin.handleClick(ms + 120);
					plugin.handleGatherClick("Woodcutting", SuperHistogramPlugin.objectKey(50, 50), "Yew tree");
				}
				boolean chopping = t >= 16 && t < 31;
				when(me.getAnimation()).thenReturn(chopping ? 867 : -1);
				if (chopping && t % 5 == 1)
				{
					logs[0]++;
					plugin.handleInventory(Collections.singletonMap(yew, logs[0]), 28 - logs[0]);
					plugin.handleStat("Woodcutting", 1_000_000 + 175 * (logs[0] - 10));
				}
			});
		}
		finish();
	}

	@Test
	public void replayAlchChopAlch52() throws Exception
	{
		setUp("alch-chop-alch", 52);
		alchChopAlch();
	}

	/**
	 * Three laps of Seers' rooftop: lap 1 clean, lap 2 with a 4-tick hesitation before the 4th obstacle,
	 * lap 3 clean and quicker on the walk back (the best).
	 */
	private void seersLaps() throws Exception
	{
		int[] ids = com.superhistogram.ui.SampleStates.SEERS_IDS, dur = com.superhistogram.ui.SampleStates.SEERS_DUR;
		int[] xp = {30, 40, 25, 35, 60, 30};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		// Ten minutes into the session, so the per-hour rate is showing.
		plugin.handleGameState(GameState.LOGIN_SCREEN, ms - 600_000);
		plugin.handleGameState(GameState.LOGGED_IN, ms - 600_000);
		x = 2710;
		y = 3470;
		final int[] ag = {1_000_000};
		final int grace = net.runelite.api.gameval.ItemID.GRACE;
		final int[] marks = {12};
		plugin.handleStat("Agility", ag[0]);
		// click tick -> obstacle; landing tick -> obstacle
		final java.util.Map<Integer, Integer> clicks = new java.util.HashMap<>(), lands = new java.util.HashMap<>();
		final java.util.Set<Integer> backToStart = new java.util.HashSet<>();
		int t = 2;
		int[][] delays = {{0, 0, 0, 0, 0, 0}, {0, 0, 0, 4, 0, 0}, {0, 0, 0, 0, 0, 0}};
		int[] walk = {12, 12, 8};
		for (int lap = 0; lap < 3; lap++)
		{
			for (int k = 0; k < 6; k++)
			{
				t += delays[lap][k];
				clicks.put(t, k);
				lands.put(t + dur[k], k);
				t += dur[k];
			}
			t += walk[lap];
			backToStart.add(t - 2);
		}
		final int end = t + 6;
		while (tick < end)
		{
			tick(tt ->
			{
				Integer c = clicks.get(tt);
				if (c != null)
				{
					plugin.handleClick(ms + 150);
					plugin.handleObstacleClick(ids[c]);
				}
				Integer l = lands.get(tt);
				if (l != null)
				{
					if (l == 5)
					{
						x = 2704;
						y = 3464;
					}
					ag[0] += xp[l];
					plugin.handleStat("Agility", ag[0]);
				}
				if (backToStart.contains(tt))
				{
					x = 2710;
					y = 3470;
				}
				if (tt == 1 || tt == 30 || tt == 150)
				{
					// Baseline inventory, then a mark picked up on lap 1 and on lap 3.
					marks[0] += tt == 1 ? 0 : 1;
					plugin.handleInventory(Collections.singletonMap(grace, marks[0]), 20);
				}
			});
		}
		finish();
	}

	@Test
	public void replaySeers52() throws Exception
	{
		setUp("seers", 52);
		seersLaps();
	}

	@Test
	public void replayAlch52() throws Exception
	{
		setUp("alch", 52);
		alchRuns();
	}

	@Test
	public void replayFiveIdeas52() throws Exception
	{
		setUp("five-ideas", 52);
		fiveIdeas(false);
	}

	@Test
	public void replayFiveIdeasOneTick52() throws Exception
	{
		setUp("five-ideas-onetick", 52);
		overrides.put("mode", com.superhistogram.model.ModeSetting.ONE_TICK);
		overrides.put("prayerMode", PrayerMode.ONE_TICK);
		fiveIdeas(true);
	}

	@Test
	public void replayPrayerLateOff52() throws Exception
	{
		setUp("prayer-late-off", 52);
		prayerLateOff();
	}

	@Test
	public void replayAttackedOnlyAlternate52() throws Exception
	{
		setUp("attacked-only-alternate", 52);
		overrides.put("metronome", SuperHistogramConfig.Metronome.CUE_TICK);
		overrides.put("metronomeStyle", SuperHistogramConfig.MetronomeStyle.ALTERNATE);
		attackedOnly();
	}

	@Test
	public void replayAttackedOnly52() throws Exception
	{
		setUp("attacked-only", 52);
		overrides.put("metronome", SuperHistogramConfig.Metronome.CUE_TICK);
		attackedOnly();
	}

	@Test
	public void replayAfkRetaliate52() throws Exception
	{
		setUp("afk-retaliate", 52);
		afkRetaliate();
	}

	@Test
	public void replayFight52() throws Exception
	{
		setUp("fight", 52);
		fight();
	}

	@Test
	public void replayFight44() throws Exception
	{
		setUp("fight", 44);
		fight();
	}

	@Test
	public void replayFight62() throws Exception
	{
		setUp("fight", 62);
		fight();
	}

	@Test
	public void replayPickpocket52() throws Exception
	{
		setUp("pickpocket", 52);
		pickpocket();
	}

	/**
	 * Ardougne knights: a Pickpocket click every 2 ticks, the XP the tick after the game acts on it; two failures
	 * stun you (a 1 hit, 8 ticks you can't pickpocket). Start at 14 pouches; at 28 the game says to empty them,
	 * you open them all (a click, the pouches go) and carry on.
	 */
	private void ardyKnights() throws Exception
	{
		int pouch = net.runelite.api.gameval.ItemID.PICKPOCKET_COIN_POUCH_KNIGHT;
		final int[] pouches = {14};
		final int[] xp = {3_000_000};
		final int[] next = {1};
		final int[] pending = {-1};
		final int[] attempts = {0};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		when(npc.getName()).thenReturn("Knight of Ardougne");
		plugin.handleStat("Thieving", xp[0]);
		plugin.handleInventory(Collections.singletonMap(pouch, pouches[0]), 27);
		while (tick < 64)
		{
			tick(t ->
			{
				if (pending[0] >= 0 && t == pending[0] + 1)
				{
					// The outcome, the tick after the game acted on the click.
					boolean fail = attempts[0] == 6 || attempts[0] == 19;
					if (fail)
					{
						splatOnMe(1);
						next[0] = t + 8;
					}
					else
					{
						pouches[0]++;
						xp[0] += 84;
						plugin.handleInventory(Collections.singletonMap(pouch, pouches[0]), 27);
						plugin.handleStat("Thieving", xp[0]);
						next[0] = t;
					}
					pending[0] = -1;
				}
				if (pouches[0] >= 28 && t == next[0])
				{
					plugin.handleChatMessage("You need to empty your coin pouches before you can continue pickpocketing.");
					next[0] = t + 3;
					return;
				}
				if (pouches[0] >= 28 && t == next[0] - 1)
				{
					plugin.handleClick(ms + 200);
					pouches[0] = 0;
					plugin.handleInventory(Collections.emptyMap(), 28);
					return;
				}
				if (t == next[0] && pending[0] < 0)
				{
					plugin.handleClick(ms + 150);
					plugin.handlePickpocketClick(7);
					plugin.handleThieveClick("Knight of Ardougne", false);
					when(me.getInteracting()).thenReturn(npc);
					when(me.getAnimation()).thenReturn(881);
					pending[0] = t + 1;
					attempts[0]++;
				}
				else
				{
					when(me.getAnimation()).thenReturn(-1);
				}
			});
		}
		finish();
	}

	@Test
	public void replayArdyKnights52() throws Exception
	{
		setUp("ardy-knights", 52);
		ardyKnights();
	}
	/**
	 * Black chinchompas, level 80 (five traps): lay five box traps two ticks apart, then they catch (or fail)
	 * one by one; you Reset each full or failed one (a catch gives XP, the trap is set again on the same tick).
	 */
	private void chins() throws Exception
	{
		int empty = net.runelite.api.gameval.ObjectID.HUNTING_BOXTRAP_EMPTY;
		int full = net.runelite.api.gameval.ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BIG;
		int failed = net.runelite.api.gameval.ObjectID.HUNTING_BOXTRAP_FAILED;
		int[][] tiles = {{50, 50}, {52, 50}, {54, 50}, {50, 52}, {52, 52}};
		final int[] xp = {2_000_000};
		final int[] at = {50, 50};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		when(me.getLocalLocation()).thenAnswer(i -> net.runelite.api.coords.LocalPoint.fromScene(at[0], at[1]));
		plugin.handleStat("Hunter", xp[0]);
		// tick -> {trap, new state (0 set, 1 full, 2 failed)}
		int[][] changes = {{16, 0, 1}, {19, 2, 2}, {22, 1, 1}, {27, 3, 1}, {33, 4, 1}, {40, 0, 1}, {44, 2, 1},
			{49, 1, 2}, {52, 3, 1}, {58, 4, 1}};
		// tick -> trap you Reset (acted next tick, done two ticks after the click)
		int[][] resets = {{18, 0}, {22, 2}, {25, 1}, {30, 3}, {36, 4}, {42, 0}, {47, 2}, {51, 1}, {55, 3}, {61, 4}};
		final boolean[] wasFull = new boolean[5];
		while (tick < 66)
		{
			tick(t ->
			{
				if (t <= 9 && t % 2 == 1)
				{
					int k = t / 2;
					at[0] = tiles[k][0];
					at[1] = tiles[k][1];
					plugin.handleClick(ms + 150);
					plugin.handleLayClick();
					when(me.getAnimation()).thenReturn(5208);
				}
				else if (t >= 2 && t <= 11 && t % 2 == 0)
				{
					int k = t / 2 - 1;
					plugin.handleObjectSpawned(empty, tiles[k][0], tiles[k][1]);
					when(me.getAnimation()).thenReturn(-1);
				}
				for (int[] c : changes)
				{
					if (c[0] == t)
					{
						plugin.handleObjectDespawned(tiles[c[1]][0], tiles[c[1]][1]);
						plugin.handleObjectSpawned(c[2] == 1 ? full : failed, tiles[c[1]][0], tiles[c[1]][1]);
						wasFull[c[1]] = c[2] == 1;
					}
				}
				for (int[] r : resets)
				{
					int k = r[1];
					if (r[0] == t)
					{
						plugin.handleClick(ms + 200);
						plugin.handleTrapClick(SuperHistogramPlugin.objectKey(tiles[k][0], tiles[k][1]),
							wasFull[k] ? full : failed);
					}
					if (r[0] + 1 == t)
					{
						// The game acts on the click: the reset animation starts.
						when(me.getAnimation()).thenReturn(5212);
					}
					if (r[0] + 2 == t)
					{
						if (wasFull[k])
						{
							xp[0] += 315;
							plugin.handleStat("Hunter", xp[0]);
						}
						plugin.handleObjectDespawned(tiles[k][0], tiles[k][1]);
						plugin.handleObjectSpawned(empty, tiles[k][0], tiles[k][1]);
						when(me.getAnimation()).thenReturn(-1);
					}
				}
			});
		}
		finish();
	}

	@Test
	public void replayChins52() throws Exception
	{
		setUp("chins", 52);
		chins();
	}

	/**
	 * Salvaging a shipwreck: a Sailing XP drop from the boat first (not salvage: ignored); the hook pulls salvage
	 * every 4 ticks from 20 to a full 28; a few idle ticks; then you sort it at a salvaging station (salvage
	 * leaves, planks come in, Sailing XP): production.
	 */
	private void salvage() throws Exception
	{
		int salvage = net.runelite.api.gameval.ItemID.SAILING_LARGE_SHIPWRECK_SALVAGE;
		int plank = 960;
		final int[] n = {20};
		final int[] planks = {0};
		final int[] xp = {1_500_000};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Sailing", xp[0]);
		java.util.function.Consumer<Void> inv = v ->
		{
			Map<Integer, Integer> m = new HashMap<>();
			m.put(salvage, n[0]);
			if (planks[0] > 0)
			{
				m.put(plank, planks[0]);
			}
			plugin.handleInventory(m, 28 - n[0] - (planks[0] > 0 ? 1 : 0));
		};
		inv.accept(null);
		while (tick < 70)
		{
			tick(t ->
			{
				if (t == 2)
				{
					xp[0] += 40;
					plugin.handleStat("Sailing", xp[0]);
				}
				boolean hooking = t >= 4 && n[0] < 28 && t < 40;
				when(me.getAnimation()).thenReturn(hooking ? 13596 : t >= 44 && n[0] > 0 ? 13593 : -1);
				if (hooking && t % 4 == 0)
				{
					n[0]++;
					inv.accept(null);
					xp[0] += 180;
					plugin.handleStat("Sailing", xp[0]);
				}
				if (t == 43)
				{
					plugin.handleClick(ms + 200);
				}
				if (t >= 45 && n[0] > 0 && t % 2 == 1)
				{
					n[0]--;
					planks[0]++;
					inv.accept(null);
					xp[0] += 25;
					plugin.handleStat("Sailing", xp[0]);
				}
			});
		}
		finish();
	}

	@Test
	public void replaySalvage52() throws Exception
	{
		setUp("salvage", 52);
		salvage();
	}

	/**
	 * Plank Make on mahogany logs: a cast every 3 ticks, two of them late (2 and 4 ticks); then a bank trip; then
	 * Spin Flax (cast on nothing, 5 flax a cast) every 5 ticks.
	 */
	private void spells() throws Exception
	{
		int logs = 6332, plank = 8782, astral = 9075, flax = 1779, bow = 1777;
		final int[] l = {25}, p = {0}, f = {0}, b = {0};
		final int[] xp = {8_000_000};
		final int[] next = {2};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Magic", xp[0]);
		Runnable inv = () ->
		{
			Map<Integer, Integer> m = new HashMap<>();
			m.put(astral, 500);
			if (l[0] > 0) m.put(logs, l[0]);
			if (p[0] > 0) m.put(plank, p[0]);
			if (f[0] > 0) m.put(flax, f[0]);
			if (b[0] > 0) m.put(bow, b[0]);
			plugin.handleInventory(m, 27 - l[0] - p[0]);
		};
		inv.run();
		final int[] pending = {-1};
		final boolean[] flaxing = {false};
		while (tick < 96)
		{
			tick(t ->
			{
				if (pending[0] >= 0 && t == pending[0])
				{
					if (flaxing[0])
					{
						f[0] -= 5;
						b[0] += 5;
						xp[0] += 75;
					}
					else
					{
						l[0]--;
						p[0]++;
						xp[0] += 90;
					}
					inv.run();
					plugin.handleStat("Magic", xp[0]);
					pending[0] = -1;
				}
				if (!flaxing[0] && l[0] == 0 && t == next[0])
				{
					// Bank: planks out, flax in.
					plugin.handleClick(ms + 200);
					p[0] = 0;
					f[0] = 25;
					flaxing[0] = true;
					inv.run();
					next[0] = t + 6;
					return;
				}
				if (t == next[0] && (flaxing[0] ? f[0] > 0 : l[0] > 0))
				{
					plugin.handleClick(ms + 150);
					if (flaxing[0])
					{
						plugin.handleSpellClick("Spin Flax", 0);
					}
					else
					{
						plugin.handleSpellClick("Plank Make", logs);
					}
					pending[0] = t + 1;
					int casts = flaxing[0] ? 0 : 25 - l[0];
					next[0] = t + (flaxing[0] ? 5 : casts == 6 ? 5 : casts == 14 ? 7 : 3);
				}
				when(me.getAnimation()).thenReturn(pending[0] == t + 1 ? -1 : pending[0] >= 0 ? 6298 : -1);
			});
		}
		finish();
	}

	@Test
	public void replaySpells52() throws Exception
	{
		setUp("spells", 52);
		spells();
	}

	/**
	 * Oak larders with a demon butler: 24 oak planks = 3 larders (8 each). Build (planks leave, 480 XP two ticks
	 * after the click), remove (3 ticks), build again; when out, the butler brings 24 more after a few ticks.
	 */
	private void larders() throws Exception
	{
		int oak = 8778;
		final int[] planks = {24};
		final int[] xp = {5_000_000};
		final int[] next = {2};
		final int[] butlerAt = {-1};
		final int[] trips = {0};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Construction", xp[0]);
		Runnable inv = () -> plugin.handleInventory(planks[0] > 0 ? Collections.singletonMap(oak, planks[0])
			: Collections.emptyMap(), 28 - planks[0]);
		inv.run();
		while (tick < 80)
		{
			tick(t ->
			{
				if (t == butlerAt[0])
				{
					planks[0] = 24;
					inv.run();
					butlerAt[0] = -1;
					next[0] = t + 2;
				}
				if (t == next[0] && planks[0] >= 8)
				{
					plugin.handleClick(ms + 150);
					when(me.getAnimation()).thenReturn(3676);
				}
				if (t == next[0] + 2 && planks[0] >= 8)
				{
					planks[0] -= 8;
					inv.run();
					xp[0] += 480;
					plugin.handleStat("Construction", xp[0]);
					when(me.getAnimation()).thenReturn(-1);
					if (planks[0] < 8)
					{
						// Call the butler; the second trip is slow.
						plugin.handleClick(ms + 300);
						butlerAt[0] = t + (trips[0]++ == 1 ? 12 : 6);
						next[0] = -1;
					}
					else
					{
						// Remove the larder, then build the next.
						next[0] = t + 3;
					}
				}
			});
		}
		finish();
	}

	@Test
	public void replayLarders52() throws Exception
	{
		setUp("larders", 52);
		larders();
	}

	/**
	 * Runecraft trips: bank, run to the altar, craft 28 pure essence (+280 XP), empty the pouches and craft
	 * again 3 ticks later (+120), then teleport back. A trip every ~40 ticks, one slower trip.
	 */
	private void runecraft() throws Exception
	{
		int pure = 7936;
		final int[] ess = {28};
		final int[] xp = {2_000_000};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Runecraft", xp[0]);
		Runnable inv = () -> plugin.handleInventory(ess[0] > 0 ? Collections.singletonMap(pure, ess[0])
			: Collections.emptyMap(), 28 - Math.min(28, ess[0]));
		inv.run();
		int[] crafts = {30, 70, 118, 158};
		IntConsumer ev = t ->
		{
			for (int c : crafts)
			{
				if (t == c - 1)
				{
					plugin.handleClick(ms + 200);
				}
				if (t == c || t == c + 3)
				{
					ess[0] = t == c ? 12 : 0;
					inv.run();
					xp[0] += t == c ? 280 : 120;
					plugin.handleStat("Runecraft", xp[0]);
					when(me.getAnimation()).thenReturn(791);
				}
				if (t == c + 5)
				{
					when(me.getAnimation()).thenReturn(-1);
				}
				if (t == c + 20)
				{
					// Banked: a fresh inventory and full pouches.
					ess[0] = 28;
					inv.run();
				}
			}
		};
		skip(60, ev);
		while (tick < 176)
		{
			tick(ev);
		}
		finish();
	}

	@Test
	public void replayRunecraft52() throws Exception
	{
		setUp("runecraft", 52);
		runecraft();
	}

	/**
	 * A herb run: five patches last time (skipped, an hour and more ago), now the third patch of this run. Each
	 * patch is harvest drops every 2 ticks, compost and plant, then a teleport to the next one (~45 ticks).
	 */
	private void herbRun() throws Exception
	{
		final int[] xp = {1_500_000};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Farming", xp[0]);
		IntConsumer patches = t ->
		{
			int at = t % 1000;
			int into = at % 60;
			if (at < 300 && (into < 14 && into % 2 == 0 || into == 18 || into == 21))
			{
				if (into == 0)
				{
					plugin.handleClick(ms + 150);
				}
				xp[0] += into < 14 ? 27 : into == 18 ? 18 : 27;
				plugin.handleStat("Farming", xp[0]);
				when(me.getAnimation()).thenReturn(into < 14 ? 2282 : 2291);
			}
			else if (into == 23)
			{
				when(me.getAnimation()).thenReturn(-1);
			}
		};
		// Last run: five patches, then a long wait.
		skip(1000, patches);
		// This run: three patches drawn, from the second on.
		skip(40, patches);
		while (tick < 1000 + 40 + 150)
		{
			tick(patches);
		}
		finish();
	}

	@Test
	public void replayHerbRun52() throws Exception
	{
		setUp("herbrun", 52);
		herbRun();
	}

	/**
	 * Wintertodt: a 5-minute round (skipped), the lobby, then a faster second round shown from its last minute to
	 * the kill count message and the wait after. XP every 3 ticks: chop, fletch, feed.
	 */
	private void wintertodt() throws Exception
	{
		x = 1632;
		y = 4000;
		final int[] xp = {3_000_000, 3_000_000, 3_000_000};
		final String[] skills = {"Woodcutting", "Fletching", "Firemaking"};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		for (int i = 0; i < 3; i++)
		{
			plugin.handleStat(skills[i], xp[i]);
		}
		IntConsumer ev = t ->
		{
			boolean in = t >= 10 && t <= 510 || t >= 600 && t <= 1000;
			if (in && t % 3 == 0)
			{
				int k = t / 30 % 3;
				xp[k] += k == 2 ? 300 : 40;
				plugin.handleStat(skills[k], xp[k]);
				when(me.getAnimation()).thenReturn(k == 0 ? 879 : k == 1 ? 1248 : 832);
			}
			if (t == 511 || t == 1001)
			{
				when(me.getAnimation()).thenReturn(-1);
				plugin.handleChatMessage("Your subdued Wintertodt count is: <col=ff0000>12</col>.");
			}
		};
		skip(960, ev);
		while (tick < 1080)
		{
			tick(ev);
		}
		finish();
	}

	@Test
	public void replayWintertodt52() throws Exception
	{
		setUp("wintertodt", 52);
		wintertodt();
	}

	/**
	 * Tempoross: a round shown from its last minute (fishing and cooking every few ticks, points rising) to the
	 * kill count message, then the boat back. The game's points-this-round value drives the headline.
	 */
	private void tempoross() throws Exception
	{
		x = 3040;
		y = 2976;
		final int[] xp = {4_000_000};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Fishing", xp[0]);
		IntConsumer ev = t ->
		{
			if (t >= 10 && t <= 520 && t % 4 == 0)
			{
				xp[0] += 120;
				plugin.handleStat("Fishing", xp[0]);
				varbits.merge(net.runelite.api.gameval.VarbitID.TEMPOROSS_CURRENT_POINTS, 15, Integer::sum);
				when(me.getAnimation()).thenReturn(t % 40 < 30 ? 623 : 896);
			}
			if (t == 521)
			{
				when(me.getAnimation()).thenReturn(-1);
				plugin.handleChatMessage("Your Tempoross kill count is: <col=ff0000>41</col>.");
			}
			if (t == 524)
			{
				varbits.put(net.runelite.api.gameval.VarbitID.TEMPOROSS_CURRENT_POINTS, 0);
			}
		};
		skip(480, ev);
		while (tick < 560)
		{
			tick(ev);
		}
		finish();
	}

	@Test
	public void replayTempoross52() throws Exception
	{
		setUp("tempoross", 52);
		tempoross();
	}

	/** Volcanic Mine: mining with points rising and the mine's stability sliding from 100% to the 50s. */
	private void volcanic() throws Exception
	{
		x = 3808;
		y = 10208;
		final int[] xp = {6_000_000};
		overrides.put("metronome", SuperHistogramConfig.Metronome.OFF);
		plugin.handleStat("Mining", xp[0]);
		varbits.put(net.runelite.api.gameval.VarbitID.FOSSIL_MINE_CURRENT_STABILITY, 100);
		IntConsumer ev = t ->
		{
			if (t >= 10 && t % 3 == 0)
			{
				xp[0] += 95;
				plugin.handleStat("Mining", xp[0]);
				varbits.merge(net.runelite.api.gameval.VarbitID.FOSSIL_MINE_TEMP_PTS, 4, Integer::sum);
				when(me.getAnimation()).thenReturn(7139);
			}
			if (t % 25 == 0)
			{
				varbits.merge(net.runelite.api.gameval.VarbitID.FOSSIL_MINE_CURRENT_STABILITY, -2, Integer::sum);
			}
		};
		skip(500, ev);
		while (tick < 548)
		{
			tick(ev);
		}
		finish();
	}

	@Test
	public void replayVolcanic52() throws Exception
	{
		setUp("volcanic", 52);
		volcanic();
	}

	/**
	 * A fire giant task: 14 left, a kill every 9 ticks (two swings), each kill +111 Slayer XP and one fewer left.
	 * The readout swaps hits-this-fight for monsters left and where your Slayer level lands at task end.
	 */
	private void slayerTask() throws Exception
	{
		when(npc.getName()).thenReturn("Fire giant");
		when(npc.getCombatLevel()).thenReturn(86);
		final int[] slayerXp = {3_500_000};
		plugin.handleStat("Strength", strXp);
		plugin.handleStat("Hitpoints", 1_000_000);
		plugin.handleStat("Slayer", slayerXp[0]);
		int count = net.runelite.api.gameval.VarPlayerID.SLAYER_COUNT;
		varps.put(count, 14);
		tick(t -> attackClick());
		int first = tick + 1;
		while (tick < 64)
		{
			tick(t ->
			{
				int k = (t - first) % 9;
				if (k == 0)
				{
					when(npc.isDead()).thenReturn(false);
					attackClick();
				}
				if (k == 1 || k == 5)
				{
					mySwing();
				}
				else
				{
					myStop();
				}
				if (k == 2 || k == 6)
				{
					splatOnNpc(k == 2 ? 38 : 0);
				}
				if (k == 3)
				{
					npcSwing();
				}
				if (k == 4)
				{
					splatOnMe(0);
				}
				if (k == 7)
				{
					splatOnNpc(73);
					slayerXp[0] += 111;
					plugin.handleStat("Slayer", slayerXp[0]);
					varps.merge(count, -1, Integer::sum);
					when(me.getInteracting()).thenReturn(null);
					when(npc.getInteracting()).thenReturn(null);
					when(npc.isDead()).thenReturn(true);
					plugin.handleNpcDespawned(npc);
				}
			});
		}
		finish();
	}

	@Test
	public void replaySlayer52() throws Exception
	{
		setUp("slayer", 52);
		slayerTask();
	}
}
