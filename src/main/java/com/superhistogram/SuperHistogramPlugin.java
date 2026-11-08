package com.superhistogram;

import com.google.inject.Provides;
import com.superhistogram.collect.ClickCollector;
import com.superhistogram.model.BarPlacement;
import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.IdleTracker;
import com.superhistogram.model.Mode;
import com.superhistogram.model.ModeDetector;
import com.superhistogram.model.MovementTracker;
import com.superhistogram.model.SessionClock;
import com.superhistogram.model.InputMark;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.XpTracker;
import com.superhistogram.collect.EatCollector;
import com.superhistogram.collect.FightCollector;
import com.superhistogram.collect.PrayerCollector;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.model.combat.Bosses;
import com.superhistogram.model.combat.FlashRule;
import com.superhistogram.model.combat.IgnoreList;
import com.superhistogram.model.combat.HitAttribution;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.AgilityCourses;
import com.superhistogram.model.skill.ThieveRun;
import com.superhistogram.model.skill.TrapTracker;
import com.superhistogram.model.skill.LoopRun;
import com.superhistogram.model.skill.RoundTracker;
import com.superhistogram.model.skill.SlayerTask;
import com.superhistogram.ui.modes.LoopView;
import com.superhistogram.model.skill.SpellRun;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.ui.BarOverlay;
import com.superhistogram.ui.Colours;
import com.superhistogram.ui.HullFlashOverlay;
import com.superhistogram.ui.Pulse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.VarClientStr;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.worldhopper.ping.Ping;
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
	/** False when the game gave no attack speed for the weapon: then it is learned from your swings. */
	volatile boolean weaponSpeedKnown = true;
	/** Speed learned from the gaps between your recent swings (unknown weapon); -1 until two gaps are seen. */
	volatile int learnedSpeed = -1;
	private final Deque<Integer> swingGaps = new ArrayDeque<>();
	volatile Beat beat;
	private PrayerCollector prayers;
	private FightCollector fight;
	private String lastFightName;
	private int attackClickNpc = -1, attackClickTick = Integer.MIN_VALUE / 2;
	/**
	 * An own animation at an NPC not yet known to be a fight (auto-retaliate, no Attack click): kept until the
	 * swing's hitsplat lands on that NPC, then recorded as the first attack on its own tick.
	 */
	private int pendingSwingNpc = -1, pendingSwingTick = Integer.MIN_VALUE / 2;
	/** Tick the last monster you were fighting despawned (died): the combat hold ends there. */
	private int combatEndedTick = Integer.MIN_VALUE / 2;
	/** Ticks a pending swing waits for its hitsplat (ranged and magic land up to a few ticks later). */
	static final int PENDING_SWING_TICKS = 6;
	volatile FlickClassifier flicks;
	volatile InventoryRun run;
	volatile AlchTracker alch;
	volatile GatherRun gather;
	volatile AgilityCourse agility;
	volatile ThieveRun thieve;
	volatile TrapTracker hunt;
	volatile SpellRun spell;
	/** Runecraft trips, farm runs and bird house runs. */
	volatile LoopRun runecraft, farming, birdhouses;
	/** Minigame rounds. */
	volatile RoundTracker minigame;
	/** Your slayer task (kept across logouts: the task is the account's). */
	volatile SlayerTask slayer;
	/** Id of the agility course whose region the player stands in; null off course. */
	volatile String regionCourse;
	/** The tick the player last gained Agility XP on a lap-end tile; -1 for none. */
	int lapEndTick = -1;
	/** Obstacle clicks waiting for their tick's model update: {tick, object id}. */
	private final Deque<int[]> obstacleClicks = new ArrayDeque<>();
	/** Clicks the game acts on at the next tick: run once that tick's update is in, stamped with it. */
	private final Deque<Acted> actedClicks = new ArrayDeque<>();
	/** The last click reached the server only after the next tick (ping), so the game acts on it a tick later. */
	private boolean lastClickLate;

	/** A click's effect, due on the tick the game acts on it. */
	private static final class Acted
	{
		final int due;
		final java.util.function.IntConsumer run;

		Acted(int due, java.util.function.IntConsumer run)
		{
			this.due = due;
			this.run = run;
		}
	}

	/** The tick the game acts on a click made now: the next one, or the one after when ping makes it late. */
	private int actedTick()
	{
		return tick + 1 + (lastClickLate ? 1 : 0);
	}

	@Inject
	WorldService worldService;

	private java.util.concurrent.ScheduledExecutorService pingExecutor;
	/** Recent round-trip pings to the current world, newest last; read on the client thread. */
	private final Deque<Integer> pings = new java.util.concurrent.ConcurrentLinkedDeque<>();
	/** Free inventory slots from the newest inventory event; -1 when unknown. */
	volatile int latestFreeSlots = -1;
	/** The tick the last LOADING state arrived on: a scene reload despawns every object (this and the next tick are ignored). */
	int loadingTick = Integer.MIN_VALUE;
	/** Newest inventory item counts; null before the first inventory event and after logout. */
	volatile Map<Integer, Integer> latestInventory;
	/** NPC index to the tick a damage or block hitsplat last landed on it; dropped on despawn, hop and logout. */
	private final Map<Integer, Integer> npcHitTicks = new HashMap<>();
	/** Tick of my latest hitsplat on any NPC; fast kills count as combat even before an attack registers. */
	private int lastOwnHitTick = Integer.MIN_VALUE / 2;
	/** NPC index to the tick its attack (hit or miss) last landed on you; dropped on despawn, hop and logout. */
	private final Map<Integer, Integer> attackers = new HashMap<>();
	/** Tick a monster's attack last landed on you: being attacked is a fight even if you never swing back. */
	private int lastAttackedTick = Integer.MIN_VALUE / 2;
	private String lastAttackerName;
	/** The parsed ignore list and the setting text it came from (re-parsed only when that changes). */
	private IgnoreList ignoreList = IgnoreList.parse("");
	private String ignoreText = "";
	/** The NPC you last clicked Pickpocket on, and when: its stun is not a fight. */
	private int pickpocketNpc = -1;
	private int pickpocketTick = Integer.MIN_VALUE / 2;
	static final int PICKPOCKET_STUN_TICKS = 10;
	Pulse pulse;
	private final HotkeyListener beatHotkey = new HotkeyListener(() -> config.beatKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(SuperHistogramPlugin.this::handleBeatKey);
		}
	};
	BarOverlay overlay;
	private HullFlashOverlay hullOverlay;
	private final HitAttribution attribution = new HitAttribution();
	private final Map<Integer, NPC> swingers = new HashMap<>();
	private volatile HullFlashOverlay.Target flash;

	/** Own tick counter: unlike the client's, it never goes backwards across logins. */
	private int tick = -1;
	/**
	 * RuneLite posts a server tick's hitsplats, animations, XP and despawns before its GameTick. The first of them
	 * opens the new tick, so they land on the tick they happened, with what is read at GameTick.
	 */
	private boolean tickOpened;
	/** Wall-clock time of the newest tick, for smooth scrolling. */
	private volatile long tickMs;
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
		overlay.setChatArea(this::chatArea);
		overlayManager.add(overlay);
		// RuneLite restores a saved (hand-dragged) size on add; only fill in the preset when there is none.
		overlay.applySizeIfUnset(config.barHeight());
		syncHeightSetting();
		overlay.applyPosition(config.position());
		hullOverlay = new HullFlashOverlay(outlineRenderer, this::flashTarget);
		overlayManager.add(hullOverlay);
		applyColours();
		keyManager.registerKeyListener(beatHotkey);
		pingExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "super-histogram-ping");
			t.setDaemon(true);
			return t;
		});
		pingExecutor.scheduleWithFixedDelay(this::measurePing, 2, 5, java.util.concurrent.TimeUnit.SECONDS);
	}

	/** Pings the current world (off the client thread), the way the World Hopper does. */
	void measurePing()
	{
		try
		{
			if (config.pingShift() != SuperHistogramConfig.PingShift.AUTO || worldService == null
				|| client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			net.runelite.http.api.worlds.WorldResult worlds = worldService.getWorlds();
			net.runelite.http.api.worlds.World w = worlds == null ? null : worlds.findWorld(client.getWorld());
			int ms = w == null ? -1 : Ping.ping(w);
			if (ms > 0)
			{
				pings.addLast(ms);
				while (pings.size() > 5)
				{
					pings.pollFirst();
				}
			}
		}
		catch (RuntimeException ignored)
		{
			// A failed ping leaves the last good ones in place.
		}
	}

	/** The delay used to move late clicks: the median of recent pings (Auto), the fixed value, or 0 (Off). */
	int pingShiftMs()
	{
		SuperHistogramConfig.PingShift mode = config.pingShift();
		if (mode == SuperHistogramConfig.PingShift.FIXED)
		{
			return config.pingFixedMs();
		}
		if (mode != SuperHistogramConfig.PingShift.AUTO || pings.isEmpty())
		{
			return 0;
		}
		java.util.List<Integer> sorted = new java.util.ArrayList<>(pings);
		java.util.Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	/**
	 * Makes the Height setting show the bar's real height: a size RuneLite restored (dragged by hand, or left
	 * from the old Small / Large presets) otherwise sits behind a setting that still reads 62.
	 */
	void syncHeightSetting()
	{
		java.awt.Dimension d = overlay.getPreferredSize();
		if (configManager == null || d == null || d.height == config.barHeight())
		{
			return;
		}
		configManager.setConfiguration(CONFIG_GROUP, "barHeight", d.height);
		configManager.unsetConfiguration(CONFIG_GROUP, "size");
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(beatHotkey);
		if (pingExecutor != null)
		{
			pingExecutor.shutdownNow();
			pingExecutor = null;
		}
		pings.clear();
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
		fight = new FightCollector(timeline);
		lastFightName = null;
		flicks = new FlickClassifier();
		run = new InventoryRun(timeline);
		alch = new AlchTracker(timeline);
		gather = new GatherRun(timeline);
		agility = new AgilityCourse(timeline);
		thieve = new ThieveRun(timeline);
		hunt = new TrapTracker(timeline);
		spell = new SpellRun(timeline);
		runecraft = new LoopRun(LoopRun.Kind.RUNECRAFT, timeline);
		farming = new LoopRun(LoopRun.Kind.FARMING, timeline);
		birdhouses = new LoopRun(LoopRun.Kind.BIRDHOUSE, timeline);
		minigame = new RoundTracker(timeline);
		slayer = new SlayerTask();
		regionCourse = null;
		lapEndTick = -1;
		obstacleClicks.clear();
		actedClicks.clear();
		latestFreeSlots = -1;
		loadingTick = Integer.MIN_VALUE;
		latestInventory = null;
		npcHitTicks.clear();
		lastOwnHitTick = Integer.MIN_VALUE / 2;
		attackers.clear();
		lastAttackedTick = Integer.MIN_VALUE / 2;
		lastAttackerName = null;
		pickpocketNpc = -1;
		idleThreshold = config.afkAfterSeconds();
		idle = new IdleTracker(idleThreshold);
		if (pulse == null)
		{
			pulse = new Pulse();
		}
		tick = -1;
		tickOpened = false;
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
		weaponSpeedKnown = true;
		learnedSpeed = -1;
		swingGaps.clear();
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
		Hitsplat h = e.getHitsplat();
		handleHit(e.getActor(), h.getHitsplatType(), h.getAmount(), System.currentTimeMillis(), h.isMine());
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
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		net.runelite.api.Point p = e.getGameObject() == null ? null : e.getGameObject().getSceneMinLocation();
		if (p != null)
		{
			handleObjectSpawned(e.getGameObject().getId(), p.getX(), p.getY());
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
			String[] ov = overheadClick(option, e.getMenuTarget());
			if (ov != null)
			{
				handleOverheadClick(now, ov[0]);
			}
		}
		if ("Attack".equals(option) && e.getMenuAction().name().startsWith("NPC_"))
		{
			handleAttackClick(e.getId());
		}
		if ("Pickpocket".equals(option) && e.getMenuAction().name().startsWith("NPC_"))
		{
			handlePickpocketClick(e.getId());
			handleThieveClick(Text.removeTags(e.getMenuTarget()), false);
		}
		if ("Steal-from".equals(option) && e.getMenuAction().name().startsWith("GAME_OBJECT_"))
		{
			handleThieveClick(Text.removeTags(e.getMenuTarget()), true);
		}
		if ("Lay".equals(option))
		{
			handleLayClick();
		}
		if (e.getMenuAction().name().startsWith("GAME_OBJECT_") && LoopRun.isBirdhouse(e.getId()))
		{
			handleBirdhouseClick();
		}
		if (e.getMenuAction().name().startsWith("GAME_OBJECT_") && TrapTracker.isTrap(e.getId()))
		{
			handleTrapClick(objectKey(e.getParam0(), e.getParam1()), e.getId());
		}
		InputMark.Kind input = inputKind(e.getMenuAction(), option);
		if (input != null)
		{
			handleInput(now, input);
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
		else
		{
			String sp = skillingSpell(e.getMenuAction(), option, e.getMenuTarget(), group);
			if (sp != null)
			{
				handleSpellClick(sp, e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET ? e.getItemId() : 0);
			}
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
	public void onMenuEntryAdded(net.runelite.api.events.MenuEntryAdded e)
	{
		MenuEntry entry = e.getMenuEntry();
		if (entry.getType() != MenuAction.EXAMINE_NPC || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		NPC npc = entry.getNpc();
		String option = ignoreMenuOption(npc);
		if (option == null)
		{
			return;
		}
		String name = Text.removeTags(npc.getName());
		// Index 1 sits just above Cancel, so it's never the left-click option.
		client.getMenu().createMenuEntry(1)
			.setOption(option)
			.setTarget(e.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(x -> toggleIgnore(name));
	}

	/** "Ignore" or "Un-ignore" for a monster's shift-right-click menu; null when there's nothing to offer. */
	String ignoreMenuOption(NPC npc)
	{
		if (npc == null || npc.getName() == null || npc.getCombatLevel() <= 0)
		{
			return null;
		}
		if (!ignored(npc))
		{
			return "Ignore";
		}
		// Only a wildcard covers it: un-ignoring would mean rewriting the pattern, so offer nothing.
		return IgnoreList.lists(config.ignoreEnemies(), npc.getName()) ? "Un-ignore" : null;
	}

	/** Adds the name to the ignore list, or takes it off. */
	void toggleIgnore(String name)
	{
		String text = config.ignoreEnemies() == null ? "" : config.ignoreEnemies();
		configManager.setConfiguration(CONFIG_GROUP, "ignoreEnemies", IgnoreList.toggle(text, name));
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() == net.runelite.api.ChatMessageType.GAMEMESSAGE
			|| e.getType() == net.runelite.api.ChatMessageType.SPAM)
		{
			handleChatMessage(e.getMessage());
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
	public void onConfigChanged(ConfigChanged e)
	{
		handleConfigChanged(e.getGroup(), e.getKey());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		handleGameState(e.getGameState(), System.currentTimeMillis());
	}

	// ---- handlers ----

	/** Pushes Bar and Colours settings into the overlays. Other settings are read live. */
	void handleConfigChanged(String group, String key)
	{
		if (!CONFIG_GROUP.equals(group) || key == null || overlay == null)
		{
			return;
		}
		switch (key)
		{
			case "barHeight":
				overlay.applySize(config.barHeight());
				// Persist so a stale hand-dragged size saved earlier does not come back on restart.
				overlayManager.saveOverlay(overlay);
				break;
			case "position":
				overlay.applyPosition(config.position());
				overlayManager.saveOverlay(overlay);
				break;
			case "damageColour":
			case "mintColour":
			case "runEnergyColour":
			case "backgroundColour":
				applyColours();
				break;
			default:
				break;
		}
	}

	private void applyColours()
	{
		Colours c = Colours.orDefaults(config.backgroundColour(), config.damageColour(), config.mintColour(),
			config.runEnergyColour());
		if (overlay != null)
		{
			overlay.setColours(c);
		}
		if (hullOverlay != null)
		{
			hullOverlay.setColours(c);
		}
	}

	/**
	 * The chat text area in canvas pixels, for placing the bar on it. Uses the chat area widget when shown;
	 * with the chat collapsed or swapped out, the space above the chat buttons; with no chatbox at all, the
	 * classic chat spot at the bottom-left of the canvas. Called from render, on the client thread.
	 */
	private BarPlacement.Rect chatArea()
	{
		if (client == null)
		{
			return null;
		}
		Widget area = client.getWidget(InterfaceID.Chatbox.CHATAREA);
		if (area != null && !area.isHidden() && area.getWidth() > 0)
		{
			java.awt.Rectangle b = area.getBounds();
			return new BarPlacement.Rect(b.x, b.y, b.width, b.height);
		}
		Widget box = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		if (box != null && !box.isHidden() && box.getWidth() > 0)
		{
			java.awt.Rectangle b = box.getBounds();
			Widget buttons = client.getWidget(InterfaceID.Chatbox.CONTROLS);
			int bh = buttons == null || buttons.isHidden() ? 0 : buttons.getHeight();
			return new BarPlacement.Rect(b.x, b.y, b.width, Math.max(0, b.height - bh));
		}
		return BarPlacement.fallbackChat(client.getCanvasHeight());
	}

	/** A server event arrived: it belongs to the tick whose GameTick comes next. */
	void openServerTick()
	{
		if (timeline == null || tickOpened)
		{
			return;
		}
		tick++;
		timeline.at(tick);
		tickOpened = true;
	}

	void handleTick(long nowMs)
	{
		if (timeline == null)
		{
			return;
		}
		if (!tickOpened)
		{
			tick++;
		}
		tickOpened = false;
		TickRecord before = timeline.peek(tick - 1);
		if (before != null && tickMs > 0 && nowMs > tickMs)
		{
			before.lengthMs = (int) Math.min(60_000L, nowMs - tickMs);
		}
		tickMs = nowMs;
		timeline.at(tick);
		clicks.setPingMs(pingShiftMs());
		clicks.onTick(tick, nowMs);
		String overhead = PrayerCollector.overheadKind(client);
		prayers.onTick(tick, overhead, client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER));
		fight.onTick(tick, fightTarget());
		TickRecord prev = timeline.peek(tick - 1);
		if (prev != null && flicks != null)
		{
			prev.flick = flicks.classify(prev.prayerClicks, overhead != null);
			prev.flickPhase = flicks.phase();
		}
		if (beat.active() && beat.speed() != Math.max(1, config.beatTicks()))
		{
			beat.onWeaponSpeed(tick, config.beatTicks());
		}
		if (beatAlways() || inCombatHeld(client.getLocalPlayer(), tick, attack))
		{
			// Tracking the enemy: the beat runs for the whole fight (or always), not only while you swing.
			beat.onAttack(tick);
		}
		beat.onTick(tick, overhead != null);
		// Server events for a tick arrive before its GameTick, so this tick's record is already complete.
		TickRecord now = timeline.at(tick);
		boolean finished = run.onTick(tick, now.xp, latestInventory);
		if (finished && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
		long goldBefore = alch.sessionGold();
		alch.onTick(tick, now.xp);
		if (alch.sessionGold() > goldBefore)
		{
			addAlchToday(alch.sessionGold() - goldBefore);
		}
		Player me = client.getLocalPlayer();
		boolean animating = me != null && me.getAnimation() != -1;
		boolean filled = gather.onTick(tick, animating, now.xp,
			latestInventory, latestFreeSlots);
		if (filled && config.blinkWhenDone())
		{
			pulse.done(nowMs);
		}
		ThieveRun th = thieve;
		if (th != null)
		{
			th.onTick(tick, now.xp, !now.hits.isEmpty(), latestInventory, latestFreeSlots);
		}
		TrapTracker tr = hunt;
		if (tr != null)
		{
			tr.onTick(tick, now.xp);
		}
		SpellRun sr = spell;
		if (sr != null)
		{
			sr.onTick(tick, now.xp, latestInventory);
		}
		for (LoopRun lr : loops())
		{
			lr.onTick(tick, now.xp, latestInventory);
		}
		SlayerTask st = slayer;
		if (st != null)
		{
			st.onTick(tick, client.getVarpValue(VarPlayerID.SLAYER_COUNT), now.xp);
		}
		RoundTracker rt = minigame;
		if (rt != null)
		{
			rt.onTick(tick, templateRegion(me), now.xp);
			rt.onScore(gameScore(rt.game()), rt.game() != null && "volcanic".equals(rt.game().id)
				? client.getVarbitValue(VarbitID.FOSSIL_MINE_CURRENT_STABILITY) : -1);
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
		boolean lapDone = ag.onTick(tick, now.xp,
			!now.hits.isEmpty(), lapEndTick >= 0 && lapEndTick == tick);
		// Clicks wait for the update of their own tick: a landing on the tick of the next click must count first.
		// A click between ticks is acted on by the game at this tick, so it is timed from here.
		while (!obstacleClicks.isEmpty() && obstacleClicks.peek()[0] <= tick)
		{
			int[] click = obstacleClicks.poll();
			ag.onObstacleClick(click[0], click[1]);
		}
		while (!actedClicks.isEmpty() && actedClicks.peek().due <= tick)
		{
			Acted click = actedClicks.poll();
			click.run.accept(click.due);
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
		pulse.setAlternate(config.metronomeStyle() == SuperHistogramConfig.MetronomeStyle.ALTERNATE);
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
		openServerTick();
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

	/** Test shorthand: a hitsplat the player dealt (or, on the player, took). */
	void handleHit(Actor actor, int type, int amount)
	{
		handleHit(actor, type, amount, System.currentTimeMillis(), true);
	}

	/** Test shorthand, as above. */
	void handleHit(Actor actor, int type, int amount, long nowMs)
	{
		handleHit(actor, type, amount, nowMs, true);
	}

	/**
	 * A damage or block hitsplat. On the local player it is drawn; on an NPC it marks a real combat target only when
	 * mine ({@code Hitsplat.isMine()}), so an NPC someone else is fighting is not.
	 */
	void handleHit(Actor actor, int type, int amount, long nowMs, boolean mine)
	{
		openServerTick();
		if (timeline == null || !isHitType(type) || actor == null)
		{
			return;
		}
		if (actor instanceof NPC)
		{
			if (mine && !ignored((NPC) actor))
			{
				int idx = ((NPC) actor).getIndex();
				boolean firstHit = !npcHitTicks.containsKey(idx);
				npcHitTicks.put(idx, tick);
				lastOwnHitTick = tick;
				if (firstHit && pendingSwingNpc == idx && tick - pendingSwingTick <= PENDING_SWING_TICKS)
				{
					recordAttack(pendingSwingTick);
				}
				pendingSwingNpc = -1;
				markSwing(amount);
			}
			return;
		}
		if (actor != client.getLocalPlayer())
		{
			return;
		}
		if (ignored(swingers.get(attribution.latest(tick))))
		{
			// An enemy on the ignore list (Scurrius's rats): its hit isn't drawn, flashed or counted as a fight.
			return;
		}
		NPC from = swingers.get(attribution.attribute(tick));
		// Drawn in every mode, but a hit on the player never switches the mode by itself.
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.hits.add(new Hit(amount, amount == 0));
		}
		if (from != null ? !stunnedBy(from) : attribution.anySwing(tick))
		{
			// A monster's attack landed on you, hit or miss: you're in a fight even if you never swing back.
			lastAttackedTick = tick;
			if (from != null)
			{
				attackers.put(from.getIndex(), tick);
				if (from.getName() != null)
				{
					lastAttackerName = Text.removeTags(from.getName());
				}
			}
		}
		flashAttacker(from, amount == 0, nowMs);
	}

	/** This monster is on the "Ignore enemies" list. */
	boolean ignored(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return false;
		}
		String text = config.ignoreEnemies();
		if (text == null)
		{
			text = "";
		}
		if (!text.equals(ignoreText))
		{
			ignoreList = IgnoreList.parse(text);
			ignoreText = text;
		}
		return ignoreList.matches(npc.getName());
	}

	/** The player clicked Pickpocket on an NPC or Steal-from on a stall ({@code stall}). */
	void handleThieveClick(String target, boolean stall)
	{
		ThieveRun th = thieve;
		if (th != null)
		{
			String name = target == null ? "" : target.replaceAll("\\s*\\(level-\\d+\\)", "").trim();
			actedClicks.add(new Acted(actedTick(), t -> th.onAttempt(t, name, stall)));
		}
	}

	/** You clicked Lay (a box trap, bird snare): the trap goes on the tile you stand on. */
	void handleLayClick()
	{
		TrapTracker tr = hunt;
		Player me = client.getLocalPlayer();
		net.runelite.api.coords.LocalPoint lp = me == null ? null : me.getLocalLocation();
		if (tr != null && lp != null)
		{
			int tile = objectKey(lp.getSceneX(), lp.getSceneY());
			actedClicks.add(new Acted(actedTick(), t -> tr.onLay(t, tile)));
		}
	}

	/** You clicked a bird house (or the spot for one): its Hunter XP is a bird house run's. */
	void handleBirdhouseClick()
	{
		LoopRun b = birdhouses;
		if (b != null)
		{
			actedClicks.add(new Acted(actedTick(), b::onClick));
		}
	}

	/** The game's own points this round: Tempoross and Volcanic Mine send them; -1 for the others. */
	private int gameScore(RoundTracker.Game g)
	{
		if (g == null)
		{
			return -1;
		}
		switch (g.id)
		{
			case "tempoross":
				return client.getVarbitValue(VarbitID.TEMPOROSS_CURRENT_POINTS);
			case "volcanic":
				return client.getVarbitValue(VarbitID.FOSSIL_MINE_TEMP_PTS);
			default:
				return -1;
		}
	}

	/** The map region you stand in; inside an instance (Tempoross, Volcanic Mine), the region it copies. */
	private int templateRegion(Player me)
	{
		if (me == null)
		{
			return -1;
		}
		LocalPoint lp = me.getLocalLocation();
		WorldPoint wp = lp == null ? me.getWorldLocation() : WorldPoint.fromLocalInstance(client, lp);
		return wp == null ? -1 : wp.getRegionID();
	}

	/** The loop trackers, in the order the bar prefers them; empty before startUp. */
	private List<LoopRun> loops()
	{
		List<LoopRun> out = new ArrayList<>(3);
		for (LoopRun r : new LoopRun[]{runecraft, farming, birdhouses})
		{
			if (r != null)
			{
				out.add(r);
			}
		}
		return out;
	}

	/** The loop on show at {@code tick}, or null. */
	private LoopRun loopShowing(int tick)
	{
		for (LoopRun r : loops())
		{
			if (r.showing(tick))
			{
				return r;
			}
		}
		return null;
	}

	/** You clicked Check, Reset or Dismantle on a trap. */
	void handleTrapClick(int tile, int objectId)
	{
		TrapTracker tr = hunt;
		if (tr != null)
		{
			actedClicks.add(new Acted(actedTick(), t -> tr.onTrapClick(t, tile, objectId)));
		}
	}

	/** An object appeared (a trap laid or changing state). */
	void handleObjectSpawned(int objectId, int sceneX, int sceneY)
	{
		openServerTick();
		TrapTracker tr = hunt;
		if (tr != null && TrapTracker.isTrap(objectId))
		{
			tr.onSpawn(tick, objectKey(sceneX, sceneY), objectId);
		}
	}

	/** A game message; the thieving tracker learns the coin pouch cap from it. */
	void handleChatMessage(String message)
	{
		RoundTracker rt = minigame;
		if (rt != null && message != null)
		{
			rt.onMessage(tick, Text.removeTags(message));
		}
		ThieveRun th = thieve;
		if (th != null && message != null)
		{
			th.onMessage(Text.removeTags(message));
		}
	}

	void handlePickpocketClick(int npcIndex)
	{
		pickpocketNpc = npcIndex;
		pickpocketTick = tick;
	}

	/** You just pickpocketed this NPC: its hit on you is a stun, not a fight. */
	private boolean stunnedBy(NPC npc)
	{
		return npc.getIndex() == pickpocketNpc && tick - pickpocketTick <= PICKPOCKET_STUN_TICKS;
	}

	/**
	 * Called only after a hitsplat has landed on the player. Attribution just picks which hull to
	 * flash; ambiguity flashes nothing.
	 */
	private void flashAttacker(NPC npc, boolean miss, long nowMs)
	{
		// Any monster attacking you, hit or miss; only a pickpocket stun doesn't flash the man.
		if (npc == null || stunnedBy(npc))
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
		openServerTick();
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
		boolean fought = npcHitTicks.remove(npc.getIndex()) != null;
		fought |= attackers.remove(npc.getIndex()) != null;
		if (fought && !fightingAnotherNpc())
		{
			// Your fight is over (it died): don't hold combat for 10 s while you go back to what you were doing.
			combatEndedTick = tick;
		}
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
		npcHitTicks.clear();
		lastOwnHitTick = Integer.MIN_VALUE / 2;
		attackers.clear();
		lastAttackedTick = Integer.MIN_VALUE / 2;
		lastAttackerName = null;
		combatEndedTick = Integer.MIN_VALUE / 2;
		flash = null;
	}

	/**
	 * The player's target is a real combat NPC: it has a combat level and took a hitsplat in the combat window.
	 * Fishing spots, bankers, dialogue and pickpocket targets are not.
	 */
	private boolean realTarget(Actor target, int now)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}
		NPC n = (NPC) target;
		Integer hit = npcHitTicks.get(n.getIndex());
		return hit != null && ModeDetector.realTarget(n.getCombatLevel(), hit, now);
	}

	/** The bar's in-combat test, with the combat hold (cut short when the monster you fought died). */
	private boolean inCombatHeld(Player me, int tickNow, AttackTimer at)
	{
		if (me == null || at == null)
		{
			return false;
		}
		int last = Math.max(Math.max(at.lastAttackTick(), lastOwnHitTick), lastAttackedTick);
		boolean held = last > combatEndedTick;
		return ModeDetector.inCombat(realTarget(me.getInteracting(), tickNow), tickNow,
			held ? last : Integer.MIN_VALUE / 2, ModeDetector.ticks(config.combatHoldSeconds()));
	}

	/** Another monster you hit is still in the combat window. */
	private boolean fightingAnotherNpc()
	{
		for (int hit : npcHitTicks.values())
		{
			if (tick - hit <= ModeDetector.COMBAT_WINDOW)
			{
				return true;
			}
		}
		for (int hit : attackers.values())
		{
			if (tick - hit <= ModeDetector.COMBAT_WINDOW)
			{
				return true;
			}
		}
		return false;
	}

	/** Name of the NPC the player is fighting while the bar is in combat; the last name is kept through gaps. */
	private String fightTarget()
	{
		Player me = client.getLocalPlayer();
		if (!inCombatHeld(me, tick, attack))
		{
			lastFightName = null;
			return null;
		}
		Actor t = me.getInteracting();
		if (t instanceof NPC && ((NPC) t).getCombatLevel() > 0 && ((NPC) t).getName() != null && !ignored((NPC) t))
		{
			lastFightName = Text.removeTags(((NPC) t).getName());
		}
		else if (lastFightName == null)
		{
			// Being attacked without swinging back: name the monster attacking you.
			lastFightName = lastAttackerName;
		}
		return lastFightName;
	}

	void handleAttackClick(int npcIndex)
	{
		attackClickNpc = npcIndex;
		attackClickTick = tick;
	}

	/**
	 * Whether this is the player's first swing at the NPC: they just clicked Attack on it. (An NPC facing the
	 * player is not enough: a pickpocket target that stuns you does that too.)
	 */
	private boolean firstSwing(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}
		NPC n = (NPC) target;
		return attackClickNpc == n.getIndex() && tick - attackClickTick <= 20;
	}

	/** An own animation (other than eating) while targeting a real combat NPC counts as an attack. */
	void handleAnimation(Actor a)
	{
		openServerTick();
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
		if (anim == -1 || anim == EAT_ANIMATION || !attack.canAttack(tick))
		{
			return;
		}
		Actor target = a.getInteracting();
		if (target instanceof NPC && ignored((NPC) target))
		{
			return;
		}
		if (!(realTarget(target, tick) || firstSwing(target)))
		{
			// Maybe an auto-retaliate first swing: only counted once its own hitsplat lands (a pickpocket never
			// puts one on the NPC).
			if (target instanceof NPC && ((NPC) target).getCombatLevel() > 0)
			{
				pendingSwingNpc = ((NPC) target).getIndex();
				pendingSwingTick = tick;
			}
			return;
		}
		recordAttack(tick);
	}

	/** Puts a hitsplat you dealt on the newest swing that caused it (within the last few ticks). */
	private void markSwing(int amount)
	{
		for (int t = tick; t >= tick - PENDING_SWING_TICKS; t--)
		{
			TickRecord r = timeline.peek(t);
			if (r != null && r.attackLate >= 0)
			{
				// A second splat from the same swing (a double hit) adds to it.
				r.swingHit = Math.max(0, r.swingHit) + Math.max(0, amount);
				return;
			}
		}
	}

	private void recordAttack(int at)
	{
		TickRecord r = timeline.at(at);
		if (r != null)
		{
			if (!weaponSpeedKnown)
			{
				learnFromSwing(at);
			}
			r.attackLate = attack.onAttack(at, speed());
		}
		beat.onAttack(at);
	}

	/** The speed in use: the weapon's, else the one learned from your swings, else the default. */
	int speed()
	{
		return weaponSpeedKnown ? weaponSpeed : learnedSpeed > 0 ? learnedSpeed : AttackTimer.DEFAULT_SPEED;
	}

	/**
	 * Unknown weapon: the speed is the shortest of the last three gaps between swings (you can swing late,
	 * never early), once there are two.
	 */
	private void learnFromSwing(int at)
	{
		int gap = at - attack.lastAttackTick();
		if (gap < 1 || gap > 8)
		{
			return;
		}
		swingGaps.addLast(gap);
		while (swingGaps.size() > 3)
		{
			swingGaps.pollFirst();
		}
		if (swingGaps.size() >= 2)
		{
			int min = Integer.MAX_VALUE;
			for (int g : swingGaps)
			{
				min = Math.min(min, g);
			}
			learnedSpeed = min;
		}
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
		openServerTick();
		Map<Integer, Integer> before = latestInventory;
		AgilityCourse ag = agility;
		if (ag != null && regionCourse != null && before != null)
		{
			// Marks of grace picked up on a course (the first inventory after login is only a baseline).
			ag.onMarksPickedUp(counts.getOrDefault(ItemID.GRACE, 0) - before.getOrDefault(ItemID.GRACE, 0));
		}
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
		GatherRun gr = gather;
		actedClicks.add(new Acted(actedTick(), t ->
		{
			if (r != null)
			{
				r.onBankClick(t);
			}
			if (gr != null)
			{
				gr.onBankClick(t);
			}
		}));
	}

	/** The player clicked a tree, rock, fishing spot or logs to light. */
	void handleGatherClick(String skill, int key, String name)
	{
		GatherRun gr = gather;
		if (gr != null)
		{
			actedClicks.add(new Acted(actedTick(), t -> gr.onTargetClick(t, skill, key, name)));
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
			obstacleClicks.add(new int[]{actedTick(), objectId});
		}
	}

	/** A mark of grace spawned on the current course (client thread); ignored on the tick a scene loads. */
	void handleItemSpawned(int itemId, WorldPoint where)
	{
		openServerTick();
		AgilityCourse ag = agility;
		if (ag == null || where == null || itemId != ItemID.GRACE || (long) tick - loadingTick <= 1)
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
		openServerTick();
		TrapTracker tr = hunt;
		if (tr != null)
		{
			tr.onDespawn(objectKey(sceneX, sceneY));
		}
		GatherRun gr = gather;
		// The load's own despawns come in its tick's update, which can already be a tick past the load.
		if (gr != null && (long) tick - loadingTick > 2)
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
			// The cast happens, and its XP arrives, on the tick after the click.
			a.onCastClick(actedTick(), alchCoins(itemId));
		}
	}

	/** The spellbook's interface group. */
	static final int SPELLBOOK_GROUP = 218;

	/**
	 * The spell a click casts outside combat: "Cast" on an item ("Plank Make -> Mahogany logs"), on an object
	 * (Charge Water Orb) or on the spellbook itself (Spin Flax); null for anything else. Spells on NPCs and
	 * players are combat; high alch has its own tracker.
	 */
	static String skillingSpell(MenuAction a, String option, String target, int group)
	{
		if (a == null || !"Cast".equals(option) || target == null)
		{
			return null;
		}
		String t = Text.removeTags(target);
		int arrow = t.indexOf(" -> ");
		String name = (arrow >= 0 ? t.substring(0, arrow) : t).trim();
		if (name.isEmpty() || name.startsWith("High Level Alchemy"))
		{
			return null;
		}
		switch (a)
		{
			case WIDGET_TARGET_ON_WIDGET:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				return name;
			case CC_OP:
				return group == SPELLBOOK_GROUP ? name : null;
			default:
				return null;
		}
	}

	/** You cast a skilling spell (not high alch); {@code itemId} is its target item, 0 for none. */
	void handleSpellClick(String spellName, int itemId)
	{
		SpellRun sr = spell;
		if (sr != null)
		{
			actedClicks.add(new Acted(actedTick(), t -> sr.onCastClick(t, spellName, itemId)));
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
		openServerTick();
		int known = itemId < 0 ? AttackTimer.DEFAULT_SPEED : weaponSpeed(itemId);
		boolean changed = known != weaponSpeed || (known > 0) != weaponSpeedKnown;
		weaponSpeedKnown = known > 0;
		weaponSpeed = known > 0 ? known : AttackTimer.DEFAULT_SPEED;
		if (changed)
		{
			learnedSpeed = -1;
			swingGaps.clear();
		}
		// The beat is yours to set (Beat every), not your weapon's: a weapon swap leaves it alone.
	}

	/** The beat starts only from this player-pressed hotkey. */
	private boolean beatAlways()
	{
		return config.beatRuns() == SuperHistogramConfig.BeatRuns.ALWAYS;
	}

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
		beat.press(tick, config.beatTicks(), beatAlways() || inCombatHeld(me, tick, attack));
	}

	private int weaponSpeed(int itemId)
	{
		ItemStats stats = itemManager == null ? null : itemManager.getItemStats(itemId);
		ItemEquipmentStats eq = stats == null ? null : stats.getEquipment();
		if (itemManager == null)
		{
			return AttackTimer.DEFAULT_SPEED;
		}
		// -1: the game has no attack speed for this item; it is learned from your swings.
		return eq != null && eq.getAspeed() > 0 ? eq.getAspeed() : -1;
	}

	void handleClick(long nowMs)
	{
		if (clicks != null)
		{
			lastClickLate = clicks.late(nowMs);
			clicks.onClick(tick, nowMs);
		}
	}

	/** Gear switch, spec, walk or drink: the menu click that is one; null for anything else. */
	static InputMark.Kind inputKind(MenuAction action, String option)
	{
		if (action == MenuAction.WALK)
		{
			return InputMark.Kind.WALK;
		}
		String o = option == null ? "" : Text.removeTags(option).trim();
		if (o.equals("Wield") || o.equals("Wear") || o.equals("Hold") || o.equals("Equip"))
		{
			return InputMark.Kind.GEAR;
		}
		if (o.equals("Drink"))
		{
			return InputMark.Kind.DRINK;
		}
		if (o.endsWith("Special Attack"))
		{
			return InputMark.Kind.SPEC;
		}
		return null;
	}

	void handleInput(long nowMs, InputMark.Kind kind)
	{
		if (clicks != null)
		{
			clicks.onInput(tick, nowMs, kind);
		}
	}

	void handlePrayerClick(long nowMs)
	{
		if (clicks != null)
		{
			clicks.onPrayerClick(tick, nowMs);
		}
	}

	void handleOverheadClick(long nowMs, String kind)
	{
		if (clicks != null)
		{
			clicks.onOverheadClick(tick, nowMs, kind);
		}
	}

	/**
	 * {kind turned on} for a click on an overhead prayer in the prayer book ({null} when it turns one off);
	 * null when the click is on some other prayer.
	 */
	static String[] overheadClick(String option, String target)
	{
		String t = target == null ? "" : target.replaceAll("<[^>]*>", "").trim().toLowerCase();
		String kind = t.equals("protect from melee") ? "melee" : t.equals("protect from missiles") ? "ranged"
			: t.equals("protect from magic") ? "magic"
			: t.equals("retribution") || t.equals("redemption") || t.equals("smite") ? "other" : null;
		if (kind == null)
		{
			return null;
		}
		return new String[]{"Activate".equals(option) ? kind : null};
	}

	/** The day {@link #alchToday} belongs to (local date), or null before it's been read for this account. */
	private String alchTodayDate;
	private long alchToday;
	/** Saved per account as "date=coins", so only today's total is ever kept. */
	private static final String ALCH_TODAY_KEY = "alchToday";

	/** Coins alched today on this account, across logins; -1 when there's no saved profile to read. */
	long alchToday()
	{
		if (configManager == null)
		{
			return -1;
		}
		String date = java.time.LocalDate.now().toString();
		if (!date.equals(alchTodayDate))
		{
			alchTodayDate = date;
			alchToday = 0;
			String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, ALCH_TODAY_KEY);
			if (saved != null && saved.startsWith(date + "="))
			{
				try
				{
					alchToday = Long.parseLong(saved.substring(date.length() + 1));
				}
				catch (NumberFormatException ignored)
				{
					alchToday = 0;
				}
			}
		}
		return alchToday;
	}

	private void addAlchToday(long coins)
	{
		if (alchToday() < 0)
		{
			return;
		}
		alchToday += coins;
		configManager.setRSProfileConfiguration(CONFIG_GROUP, ALCH_TODAY_KEY, alchTodayDate + "=" + alchToday);
	}

	void handleProfileChanged()
	{
		handleProfileChanged(System.currentTimeMillis());
	}

	/**
	 * The active RuneScape profile changed: the saved bests belong to another account now, and the session
	 * totals (XP, alch gold) and the session time behind every per-hour rate start again.
	 */
	void handleProfileChanged(long nowMs)
	{
		if (xp != null)
		{
			xp.resetSession();
		}
		if (alch != null)
		{
			alch.reset();
		}
		alchTodayDate = null;
		if (clock != null)
		{
			clock.resetSession(nowMs);
		}
		AgilityCourse ag = agility;
		if (ag != null)
		{
			ag.reset();
			if (timeline != null)
			{
				ag.rebind(timeline);
			}
		}
		ThieveRun th = thieve;
		if (th != null)
		{
			th.reset();
		}
		TrapTracker tr = hunt;
		if (tr != null)
		{
			tr.reset();
		}
		SpellRun sr = spell;
		if (sr != null)
		{
			sr.reset();
		}
		for (LoopRun lr : loops())
		{
			lr.reset();
		}
		if (minigame != null)
		{
			minigame.reset();
		}
		if (slayer != null)
		{
			slayer.reset();
		}
		lapEndTick = -1;
		obstacleClicks.clear();
		actedClicks.clear();
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
				if (hunt != null)
				{
					hunt.onSceneChange();
				}
				break;
			case HOPPING:
				// A new world has its own ping.
				pings.clear();
				clock.onHopping();
				clearFlash();
				movement.reset();
				lastLocation = null;
				break;
			case LOGIN_SCREEN:
				loggedIn = false;
				clearFlash();
				clock.onLoginScreen(nowMs);
				xp.onLoginScreen();
				modeDetector.reset();
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
				fight = new FightCollector(timeline);
				lastFightName = null;
				attackClickNpc = -1;
				flicks.reset();
				run = new InventoryRun(timeline);
				alch.rebind(timeline);
				gather = new GatherRun(timeline);
				// Another account may log in next: forget the course and every best (they reload per profile).
				agility.reset();
				agility.rebind(timeline);
				thieve.rebind(timeline);
				hunt.rebind(timeline);
				spell.rebind(timeline);
				for (LoopRun lr : loops())
				{
					lr.rebind(timeline);
				}
				minigame.rebind(timeline);
				lapEndTick = -1;
				regionCourse = null;
				obstacleClicks.clear();
				actedClicks.clear();
				latestFreeSlots = -1;
				loadingTick = Integer.MIN_VALUE;
				latestInventory = null;
				tick = -1;
		tickOpened = false;
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
		return buildState(System.currentTimeMillis());
	}

	/** As above, at the given wall-clock time. Package-private for tests. */
	BarState buildState(long now)
	{
		if (!loggedIn || timeline == null)
		{
			return null;
		}
		IdleTracker idleNow = idle;
		boolean afk = idleNow != null && idleNow.isAfk();
		Player me = client.getLocalPlayer();
		int tickNow = timeline.newestTick();
		AttackTimer at = attack;
		AgilityCourse agilityNow = agility;
		boolean agilityOn = agilityNow != null && agilityNow.showing(tickNow);
		// Hits on the player (falls, Wintertodt, stuns) never switch the mode; only a real target or own attack does.
		boolean combat = inCombatHeld(me, tickNow, at);
		InventoryRun runNow = run;
		AlchTracker alchNow = alch;
		GatherRun gatherNow = gather;
		ThieveRun thieveNow = thieve;
		TrapTracker huntNow = hunt;
		SpellRun spellNow = spell;
		LoopRun loopNow = loopShowing(tickNow);
		RoundTracker roundsNow = minigame;
		boolean roundsOn = roundsNow != null && roundsNow.showing(tickNow);
		// In a minigame its rounds win: chopping and fletching at Wintertodt are not a gathering or production run.
		Mode skilling = roundsOn ? Mode.ROUNDS : ModeDetector.skilling(alchNow != null && alchNow.showing(tickNow),
			spellNow != null && spellNow.showing(tickNow), runNow != null && runNow.showing(tickNow), agilityOn,
			loopNow != null, thieveNow != null && thieveNow.showing(tickNow),
			huntNow != null && huntNow.showing(tickNow), gatherNow != null && gatherNow.showing(tickNow));
		Mode mode = modeDetector.detect(config.mode(), afk, combat, skilling, config.prayerMode(), config.baseline());
		if (alchNow != null && mode != Mode.ALCH && mode != Mode.AFK && mode != Mode.GENERAL && mode != Mode.MINIMAL)
		{
			// Another activity took the bar (salvaging, a fight): the next alch starts a new run.
			alchNow.breakRun();
		}
		// The monster's name from the first combat tick (the fight span only learns it on the next game tick).
		String foe = lastFightName != null ? lastFightName : npcName(me == null ? null : me.getInteracting());
		return BarState.builder()
			.timeline(timeline)
			.nowTick(timeline == null ? -1 : timeline.newestTick())
			.xp(xp)
			.idle(idleNow)
			.clock(clock)
			.runEnergyPct(runEnergyPct)
			.modeName(mode == Mode.PRODUCTION ? ("Sailing".equals(runNow.skill()) ? "Sorting" : runNow.skill())
				: mode == Mode.CASTING && spellNow.spell() != null ? spellNow.spell()
				: mode == Mode.RUNS && loopNow != null ? LoopView.name(loopNow.kind())
				: mode == Mode.ROUNDS && roundsNow != null && roundsNow.game() != null ? roundsNow.game().name
				: mode == Mode.GATHERING ? ("Sailing".equals(gatherNow.skill()) ? "Salvaging" : gatherNow.skill())
				: combatMode(mode) && foe != null ? foe : modeName(mode))
			.modeSub(mode == Mode.GATHERING ? gatherSub(gatherNow)
				: mode == Mode.AGILITY ? agilitySub(agilityNow)
				: mode == Mode.THIEVING ? thieveSub(thieveNow)
				: mode == Mode.CASTING ? "auto \u00b7 Magic"
				: combatMode(mode) && foe != null ? modeName(mode) + " \u00b7 " + modeSub(mode) : modeSub(mode))
			.showLoginTime(config.showLoginTime())
			.mode(mode)
			.nowMs(now)
			.tickMs(tickMs)
			.smooth(config.smoothScroll())
			.nowLinePct(config.nowLinePercent())
			.beat(beat)
			.flicks(flicks)
			.weaponSpeed(speed())
			.nextAttackTick(combat && at != null && at.readyTick() > tickNow && (weaponSpeedKnown || learnedSpeed > 0)
				? at.readyTick() : -1)
			.nextAttackLearned(!weaponSpeedKnown)
			.clickWindowMs(config.clickWindowMs())
			.actionsRow(config.actionsRow())
			.showEatCosts(config.showEatCosts())
			.run(runNow)
			.timeToFinish(config.timeToFinish())
			.wastedTicks(config.wastedTicks())
			.xpDrops(config.xpDrops())
			.xpDots(config.xpDots())
			.alch(alchNow)
			.alchToday(alchToday())
			.gather(gatherNow)
			.alchTopRow(config.alchTopRow())
			.rightSide(config.rightSide())
			.agility(agilityNow)
			.thieve(thieveNow)
			.hunt(huntNow)
			.spell(spellNow)
			.loop(loopNow)
			.rounds(roundsNow)
			.slayer(slayer)
			.build();
	}

	private static String agilitySub(AgilityCourse a)
	{
		AgilityCourses.Course c = a == null ? null : AgilityCourses.byId(a.course());
		return c == null ? "auto" : "auto \u00b7 " + c.name;
	}

	private static String thieveSub(ThieveRun t)
	{
		String name = t == null ? null : t.target();
		return name == null || name.isEmpty() ? "auto" : "auto \u00b7 " + name;
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
			case THIEVING:
			case HUNTER:
			case CASTING:
			case RUNS:
			case ROUNDS:
				return "auto";
			case LAZY_FLICK:
				return speed() + "t \u00b7 " + config.beatKey();
			default:
				return "baseline";
		}
	}

	private static String npcName(Actor a)
	{
		return a instanceof NPC && ((NPC) a).getCombatLevel() > 0 && ((NPC) a).getName() != null
			? Text.removeTags(((NPC) a).getName()) : null;
	}

	private static boolean combatMode(Mode mode)
	{
		return mode == Mode.LAZY_FLICK || mode == Mode.ONE_TICK;
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
			case THIEVING:
				return "Thieving";
			case HUNTER:
				return "Hunter";
			case CASTING:
				return "Magic";
			case RUNS:
				return "Runs";
			case ROUNDS:
				return "Minigame";
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
