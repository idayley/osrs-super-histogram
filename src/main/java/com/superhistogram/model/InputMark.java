package com.superhistogram.model;

/** A non-attack input shown in the inputs lane: what it was and where in the tick it reached the server. */
public final class InputMark
{
	public enum Kind
	{
		/** Wield, wear or hold: a gear switch. */
		GEAR,
		/** Special attack toggled (orb or combat tab). */
		SPEC,
		/** A walk click. */
		WALK,
		/** Drinking a potion. */
		DRINK
	}

	public final Kind kind;
	/** In-tick position, 0..1. */
	public final float at;

	public InputMark(Kind kind, float at)
	{
		this.kind = kind;
		this.at = at;
	}
}
