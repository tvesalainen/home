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

import static java.lang.Math.min;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vesalainen.util.ArrayIterator;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class BoundedPriorityQueue<T> extends AbstractCollection<T>
{
    private final T[] arr;
    private final int capacity;
    private final Comparator<? super T> comparator;
    private int head;
    private int tail;
    private ReentrantLock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();

    public BoundedPriorityQueue(int size)
    {
        this(size, null);
    }
    public BoundedPriorityQueue(int size, Comparator<? super T> comparator)
    {
        this.arr = (T[]) new Object[size];
        this.capacity = size;
        this.comparator = comparator;
    }
    
    public void offerAndWait(T item)
    {
        lock.lock();
        try
        {
            while (tail-head == capacity)
            {
                condition.await();
            }
            offer(item);
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }        
        finally
        {
            lock.unlock();
        }
    }
    public void offer(T item)
    {
        lock.lock();
        try
        {
            int idx = Arrays.binarySearch(arr, head, tail, item, comparator);
            if (idx >= 0)
            {
                insert(idx, item);
            }
            else
            {
                insert(-idx-1, item);
            }
        }
        finally
        {
            lock.unlock();
        }
    }
    @Override
    public int size()
    {
        return tail-head;
    }
    public int free()
    {
        return capacity-(tail-head);
    }
    public T poll()
    {
        lock.lock();
        try
        {
            if (size() > 0)
            {
                T poll = arr[head];
                arr[head++] = null;
                return poll;
            }
            else
            {
                return null;
            }
        }
        finally
        {
            condition.signalAll();
            lock.unlock();
        }
    }
    public T peek()
    {
        lock.lock();
        try
        {
            if (size() > 0)
            {
                return arr[head];
            }
            else
            {
                return null;
            }
        }
        finally
        {
            lock.unlock();
        }
    }
    private void insert(int idx, T item)
    {
        if (head > 0)
        {
            System.arraycopy(arr, head, arr, head-1, idx-head);
            arr[idx-1] = item;
            head--;
        }
        else
        {
            if (idx < capacity)
            {
                System.arraycopy(arr, idx, arr, idx+1, min(tail-idx, capacity-idx-1));
                arr[idx] = item;
                tail = min(capacity,tail+1);
            }
        }
    }
    
    @Override
    public String toString()
    {
        if (size() < 20)
        {
            return Arrays.toString(Arrays.copyOfRange(arr, head, tail));
        }
        else
        {
            return "["+arr[head]+" size="+size()+" "+arr[tail-1]+"]";
        }
    }

    @Override
    public Iterator<T> iterator()
    {
        lock.lock();
        try
        {
            return new ArrayIterator<>(Arrays.copyOfRange(arr, head, tail));
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter)
    {
        lock.lock();
        try
        {
            boolean done = false;
            int freeStart=-1;
            int deleted = 0;
            int ok = 0;
            for (int ii=head;ii<tail;ii++)
            {
                if (filter.test(arr[ii]))
                {
                    if (freeStart == -1)
                    {
                        freeStart = ii;
                        deleted++;
                    }
                    else
                    {
                        System.arraycopy(arr, ii-ok, arr, freeStart, ok);
                        freeStart += ok;
                        done = true;
                        deleted++;
                    }
                    ok = 0;
                }
                else
                {
                    ok++;
                }
            }
            if (freeStart != -1)
            {
                System.arraycopy(arr, tail-ok, arr, freeStart, ok);
                done = true;
            }
            Arrays.fill(arr, tail-deleted, tail, null);
            tail-=deleted;
            return done;
        }
        finally
        {
            condition.signalAll();
            lock.unlock();
        }
    }
    
}
