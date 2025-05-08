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

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class ArrayMergerTest
{
    
    public ArrayMergerTest()
    {
    }

    @Test
    public void test1()
    {
        ArrayMerger am = new ArrayMerger("/foo/#/bar");
        assertTrue(am.matches("/foo/5/bar"));
        assertEquals("12", am.getIndexOf("/foo/12/bar"));
        Set<String> set = new HashSet<>();
        set.add("/foo/0/bar");
        set.add("/foo/0/type:goo");
        set.add("/foo/1/bar");
        set.add("/foo/1/type:goo");
        Set<String> res = new HashSet<>();
        res.add("/foo/0/type:act");
        res.add("/foo/1/type:act");
        //assertEquals(res, am.merge(set, "/foo/#/type:act"));
    }
    
}
