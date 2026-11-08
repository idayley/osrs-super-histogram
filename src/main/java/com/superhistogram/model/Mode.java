package com.superhistogram.model;

/** Which view the bar shows. Later phases add values. */
public enum Mode
{
	GENERAL,
	AFK,
	MINIMAL,
	LAZY_FLICK,
	ONE_TICK,
	PRODUCTION,
	ALCH,
	GATHERING,
	AGILITY,
	THIEVING,
	HUNTER,
	CASTING,
	/** Runecraft trips, farm runs, bird house runs. */
	RUNS,
	/** Minigame rounds: Wintertodt, Tempoross, Guardians of the Rift, Zalcano, Volcanic Mine. */
	ROUNDS
}
