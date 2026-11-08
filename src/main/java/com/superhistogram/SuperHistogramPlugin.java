package com.superhistogram;

import com.google.inject.Provides;
import com.superhistogram.collect.ClickCollector;
import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.IdleTracker;
import com.superhistogram.model.Mode;
import com.superhistogram.model.ModeDetector;
import com.superhistogram.model.MovementTracker;
import com.superhistogram.model.SessionClock;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;
import com.superhistogram.collect.EatCollector;
import com.superhistogram.collect.PrayerCollector;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.ui.BarOverlay;
import com.superhistogram.ui.Pulse;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.VarClientStr;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.input.KeyManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Super Histogram"
)
public class SuperHistogramPlugin extends Plugin
{
	static final int TIMELINE_TICKS = 512;
	/** A jump of more than this many tiles in one tick is a teleport, not movement. */
	static final int MAX_STEP_TILES = 2;
	static final int EAT_ANIMATION = AnimationID.CONSUMING;

	@Inject
	Client client;

	@Inject
	OverlayManager overlayManager;

	@Inject
	SuperHistogramConfig config;

	@Inject
	ItemManager itemManager;

	@Inject
	KeyManager keyManager;

	@Inject
	ClientThread clientThread;

	private final ModeDetector modeDetector = new ModeDetector();

	// Widget groups of the prayer book and the minimap orbs (where quick-prayer is toggled).
	private static final int PRAYERBOOK_GROUP = net.runelite.api.gameval.InterfaceID.PRAYERBOOK, ORBS_GROUP = net.runelite.api.gameval.InterfaceID.ORBS;

