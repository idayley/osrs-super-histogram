package com.superhistogram.model.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enemies the bar ignores: a comma list of names, case-insensitive, where {@code *} matches anything
 * ("Giant rat, *imp*").
 */
public final class IgnoreList
{
	private static final IgnoreList EMPTY = new IgnoreList(new ArrayList<>());
	private final List<Pattern> patterns;

	private IgnoreList(List<Pattern> patterns)
	{
		this.patterns = patterns;
	}

	public static IgnoreList parse(String csv)
	{
		List<String> names = names(csv);
		if (names.isEmpty())
		{
			return EMPTY;
		}
		List<Pattern> ps = new ArrayList<>();
		for (String n : names)
		{
			List<String> parts = new ArrayList<>();
			for (String part : n.toLowerCase(Locale.ROOT).split("\\*", -1))
			{
				parts.add(Pattern.quote(part));
			}
			ps.add(Pattern.compile(String.join(".*", parts)));
		}
		return new IgnoreList(ps);
	}

	public boolean matches(String name)
	{
		if (name == null || patterns.isEmpty())
		{
			return false;
		}
		String n = clean(name).toLowerCase(Locale.ROOT);
		for (Pattern p : patterns)
		{
			if (p.matcher(n).matches())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The list with {@code name} taken off when it's written there, otherwise added. A name only a wildcard
	 * covers is left as is (taking it off would mean rewriting the pattern).
	 */
	public static String toggle(String csv, String name)
	{
		String n = clean(name);
		List<String> names = names(csv);
		for (int i = 0; i < names.size(); i++)
		{
			if (names.get(i).equalsIgnoreCase(n))
			{
				names.remove(i);
				return String.join(", ", names);
			}
		}
		if (parse(csv).matches(n))
		{
			return String.join(", ", names);
		}
		names.add(n);
		return String.join(", ", names);
	}

	/** The name is written on the list as itself (not only covered by a wildcard). */
	public static boolean lists(String csv, String name)
	{
		String n = clean(name);
		for (String s : names(csv))
		{
			if (s.equalsIgnoreCase(n))
			{
				return true;
			}
		}
		return false;
	}

	private static List<String> names(String csv)
	{
		List<String> out = new ArrayList<>();
		if (csv == null)
		{
			return out;
		}
		for (String s : csv.split(","))
		{
			String t = s.trim();
			if (!t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}

	private static String clean(String name)
	{
		return name.replaceAll("<[^>]*>", "").trim();
	}
}
