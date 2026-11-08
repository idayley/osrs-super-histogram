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
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.XpTracker;
import com.superhistogram.collect.EatCollector;
import com.superhistogram.collect.PrayerCollector;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.model.combat.Bosses;
import com.superhistogram.model.combat.FlashRule;
import com.superhistogram.model.combat.HitAttribution;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.AgilityCourses;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.ui.BarOverlay;
import com.superhistogram.ui.HullFlashOverlay;
import com.superhistogram.ui.Pulse;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.VarClientStr;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

@PluginDescriptor(
	name = "Super Histogram"
)
public class SuperHistogramPlugin extends Plugin
{
	static final int TIMELINE_TICKS = 512;
	static final String CONFIG_GROUP = "superhistogram";
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
	ConfigManager configManager;

	@Inject
	KeyManager keyManager;

	@Inject
	ClientThread clientThread;

	@Inject
	ModelOutlineRenderer outlineRenderer;

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
	volatile InventoryRun run;
	volatile AlchTracker alch;
	volatile GatherRun gather;
	volatile AgilityCourse agility;
	/** Id of the agility course whose region the player stands in; null off course. */
	volatile String regionCourse;
	/** The tick the player last gained Agility XP on a lap-end tile; -1 for none. */
	int lapEndTick = -1;
	/** Obstacle clicks waiting for their tick's model update: {tick, object id}. */
	private final Deque<int[]> obstacleClicks = new ArrayDeque<>();
	/** Free inventory slots from the newest inventory event; -1 when unknown. */
	volatile int latestFreeSlots = -1;
	/** The tick the last LOADING state arrived on: a scene reload despawns every object (this and the next tick are ignored). */
	int loadingTick = Integer.MIN_VALUE;
	/** Newest inventory item counts; null before the first inventory event and after logout. */
	volatile Map<Integer, Integer> latestInventory;
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
	private HullFlashOverlay hullOverlay;
	private final HitAttribution attribution = new HitAttribution();
	private final Map<Integer, NPC> swingers = new HashMap<>();
	private volatile HullFlashOverlay.Target flash;

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
		hullOverlay = new HullFlashOverlay(outlineRenderer, this::flashTarget);
		overlayManager.add(hullOverlay);
		keyManager.registerKeyListener(beatHotkey);
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(beatHotkey);
		if (hullOverlay != null)
		{
			overlayManager.remove(hullOverlay);
			hullOverlay = null;
		}
		clearFlash();
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
		run = new InventoryRun(timeline);
		alch = new AlchTracker(timeline);
		gather = new GatherRun(timeline);
		agility = new AgilityCourse(timeline);
		regionCourse = null;
		lapEndTick = -1;
		obstacleClicks.clear();
		latestFreeSlots = -1;
		loadingTick = Integer.MIN_VALUE;
		latestInventory = null;
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
	public void onItemSpawned(ItemSpawned e)
	{
		if (e.getItem() != null && e.getTile() != null)
		{
			handleItemSpawned(e.getItem().getId(), e.getTile().getWorldLocation());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		handleNpcDespawned(e.getNpc());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		net.runelite.api.Point p = e.getGameObject() == null ? null : e.getGameObject().getSceneMinLocation();
		if (p != null)
		{
			handleObjectDespawned(p.getX(), p.getY());
		}
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
		if (isBankClick(option, e.getMenuTarget()))
		{
			handleBankClick();
		}
		if (isHighAlchClick(e.getMenuAction(), e.getMenuTarget()))
		{
			handleCastClick(e.getItemId());
		}
		MenuAction action = e.getMenuAction();
		String gs = gatherSkill(action, option, e.getMenuTarget());
		if (gs != null)
		{
			int key = action.name().startsWith("GAME_OBJECT_") ? objectKey(e.getParam0(), e.getParam1())
				: action.name().startsWith("NPC_") ? npcKey(e.getId()) : 0;
			handleGatherClick(gs, key, gatherTargetName(option, e.getMenuTarget()));
		}
		if (isObstacleAction(action) && !isBankClick(option, e.getMenuTarget()) && gs == null)
		{
			handleObstacleClick(e.getId());
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
			int used = 0;
			for (Item i : c.getItems())
			{
				if (i != null && i.getId() > 0)
				{
					counts.merge(i.getId(), i.getQuantity(), Integer::sum);
					used++;
				}
			}
			handleInventory(counts, GatherRun.INVENTORY - used);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		handleProfileChanged();
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
		boolean finished = run.onTick(tick - 1, prev == null ? Collections.<XpDrop>emptyList() : prev.xp, latestInventory);
		if (finished && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
		alch.onTick(tick - 1, prev == null ? Collections.<XpDrop>emptyList() : prev.xp);
		Player me = client.getLocalPlayer();
		boolean animating = me != null && me.getAnimation() != -1;
		boolean filled = gather.onTick(tick - 1, animating, prev == null ? Collections.<XpDrop>emptyList() : prev.xp,
			latestInventory, latestFreeSlots);
		if (filled && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
		WorldPoint spot = me == null ? null : me.getWorldLocation();
		AgilityCourses.Course course = spot == null ? null : AgilityCourses.byRegion(spot.getRegionID());
		regionCourse = course == null ? null : course.id;
		AgilityCourse ag = agility;
		if (course != null && !course.id.equals(ag.course()))
		{
			ag.onCourse(course.id);
			if (configManager != null)
			{
				Integer best = configManager.getRSProfileConfiguration(CONFIG_GROUP, "bestLap." + course.id, Integer.class);
				if (best != null)
				{
					ag.loadBest(course.id, best);
				}
			}
		}
		boolean lapDone = ag.onTick(tick - 1, prev == null ? Collections.<XpDrop>emptyList() : prev.xp,
			prev != null && !prev.hits.isEmpty(), lapEndTick >= 0 && lapEndTick == tick - 1);
		// Clicks wait for the update of their own tick: a landing on the tick of the next click must count first.
		for (int[] click = obstacleClicks.poll(); click != null; click = obstacleClicks.poll())
		{
			ag.onObstacleClick(click[0], click[1]);
		}
		if (lapDone && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
		if (ag.takeBestChanged() && configManager != null)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, "bestLap." + ag.course(), ag.bestTicks());
		}
		boolean cue = beat.isBeat(tick) || (run.running() && run.finishTick() == tick)
			|| (alch.showing(tick) && alch.nextReadyTick() == tick) || gather.cueTick() == tick
			|| (ag.showing(tick) && ag.readyTick() == tick);

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
			pulse.tick(nowMs, cue);
		}
		else if (metronome == SuperHistogramConfig.Metronome.CUE_TICK && cue)
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
		if ("Agility".equals(skill) && agility != null)
		{
			Player p = client == null ? null : client.getLocalPlayer();
			WorldPoint w = p == null ? null : p.getWorldLocation();
			if (w != null && AgilityCourses.isLapEnd(agility.course(), w.getX(), w.getY(), w.getPlane()))
			{
				lapEndTick = tick;
			}
		}
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
		handleHit(actor, type, amount, System.currentTimeMillis());
	}

	void handleHit(Actor actor, int type, int amount, long nowMs)
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
		flashAttacker(amount == 0, nowMs);
	}

	/**
	 * Called only after a hitsplat has landed on the player. Attribution just picks which hull to
	 * flash; ambiguity flashes nothing.
	 */
	private void flashAttacker(boolean miss, long nowMs)
	{
		NPC npc = swingers.get(attribution.attribute(tick));
		if (npc == null)
		{
			return;
		}
		FlashRule.Flash f = FlashRule.decide(config.hullFlash(), config.hullFlashAtBosses(),
			Bosses.isBoss(npc.getName()), miss, config.hullFlashMisses() == SuperHistogramConfig.HullFlashMisses.FAINT);
		if (f != FlashRule.Flash.NONE)
		{
			flash = new HullFlashOverlay.Target(npc, nowMs, f == FlashRule.Flash.MISS);
		}
	}

	HullFlashOverlay.Target flashTarget()
	{
		return flash;
	}

	void handleNpcDespawned(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		if (gather != null)
		{
			gather.onTargetGone(tick, npcKey(npc.getIndex()));
		}
		attribution.forget(npc.getIndex());
		swingers.remove(npc.getIndex());
		HullFlashOverlay.Target t = flash;
		if (t != null && t.npc == npc)
		{
			flash = null;
		}
	}

	private void clearFlash()
	{
		attribution.reset();
		swingers.clear();
		flash = null;
	}

	/** An own animation while targeting an NPC counts as an attack unless it is the eat animation. */
	void handleAnimation(Actor a)
	{
		if (timeline == null || a == null)
		{
			return;
		}
		if (a instanceof NPC)
		{
			NPC n = (NPC) a;
			if (n.getAnimation() != -1 && !n.isDead() && n.getInteracting() != null
				&& n.getInteracting() == client.getLocalPlayer())
			{
				attribution.onSwing(n.getIndex(), tick);
				swingers.put(n.getIndex(), n);
			}
			return;
		}
		if (a != client.getLocalPlayer())
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
		handleInventory(counts, latestFreeSlots);
	}

	/** counts is the whole inventory; freeSlots the number of empty slots (-1 when unknown). */
	void handleInventory(Map<Integer, Integer> counts, int freeSlots)
	{
		latestFreeSlots = freeSlots;
		latestInventory = new HashMap<>(counts);
		if (eats != null)
		{
			eats.onInventory(tick, counts);
		}
	}

	/** The player clicked a bank (booth, banker, chest) or used an item on one. */
	void handleBankClick()
	{
		InventoryRun r = run;
		if (r != null)
		{
			r.onBankClick(tick);
		}
		GatherRun gr = gather;
		if (gr != null)
		{
			gr.onBankClick(tick);
		}
	}

	/** The player clicked a tree, rock, fishing spot or logs to light. */
	void handleGatherClick(String skill, int key, String name)
	{
		GatherRun gr = gather;
		if (gr != null)
		{
			gr.onTargetClick(tick, skill, key, name);
		}
	}

	/** True for the five game-object options: obstacles are clicked with one of them. */
	static boolean isObstacleAction(MenuAction a)
	{
		return a == MenuAction.GAME_OBJECT_FIRST_OPTION || a == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| a == MenuAction.GAME_OBJECT_THIRD_OPTION || a == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| a == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	/** The player clicked a game object; it counts as an obstacle only on an agility course. */
	void handleObstacleClick(int objectId)
	{
		if (agility != null && regionCourse != null && tick >= 0)
		{
			obstacleClicks.add(new int[]{tick, objectId});
		}
	}

	/** A mark of grace spawned on the current course (client thread); ignored on the tick a scene loads. */
	void handleItemSpawned(int itemId, WorldPoint where)
	{
		AgilityCourse ag = agility;
		if (ag == null || where == null || itemId != ItemID.GRACE || tick == loadingTick)
		{
			return;
		}
		AgilityCourses.Course c = AgilityCourses.byRegion(where.getRegionID());
		if (c != null && c.id.equals(ag.course()))
		{
			ag.onMark(tick);
		}
	}

	/** A game object despawned at this scene tile (client thread); ignored on the tick a scene loads. */
	void handleObjectDespawned(int sceneX, int sceneY)
	{
		GatherRun gr = gather;
		if (gr != null && (long) tick - loadingTick > 1)
		{
			gr.onTargetGone(tick, objectKey(sceneX, sceneY));
		}
	}

	/** Identity of a clicked or despawned object: always positive. */
	static int objectKey(int sceneX, int sceneY)
	{
		return 1 + sceneX * 512 + sceneY;
	}

	/** Identity of an NPC (a fishing spot): always negative. */
	static int npcKey(int index)
	{
		return -(index + 1);
	}

	private static final Set<String> FISHING_OPTIONS = new HashSet<>(Arrays.asList(
		"Net", "Small Net", "Big Net", "Bait", "Lure", "Harpoon", "Cage", "Fish", "Use-rod"));

	/** The gathering skill a menu click starts, or null when it is not a gathering click. */
	static String gatherSkill(MenuAction a, String option, String target)
	{
		if (a == null || option == null)
		{
			return null;
		}
		switch (a)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return "Chop down".equals(option) || "Chop".equals(option) ? "Woodcutting"
					: "Mine".equals(option) ? "Mining" : null;
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
				return FISHING_OPTIONS.contains(option) ? "Fishing" : null;
			case CC_OP:
				return "Light".equals(option) ? "Firemaking" : null;
			case WIDGET_TARGET_ON_WIDGET:
				return "Use".equals(option) && target != null && Text.removeTags(target).contains("Tinderbox")
					? "Firemaking" : null;
			default:
				return null;
		}
	}

	/** The clicked target's name: tags removed; for "Use" the side of " -> " that is not the tinderbox. */
	static String gatherTargetName(String option, String target)
	{
		if (target == null)
		{
			return "";
		}
		String t = Text.removeTags(target);
		if ("Use".equals(option))
		{
			for (String side : t.split(" -> "))
			{
				if (!"Tinderbox".equals(side.trim()))
				{
					return side.trim();
				}
			}
		}
		return t;
	}

	static boolean isBankClick(String option, String target)
	{
		if ("Bank".equals(option))
		{
			return true;
		}
		return "Use".equals(option) && target != null && Text.removeTags(target).contains("Bank");
	}

	/** The player cast High Level Alchemy on an item (client thread). */
	void handleCastClick(int itemId)
	{
		AlchTracker a = alch;
		if (a != null)
		{
			a.onCastClick(tick, alchCoins(itemId));
		}
	}

	/** True for the click that casts High Level Alchemy on an inventory item. */
	static boolean isHighAlchClick(MenuAction action, String target)
	{
		return action == MenuAction.WIDGET_TARGET_ON_WIDGET && target != null
			&& Text.removeTags(target).startsWith("High Level Alchemy");
	}

	/**
	 * Coins the cast gives: the item's High Alchemy value, or null when unknown (no composition, no value).
	 * Noted items use their base item.
	 */
	Long alchCoins(int itemId)
	{
		if (itemManager == null || itemId <= 0)
		{
			return null;
		}
		ItemComposition comp = itemManager.getItemComposition(itemManager.canonicalize(itemId));
		if (comp == null || comp.getHaPrice() <= 0)
		{
			return null;
		}
		return (long) comp.getHaPrice();
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

	/** The active RuneScape profile changed: the saved bests belong to another account now. */
	void handleProfileChanged()
	{
		AgilityCourse ag = agility;
		if (ag != null)
		{
			ag.reset();
			if (timeline != null)
			{
				ag.rebind(timeline);
			}
		}
		lapEndTick = -1;
		obstacleClicks.clear();
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
			case LOADING:
				loadingTick = tick;
				break;
			case HOPPING:
				clock.onHopping();
				clearFlash();
				movement.reset();
				lastLocation = null;
				break;
			case LOGIN_SCREEN:
				loggedIn = false;
				clearFlash();
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
				run = new InventoryRun(timeline);
				alch.rebind(timeline);
				gather = new GatherRun(timeline);
				// Another account may log in next: forget the course and every best (they reload per profile).
				agility.reset();
				agility.rebind(timeline);
				lapEndTick = -1;
				regionCourse = null;
				obstacleClicks.clear();
				latestFreeSlots = -1;
				loadingTick = Integer.MIN_VALUE;
				latestInventory = null;
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
		AgilityCourse agilityNow = agility;
		boolean agilityOn = agilityNow != null && agilityNow.showing(tickNow);
		// A fall hits the player; while agility shows, only an NPC target or an own attack means combat.
		boolean combat = me != null && at != null
			&& ModeDetector.inCombat(me.getInteracting() instanceof NPC, tickNow,
				agilityOn ? AttackTimer.NEVER : lastHitTick, at.lastAttackTick());
		InventoryRun runNow = run;
		AlchTracker alchNow = alch;
		GatherRun gatherNow = gather;
		Mode skilling = ModeDetector.skilling(alchNow != null && alchNow.showing(tickNow),
			runNow != null && runNow.showing(tickNow), agilityOn, gatherNow != null && gatherNow.showing(tickNow));
		Mode mode = modeDetector.detect(afk, combat, skilling, config.prayerMode(), config.baseline());
		return BarState.builder()
			.timeline(timeline)
			.nowTick(timeline == null ? -1 : timeline.newestTick())
			.xp(xp)
			.idle(idleNow)
			.clock(clock)
			.runEnergyPct(runEnergyPct)
			.modeName(mode == Mode.PRODUCTION ? runNow.skill()
				: mode == Mode.GATHERING ? gatherNow.skill() : modeName(mode))
			.modeSub(mode == Mode.GATHERING ? gatherSub(gatherNow)
				: mode == Mode.AGILITY ? agilitySub(agilityNow) : modeSub(mode))
			.showRunEnergy(config.showRunEnergy())
			.showLoginTime(config.showLoginTime())
			.mode(mode)
			.nowMs(now)
			.beat(beat)
			.flicks(flicks)
			.weaponSpeed(weaponSpeed)
			.actionsRow(config.actionsRow())
			.showEatCosts(config.showEatCosts())
			.run(runNow)
			.timeToFinish(config.timeToFinish())
			.wastedTicks(config.wastedTicks())
			.xpDrops(config.xpDrops())
			.xpDots(config.xpDots())
			.alch(alchNow)
			.gather(gatherNow)
			.alchTopRow(config.alchTopRow())
			.rightSide(config.rightSide())
			.agility(agilityNow)
			.lapCompare(config.lapCompare())
			.build();
	}

	private static String agilitySub(AgilityCourse a)
	{
		AgilityCourses.Course c = a == null ? null : AgilityCourses.byId(a.course());
		return c == null ? "auto" : "auto \u00b7 " + c.name;
	}

	private static String gatherSub(GatherRun g)
	{
		String name = g.targetName();
		return name == null || name.isEmpty() ? "auto" : "auto \u00b7 " + name;
	}

	private String modeSub(Mode mode)
	{
		switch (mode)
		{
			case AFK:
				return "auto";
			case ONE_TICK:
				return "every tick";
			case PRODUCTION:
				return "auto";
			case ALCH:
				return "auto \u00b7 5t";
			case GATHERING:
			case AGILITY:
				return "auto";
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
			case ALCH:
				return "High alch";
			case AGILITY:
				return "Agility";
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
