/*
 * Copyright (C) 2024 Timo Vesalainen <timo.vesalainen@iki.fi>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vesalainen.home.hue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class ArrayMerger
{
    private String start;
    private String end;
    private int startLength;
    private int endLength;

    public ArrayMerger(String key)
    {
        String[] split = key.split("#");
        if (split.length != 2)
        {
            throw new IllegalArgumentException(key);
        }
        this.start = split[0];
        this.end = split[1];
        this.startLength = start.length();
        this.endLength = end.length();
    }

    public boolean matches(String str)
    {
        return str.startsWith(start) && str.endsWith(end);
    }
    public String getIndexOf(String str)
    {
        return str.substring(startLength, str.length()-endLength);
    }
    public Set<String> merge(Set<String> src, String trg)
    {
        return merge(src, Collections.singleton(trg));
    }
    public Set<String> merge(Set<String> src, Collection<String> trg)
    {
        Set<String> set = new HashSet<>();
        for (String s : src)
        {
            if (s.startsWith("/configuration/when"))
            {
                if (matches(s))
                {
                    String idx = getIndexOf(s);
                    for (String t : trg)
                    {
                        set.add(t.replace("#", idx));
                    }
                }
                else
                {
                    set.add(s);
                }
            }
        }
        return set;
    }
    public boolean matches(Set<String> src)
    {
        for (String s : src)
        {
            if (matches(s))
            {
                return true;
            }
        }
        return false;
    }
    @Override
    public String toString()
    {
        return start + "#" + end;
    }
    
}
