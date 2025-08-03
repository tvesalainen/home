/*
 * Copyright (C) 2025 Timo Vesalainen <timo.vesalainen@iki.fi>
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

import java.util.EnumSet;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class CheckList<E extends Enum<E>> extends JavaLogging
{
    private final EnumSet<E> all;
    private EnumSet<E> work;
    private final String name;
    public CheckList(Class<E> cls, String name)
    {
        super(CheckList.class);
        this.all = EnumSet.allOf(cls);
        this.work = EnumSet.noneOf(cls);
        this.name = name;
    }
    
    public boolean done(E deed)
    {
        boolean done = isDone(deed);
        boolean ready = ready();
        if (!done)
        {
            info("%s %s done", name, deed);
        }
        work.add(deed);
        if (ready() && !ready)
        {
            info("%s %s done and ready!", name, deed);
        }
        return !done;
    }
    public boolean isDone(E... deeds)
    {
        for (E e : deeds)
        {
            if (!work.contains(e))
            {
                return false;
            }
        }
        return true;
    }
    public boolean ready()
    {
        return all.equals(work);
    }

    public boolean clear(E deed)
    {
        return work.remove(deed);
    }
    public void clear()
    {
        work.clear();
    }

    @Override
    public String toString()
    {
        if (ready())
        {
            return "CheckList{ready}";
        }
        else
        {
            EnumSet<E> clone = all.clone();
            clone.removeAll(work);
            return "CheckList{"+clone+"}";
        }
    }
}  
