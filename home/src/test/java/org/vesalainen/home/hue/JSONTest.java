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

import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class JSONTest
{
    
    public JSONTest()
    {
    }

    @Test
    public void testBuilder1()
    {
        Object obj = JSON.build("/foo/bar", 123)
                .set("/foo/abc", "asd")
                .set("/foo/boo", true)
                .get()
                ;
        assertEquals(123, JSON.get(obj, "/foo/bar"));
        assertEquals("asd", JSON.get(obj, "/foo/abc"));
        assertEquals(true, JSON.get(obj, "/foo/boo"));
        Set<String> set = JSON.keySet(obj);
        assertEquals(3, set.size());
        assertTrue(set.contains("/foo/bar"));
        assertTrue(set.contains("/foo/abc"));
        assertTrue(set.contains("/foo/boo"));
    }
    @Test
    public void testBuilder2()
    {
        Object obj = JSON.build("/foo/0", 123)
                .set("/foo/1", "asd")
                .set("/foo/2", false)
                .get()
                ;
        assertEquals(123, JSON.get(obj, "/foo/0"));
        assertEquals("asd", JSON.get(obj, "/foo/1"));
        assertEquals(false, JSON.get(obj, "/foo/2"));
    }
    
}