	// Replaced wholesale on start/stop, read by the render thread.
	volatile Timeline timeline;
	volatile XpTracker xp;
	volatile IdleTracker idle;
	volatile SessionClock clock;
	private MovementTracker movement;
	private ClickCollector clicks;
	volatile AttackTimer attack;
	private EatCollector eats;
	volatile int weaponSpeed = AttackTimer.DEFAULT_SPEED;
	volatile Beat beat;
	private PrayerCollector prayers;
	volatile FlickClassifier flicks;
	private int lastHitTick = AttackTimer.NEVER;
	Pulse pulse;
	private final HotkeyListener beatHotkey = new HotkeyListener(() -> config.beatKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(SuperHistogramPlugin.this::handleBeatKey);
		}
	};
	private BarOverlay overlay;

	/** Own tick counter: unlike the client's, it never goes backwards across logins. */
	private int tick = -1;
	private int idleThreshold;
	private WorldPoint lastLocation;
	private volatile int runEnergyPct;
	/** Set from game-state events so the render thread never calls the client. */
	private volatile boolean loggedIn;

	@Override
	protected void startUp() throws Exception
	{
		initModel();
		pulse = new Pulse();
		overlay = new BarOverlay(this::buildState, pulse);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(beatHotkey);
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(beatHotkey);
		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}
	}

	/** Creates fresh model objects. Package-private for tests. */
	void initModel()
	{
		timeline = new Timeline(TIMELINE_TICKS);
		xp = new XpTracker(timeline);
		clock = new SessionClock();
		movement = new MovementTracker(timeline);
		clicks = new ClickCollector(timeline);
		resetCombat();
		beat = new Beat();
		prayers = new PrayerCollector(timeline);
		flicks = new FlickClassifier();
		lastHitTick = AttackTimer.NEVER;
		idleThreshold = config.afkAfterSeconds();
		idle = new IdleTracker(idleThreshold);
		if (pulse == null)
		{
			pulse = new Pulse();
		}
		tick = -1;
		lastLocation = null;
		runEnergyPct = 0;
		loggedIn = client != null && client.getGameState() == GameState.LOGGED_IN;
		if (loggedIn)
		{
			clock.onLoggedIn(System.currentTimeMillis());
		}
	}

	private void resetCombat()
	{
		attack = new AttackTimer();
		eats = new EatCollector(timeline, attack);
		weaponSpeed = AttackTimer.DEFAULT_SPEED;
	}

	// ---- event subscriptions: thin wrappers over the testable handlers below ----

	@Subscribe
	public void onGameTick(GameTick e)
	{
		handleTick(System.currentTimeMillis());
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		handleStat(e.getSkill().getName(), e.getXp());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		handleHit(e.getActor(), e.getHitsplat().getHitsplatType(), e.getHitsplat().getAmount());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		long now = System.currentTimeMillis();
		handleClick(now);
		int group = e.getWidgetId() >>> 16;
		String option = e.getMenuOption();
		if ((group == PRAYERBOOK_GROUP || group == ORBS_GROUP)
			&& ("Activate".equals(option) || "Deactivate".equals(option)))
		{
			handlePrayerClick(now);
		}
		if ("Eat".equals(e.getMenuOption()))
		{
			handleEatClick(e.getItemId());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		handleAnimation(e.getActor());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		ItemContainer c = e.getItemContainer();
		if (c == null)
		{
			return;
		}
		if (e.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			Item w = c.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
			handleWeapon(w == null ? -1 : w.getId());
		}
		else if (e.getContainerId() == InventoryID.INVENTORY.getId())
		{
			Map<Integer, Integer> counts = new HashMap<>();
			for (Item i : c.getItems())
			{
				if (i != null && i.getId() > 0)
				{
					counts.merge(i.getId(), i.getQuantity(), Integer::sum);
				}
			}
			handleInventory(counts);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		handleGameState(e.getGameState(), System.currentTimeMillis());
	}

	// ---- handlers ----

	void handleTick(long nowMs)
	{
		if (timeline == null)
		{
			return;
		}
		tick++;
		timeline.at(tick);
		clicks.onTick(nowMs);
		String overhead = PrayerCollector.overheadKind(client);
		prayers.onTick(tick, overhead);
		TickRecord prev = timeline.peek(tick - 1);
		if (prev != null && flicks != null)
		{
			prev.flick = flicks.classify(prev.prayerClicks, overhead != null);
			prev.flickPhase = flicks.phase();
		}
		beat.onTick(tick, overhead != null);

		Player me = client.getLocalPlayer();
		if (me != null)
		{
			WorldPoint here = me.getWorldLocation();
			int tiles = 0;
			if (here != null && lastLocation != null && here.getPlane() == lastLocation.getPlane())
			{
				int d = Math.max(Math.abs(here.getX() - lastLocation.getX()), Math.abs(here.getY() - lastLocation.getY()));
				tiles = d > MAX_STEP_TILES ? 0 : d;
			}
			lastLocation = here;
			runEnergyPct = Math.max(0, Math.min(100, client.getEnergy() / 100));
			movement.onTick(tick, tiles, me.getAnimation() != -1, runEnergyPct);
		}

		if (config.afkAfterSeconds() != idleThreshold)
		{
			idleThreshold = config.afkAfterSeconds();
			idle.setAfkAfterSeconds(idleThreshold);
		}
		int idleSeconds = Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks()) / 50;
		idle.update(idleSeconds, nowMs);
		SuperHistogramConfig.Metronome metronome = config.metronome();
		if (metronome == SuperHistogramConfig.Metronome.EVERY_TICK)
		{
			pulse.tick(nowMs, beat.isBeat(tick));
		}
		else if (metronome == SuperHistogramConfig.Metronome.CUE_TICK && beat.isBeat(tick))
		{
			pulse.tick(nowMs, true);
		}
		if (idle.warningFired() && config.afkWarning() && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
	}

	void handleStat(String skill, int totalXp)
	{
		if (xp != null)
		{
			xp.onStat(skill, totalXp, tick);
		}
	}

	/** Damage and block splats (any colour variant); heal, poison, venom, disease etc. are not hits. */
	static boolean isHitType(int type)
	{
		return type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER
			|| (type >= HitsplatID.DAMAGE_ME && type <= HitsplatID.DAMAGE_OTHER_WHITE)
			|| (type >= HitsplatID.DAMAGE_MAX_ME && type <= HitsplatID.DAMAGE_MAX_ME_WHITE)
			|| (type >= HitsplatID.DAMAGE_ME_POISE && type <= HitsplatID.DAMAGE_MAX_ME_POISE);
	}

	/** Records a damage or block hitsplat only when it lands on the local player. */
	void handleHit(Actor actor, int type, int amount)
	{
		if (timeline == null || !isHitType(type) || actor == null || actor != client.getLocalPlayer())
		{
			return;
		}
		lastHitTick = tick;
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.hits.add(new Hit(amount, amount == 0));
		}
	}

	/** An own animation while targeting an NPC counts as an attack unless it is the eat animation. */
	void handleAnimation(Actor a)
	{
		if (timeline == null || a == null || a != client.getLocalPlayer())
		{
			return;
		}
		int anim = a.getAnimation();
		if (anim == -1 || anim == EAT_ANIMATION || !(a.getInteracting() instanceof NPC) || !attack.canAttack(tick))
		{
			return;
		}
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.attackLate = attack.onAttack(tick, weaponSpeed);
		}
		beat.onAttack(tick);
	}

	void handleEatClick(int itemId)
	{
		if (eats != null)
		{
			eats.onEatClick(tick, itemId);
		}
	}

	void handleInventory(Map<Integer, Integer> counts)
	{
		if (eats != null)
		{
			eats.onInventory(tick, counts);
		}
	}

	/** itemId is the equipped weapon, or -1 for none. */
	void handleWeapon(int itemId)
	{
		int old = weaponSpeed;
		weaponSpeed = itemId < 0 ? AttackTimer.DEFAULT_SPEED : weaponSpeed(itemId);
		if (weaponSpeed != old && beat != null)
		{
			beat.onWeaponSpeed(tick, weaponSpeed);
		}
	}

	/** The beat starts only from this player-pressed hotkey. */
	void handleBeatKey()
	{
		Player me = client.getLocalPlayer();
		if (beat == null || me == null || tick < 0)
		{
			return;
		}
		String typed = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
		if (typed != null && !typed.isEmpty())
		{
			return;
		}
		beat.press(tick, weaponSpeed,
			ModeDetector.inCombat(me.getInteracting() instanceof NPC, tick, lastHitTick, attack.lastAttackTick()));
	}

	private int weaponSpeed(int itemId)
	{
		ItemStats stats = itemManager == null ? null : itemManager.getItemStats(itemId);
		ItemEquipmentStats eq = stats == null ? null : stats.getEquipment();
		return eq != null && eq.getAspeed() > 0 ? eq.getAspeed() : AttackTimer.DEFAULT_SPEED;
	}

	void handleClick(long nowMs)
	{
		if (clicks != null)
		{
			clicks.onClick(tick, nowMs);
		}
	}

	void handlePrayerClick(long nowMs)
	{
		if (clicks != null)
		{
			clicks.onPrayerClick(tick, nowMs);
		}
	}

	void handleGameState(GameState state, long nowMs)
	{
		if (clock == null)
		{
			return;
		}
		switch (state)
		{
			case LOGGED_IN:
				loggedIn = true;
				clock.onLoggedIn(nowMs);
				break;
			case HOPPING:
				clock.onHopping();
				movement.reset();
				lastLocation = null;
				break;
			case LOGIN_SCREEN:
				loggedIn = false;
				clock.onLoginScreen();
				xp.onLoginScreen();
				movement.reset();
				// Fresh timeline so nothing from this session reappears after relog;
				// XpTracker keeps its session totals.
				timeline = new Timeline(TIMELINE_TICKS);
				xp.rebind(timeline);
				movement = new MovementTracker(timeline);
				clicks = new ClickCollector(timeline);
				resetCombat();
				beat.reset();
				prayers = new PrayerCollector(timeline);
				flicks.reset();
				lastHitTick = AttackTimer.NEVER;
				tick = -1;
				idle = new IdleTracker(idleThreshold);
				lastLocation = null;
				runEnergyPct = 0;
				break;
			default:
				break;
		}
	}

	/**
	 * Snapshot for the painter; safe at any time. Null while logged out or before startUp, which
	 * the painter draws as the empty ground.
	 */
	BarState buildState()
	{
		if (!loggedIn || timeline == null)
		{
			return null;
		}
		long now = System.currentTimeMillis();
		IdleTracker idleNow = idle;
		boolean afk = idleNow != null && idleNow.isAfk();
		Player me = client.getLocalPlayer();
		int tickNow = timeline.newestTick();
		AttackTimer at = attack;
		boolean combat = me != null && at != null
			&& ModeDetector.inCombat(me.getInteracting() instanceof NPC, tickNow, lastHitTick, at.lastAttackTick());
		Mode mode = modeDetector.detect(afk, combat, config.prayerMode(), config.baseline());
		return BarState.builder()
			.timeline(timeline)
			.nowTick(timeline == null ? -1 : timeline.newestTick())
			.xp(xp)
			.idle(idleNow)
			.clock(clock)
			.runEnergyPct(runEnergyPct)
			.modeName(modeName(mode))
			.modeSub(modeSub(mode))
			.showRunEnergy(config.showRunEnergy())
			.showLoginTime(config.showLoginTime())
			.mode(mode)
			.nowMs(now)
			.beat(beat)
			.flicks(flicks)
			.weaponSpeed(weaponSpeed)
			.actionsRow(config.actionsRow())
			.showEatCosts(config.showEatCosts())
			.build();
	}

	private String modeSub(Mode mode)
	{
		switch (mode)
		{
			case AFK:
				return "auto";
			case ONE_TICK:
				return "every tick";
			case LAZY_FLICK:
				return weaponSpeed + "t \u00b7 " + config.beatKey();
			default:
				return "baseline";
		}
	}

	private static String modeName(Mode mode)
	{
		switch (mode)
		{
			case ONE_TICK:
				return "1-tick flick";
			case LAZY_FLICK:
				return "Lazy flick";
			case AFK:
				return "AFK";
			case MINIMAL:
				return "Minimal";
			default:
				return "General";
		}
	}

	@Provides
	SuperHistogramConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SuperHistogramConfig.class);
	}
}
