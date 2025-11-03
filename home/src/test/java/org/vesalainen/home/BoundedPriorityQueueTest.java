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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class BoundedPriorityQueueTest
{
    
    public BoundedPriorityQueueTest()
    {
    }

    @Test
    public void test1()
    {
        BoundedPriorityQueue<Integer> pq = new BoundedPriorityQueue<>(4);
        pq.offer(6);
        pq.offer(4);
        assertEquals(4, pq.peek());
        assertEquals(4, pq.poll());
        pq.offer(2);
        assertEquals(2, pq.peek());
        pq.offer(0);
        assertEquals(0, pq.peek());
        pq.offer(3);
        assertEquals(0, pq.poll());
        pq.offer(4);
        pq.offer(14);
        pq.offer(4);
    }
    @Test
    public void test2()
    {
        BoundedPriorityQueue<Integer> pq = new BoundedPriorityQueue<>(20);
        for (int ii=0;ii<18;ii++)
        {
            pq.offer(ii);
        }
        pq.removeIf((Integer i)->i==5);
        String toString = pq.toString();
        assertEquals(17, pq.size());
        pq.removeIf((Integer i)->i==0);
        toString = pq.toString();
        assertEquals(16, pq.size());
        assertEquals(1, pq.peek());
        pq.removeIf((Integer i)->i==17);
        toString = pq.toString();
        assertEquals(15, pq.size());
        pq.removeIf((Integer i)->2*(i/2)==i);
        toString = pq.toString();
        assertEquals(7, pq.size());
        pq.removeIf((Integer i)->2*(i/2)!=i);
        toString = pq.toString();
        assertEquals(0, pq.size());
    }
}
