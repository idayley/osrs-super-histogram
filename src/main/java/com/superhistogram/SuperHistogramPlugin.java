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
import com.superhistogram.ui.BarOverlay;
import com.superhistogram.ui.Pulse;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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

	@Inject
	Client client;

	@Inject
	OverlayManager overlayManager;

	@Inject
	SuperHistogramConfig config;

	private final ModeDetector modeDetector = new ModeDetector();

	// Replaced wholesale on start/stop, read by the render thread.
	volatile Timeline timeline;
	volatile XpTracker xp;
	volatile IdleTracker idle;
	volatile SessionClock clock;
	private MovementTracker movement;
	private ClickCollector clicks;
	Pulse pulse;
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
	}

	@Override
	protected void shutDown() throws Exception
	{
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
		handleClick(System.currentTimeMillis());
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
		// CUE_TICK behaves like OFF until Phase 2.
		if (config.metronome() == SuperHistogramConfig.Metronome.EVERY_TICK)
		{
			pulse.tick(nowMs, false);
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
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.hits.add(new Hit(amount, amount == 0));
		}
	}

	void handleClick(long nowMs)
	{
		if (clicks != null)
		{
			clicks.onClick(tick, nowMs);
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
		Mode mode = modeDetector.detect(afk, config.baseline());
		return BarState.builder()
			.timeline(timeline)
			.nowTick(timeline == null ? -1 : timeline.newestTick())
			.xp(xp)
			.idle(idleNow)
			.clock(clock)
			.runEnergyPct(runEnergyPct)
			.modeName(modeName(mode))
			.modeSub(mode == Mode.AFK ? "auto" : "baseline")
			.showRunEnergy(config.showRunEnergy())
			.showLoginTime(config.showLoginTime())
			.mode(mode)
			.nowMs(now)
			.build();
	}

	private static String modeName(Mode mode)
	{
		switch (mode)
		{
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
