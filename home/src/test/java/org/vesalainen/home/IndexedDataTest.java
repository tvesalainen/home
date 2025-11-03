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
package org.vesalainen.home;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class IndexedDataTest
{
    
    public IndexedDataTest()
    {
    }

    @Test
    public void test1()
    {
        IndexedData q = new IndexedData(Duration.ofMinutes(15), Duration.ofHours(1));
        q.addSupplier("z", this::a, "x", "y");
        q.addSupplier("b", f::new, "x", "y");
        int index = q.getIndex();
        q.set(index, "x", 1.0);
        q.set(index, "y", 2.0);
        assertEquals(3.0, q.get(index, "z"));
        assertEquals(f.class, q.get(index, "b").getClass());
    }
    @Test
    public void test2()
    {
        ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
        IndexedData q = new IndexedData(Duration.ofMinutes(15), Duration.ofHours(1));
        int start = q.getIndex();
        q.set(start, "x", 1);
        pool.submit(()->read(q));
        for (int ii=0;ii<5;ii++)
        {
            q.set(start+ii, "x", ii+2);
        }
    }
    private void read(IndexedData q)
    {
        int start = q.getIndex();
        for (int ii=0;ii<4;ii++)
        {
            double x = q.getAndWait(start+ii, "x");
            System.err.println(x);
        }
    }
    private Object a(Object... p)
    {
        return (Double)p[0]+(Double)p[1];
    }
    private class f
    {
        double x;
        double y;

        public f(Object... p)
        {
            this.x = (double) p[0];
            this.y = (double) p[1];
        }

        @Override
        public String toString()
        {
            return "f{" + "x=" + x + ", y=" + y + '}';
        }
        
    }
}
