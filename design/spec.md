# Super Histogram — design spec

A RuneLite plugin: one resizable bottom bar (default 1026 × 62 px, sits where the chatbox is) that is a scrolling tick timeline, worth leaving on all the time. It auto-detects what you are doing and shows the right view. Mockups for every section are in `design/mockups/`.

## Rules boundary (Jagex third-party client guidelines)

- Show only what already happened, or what the player started themselves.
- Enemy-tied beats start only from the player's hotkey (default F). The player's own rhythms (weapon speed, skilling actions) may start automatically.
- Never: next-attack prediction, attack counters, prayer suggestions, boss-mechanic timers, projectile or impact locations, or anything indicating which player an NPC is focused on.
- Wintertodt / Tempoross / Guardians of the Rift: only the player's own actions.

## Grammar (every mode)

**One timeline.** The bar always draws everything that happened, in fixed lanes, in every mode. A mode is a *lens*: it dims lanes it doesn't care about, adds its own helpers on top, and sets the readouts. Switching mode never moves, hides or re-scales anything on the timeline (mockup: `design/mockups/one-timeline.html`).

Lanes, top to bottom (3-row bar):

- **Top:** what came to you — hits on you (yellow `#ffd23f` line + number; blocked/zero = faint grey `#5c636b`), XP drops (+ item / mark-of-grace icons, "burnt"), gold.
- **Doing:** what you are doing — walking/running (faint), fighting (target name), the skilling activity (production run, gathering pill with roll notches, alch casts, agility obstacles), else the generic animation pill. A skilling or fight pill hides the generic one for the ticks it covers.
- **Prayer:** prayer pills in prayer colours, from the click that turned them on to the click that turned them off, with prayer points used ("3 pts").
- **Inputs:** your clicks, attacks (sword), eats (fish + cost), bank clicks, depletion marks.
- **Ruler** with the "now" line; history scrolls left.

**Lens helpers** (only in their mode): beat carets and "+N late" (lazy flick), flick verdicts (1-tick), finish outline + flag and "+N" wasted (production), "+N" re-click (gathering), "+N" lost and next-ready caret (alch), lap lines and "+N" lost (agility), idle pill (AFK). Minimal dims every lane but XP.

- **Left readout:** mode name (bold) + sub line (e.g. "auto · questing").
- **Right readout:** one headline number + one small line; login time faint at the far right.

## Colours

Bar ground `rgba(20,23,27,.92)`, text `#e9ecef`, dim `#7d8590`. Prayer melee `#d0313a`, ranged `#64ab31`, magic `#1499dc`, other `#9150d0`. Damage `#ffd23f`. Gold is also yellow but always carries a coin icon; high alch shows the coins received per cast (its High Alchemy value, always positive). Mint `#5ee0c8` = good / on time / done. White = attention (+N, Late). Run energy `#e8c86a` (white under 20%). Skill text colours: Str `#8fd694`, HP `#f2a7bd`, Slay `#c7a3ff`, Mag `#8fc3ff`, Fle `#7fd6c2`, Fish `#8fd0e8`, Cook `#f0b97a`, Agility `#7fa7c9`, Thieving `#c58fd6`, Attack `#e08a7a`, Defence `#9fb4d9` (other skills: pick distinct pastels). Font: Outfit.

## Feedback

- **Metronome** (Off / Every tick / Cue tick only): a faint white inset outline blinks every tick; bright on the click-cue tick.
- **Blink when done:** the whole bar ground flashes mint twice (~1 s). It replaces that tick's metronome blink.

## Resizing

Any width and height. Scale `u = height / 62`; tick width `max(18, 32·u)`. Wider = more history, nothing stretches. Detail drops in this order:
- height < 56: inputs lane hidden; doing and prayer lanes share the middle (each half height);
- height < 40: one row — the doing pill with prayer as a thin strip under it, hits become small notches on top;
- width < 720: right readout hidden;
- width < 480: left readout hidden.

## Modes

- **General (baseline):** faint pill while walking/running (running pill shows energy cost, e.g. "running 71→64%"), solid pill while animating; XP + hits on top; clicks below. Right: session XP + last drop.
- **AFK:** auto after 60 s without input, unless in combat (a fight with no clicks stays a fight). One long pill "idle m:ss since last click"; right shows a mint countdown to the 5-minute idle logout; the bar flashes mint at 4:30 idle.
- **Minimal:** XP drops only; right shows XP/hr and time to next level.
- **Combat — lazy flick:** F press starts the beat at weapon speed; prayer pills; hits; actions row toggle Off / Attacks / Attacks + eats; eats show their attack-timer cost (+3, +2 karambwan/halibut/gnome, +5 combo, "free" if the timer had already run down); potions not shown. Right: trail of recent beats + verdict (✓ / Late / Early).
- **Combat — 1-tick flick:** dot per tick placed by in-tick click time; hollow = split, × = skipped, long pill = draining; right: in-tick timing histogram + streak.
- **Hull flash on hit:** when an enemy's hitsplat lands on you, its outline flashes yellow and fades over ~0.5 s; misses faint grey; off at bosses by default.
- **Production (cooking, fletching, herblore, crafting, smithing, bones on altar):** inventory pill fills, XP drops, finish flag ahead, exact countdown (ticks × 0.6 s), white "+N" wasted before the bank click.
- **High alch:** 5-tick cast pills, coins received per cast (High Alchemy value, coin icon; "?" if unknown), top row Gold / XP / Both; right: session coins and /hr, or XP and time to level.
- **Multi-XP:** each skill's XP as its own coloured number side by side; frequent skills (every 1–2 ticks) shown as coloured dots, hidden when a number group occupies the space.
- **Gathering (woodcutting, mining, fishing, firemaking):** pill per resource with roll notches, item + XP drops, depletion mark + "+N" re-click delay, "~" estimated time to full.
- **Agility:** pill per obstacle numbered in course order, lap-start line, white "+N" ticks stood still before the next click, XP + mark-of-grace on top; right: lap time, best lap, ticks lost, slowest obstacle. Same tick width as every other mode (the timeline never re-scales).

## Settings

See `design/mockups/settings-panel.html` for the panel and defaults: Bar (height in px, mode auto/pinned, baseline General, AFK after 60 s, AFK warning on, run energy on, login time on), Feedback (metronome Off / every tick / beat ticks / every N ticks (default 4, from your last attack), blink On), Combat (lazy flick, F hotkey, actions row Attacks, eat costs on, hull flash on / not at bosses / misses faint), XP & gold (Numbers, dots on, alch Gold, right side per hour), Skilling (time to finish on, +N on, agility compare Best), Colours.
