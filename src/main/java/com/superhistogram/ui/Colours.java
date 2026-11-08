package com.superhistogram.ui;

import java.awt.Color;

/** The four user-overridable colours, as one immutable set. */
public final class Colours
{
	public static final Colours DEFAULT = new Colours(Palette.GROUND, Palette.DAMAGE, Palette.MINT, Palette.RUN_ENERGY);

	public final Color ground, damage, mint, runEnergy;

	public Colours(Color ground, Color damage, Color mint, Color runEnergy)
	{
		this.ground = ground;
		this.damage = damage;
		this.mint = mint;
		this.runEnergy = runEnergy;
	}

	/** A set where any null colour falls back to its default. */
	public static Colours orDefaults(Color ground, Color damage, Color mint, Color runEnergy)
	{
		return new Colours(ground != null ? ground : Palette.GROUND, damage != null ? damage : Palette.DAMAGE,
			mint != null ? mint : Palette.MINT, runEnergy != null ? runEnergy : Palette.RUN_ENERGY);
	}
}
