package com.superhistogram.model;

/** The Mode config: auto-detect, or pin one view. */
public enum ModeSetting
{
	AUTO(null),
	GENERAL(Mode.GENERAL),
	MINIMAL(Mode.MINIMAL),
	AFK(Mode.AFK),
	LAZY_FLICK(Mode.LAZY_FLICK),
	ONE_TICK(Mode.ONE_TICK),
	PRODUCTION(Mode.PRODUCTION),
	ALCH(Mode.ALCH),
	GATHERING(Mode.GATHERING),
	AGILITY(Mode.AGILITY);

	private final Mode mode;

	ModeSetting(Mode mode)
	{
		this.mode = mode;
	}

	/** The pinned view, or null for auto-detect. */
	public Mode mode()
	{
		return mode;
	}
}
