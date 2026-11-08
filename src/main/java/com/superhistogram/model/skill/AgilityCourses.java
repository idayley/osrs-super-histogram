package com.superhistogram.model.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rooftop and other agility courses: region IDs and lap-end tiles.
 * The table is copied from RuneLite 1.13.6 {@code net.runelite.client.plugins.agility.Courses}
 * (BSD 2-Clause, Copyright (c) 2017 Adam and the RuneLite contributors).
 */
public final class AgilityCourses
{
	public static final class Course
	{
		public final String id;
		public final String name;
		public final int region;
		/** Lap-end tiles as {x, y, plane}. */
		public final int[][] ends;

		Course(String id, String name, int region, int[]... ends)
		{
			this.id = id;
			this.name = name;
			this.region = region;
			this.ends = ends;
		}
	}

	private static final List<Course> ALL;

	static
	{
		List<Course> l = new ArrayList<>();
		l.add(new Course("GNOME", "Gnome", 9781, t(2484, 3437), t(2487, 3437)));
		l.add(new Course("DRAYNOR", "Draynor", 12338, t(3103, 3261)));
		l.add(new Course("AL_KHARID", "Al Kharid", 13105, t(3299, 3194)));
		l.add(new Course("VARROCK", "Varrock", 12853, t(3236, 3417)));
		l.add(new Course("BARBARIAN", "Barbarian", 10039, t(2543, 3553)));
		l.add(new Course("CANIFIS", "Canifis", 13878, t(3510, 3485)));
		l.add(new Course("FALADOR", "Falador", 12084,
			t(3029, 3332), t(3029, 3333), t(3029, 3334), t(3029, 3335)));
		l.add(new Course("WILDERNESS", "Wilderness", 11837,
			t(2993, 3933), t(2994, 3933), t(2995, 3933)));
		l.add(new Course("SEERS", "Seers'", 10806, t(2704, 3464)));
		l.add(new Course("POLLNIVNEACH", "Pollnivneach", 13358, t(3363, 2998)));
		l.add(new Course("RELLEKKA", "Rellekka", 10553, t(2653, 3676)));
		l.add(new Course("ARDOUGNE", "Ardougne", 10547, t(2668, 3297)));
		ALL = Collections.unmodifiableList(l);
	}

	private AgilityCourses()
	{
	}

	private static int[] t(int x, int y)
	{
		return new int[] {x, y, 0};
	}

	/** The course for a region, or null off course. */
	public static Course byRegion(int regionId)
	{
		for (Course c : ALL)
		{
			if (c.region == regionId)
			{
				return c;
			}
		}
		return null;
	}

	public static Course byId(String id)
	{
		for (Course c : ALL)
		{
			if (c.id.equals(id))
			{
				return c;
			}
		}
		return null;
	}

	public static boolean isLapEnd(String id, int x, int y, int plane)
	{
		Course c = byId(id);
		if (c == null)
		{
			return false;
		}
		for (int[] e : c.ends)
		{
			if (e[0] == x && e[1] == y && e[2] == plane)
			{
				return true;
			}
		}
		return false;
	}

	public static List<Course> all()
	{
		return ALL;
	}
}
