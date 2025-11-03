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

import static java.lang.Math.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vesalainen.util.concurrent.PredicateSynchronizer;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class IndexedData
{
    private final int period;
    private final long periodInMillis;
    private final int capacity;
    private final Map<String,Data> map = new HashMap<>();
    
    public IndexedData(Duration period, Duration window)
    {
        this((int)period.getSeconds(), (int)(window.getSeconds()/period.getSeconds())+1);
    }

    public IndexedData(int period, int capacity)
    {
        this.period = period;
        this.periodInMillis = period*1000;
        this.capacity = capacity;
    }
    public <T> void addSupplier(String target, Function<Double[],T> supplier, String... parameters)
    {
        Data data = getData(target);
        data.initializers.add(new ObjectInitializer(target, supplier, parameters));
    }

    public int getCapacity()
    {
        return capacity;
    }
    public int getSize(String parameter)
    {
        Data data = getData(parameter);
        return data.getSize();
    }
    public int getMaxIndex(String parameter)
    {
        Data data = getData(parameter);
        return data.getMaxIndex();
    }
    public int getMinIndex(String parameter)
    {
        Data data = getData(parameter);
        return data.getMinIndex();
    }
    public <T> T getAndWait(String parameter)
    {
        return getAndWait(getIndex(), parameter);
    }
    public <T> T getAndWait(TemporalAccessor accessor, String parameter)
    {
        return getAndWait(getIndex(accessor), parameter);
    }
    public <T> T getAndWait(int periodIndex, String parameter)
    {
        Data data = getData(parameter);
        return data.getAndWait(periodIndex);
    }
    public <T> T get(String parameter)
    {
        return get(getIndex(), parameter);
    }
    public <T> T get(TemporalAccessor accessor, String parameter)
    {
        return get(getIndex(accessor), parameter);
    }
    public <T> T get(int periodIndex, String parameter)
    {
        Data data = getData(parameter);
        return data.get(periodIndex);
    }
    public <T> void set(TemporalAccessor accessor, String parameter, T value)
    {
        set(getIndex(accessor), parameter, value);
    }
    public <T> void set(String parameter, T value)
    {
        set(getIndex(), parameter, value);
    }
    public <T> void set(int periodIndex, String parameter, T value)
    {
        Data data = getData(parameter);
        data.set(periodIndex, value);
    }
    public final int getIndex(TemporalAccessor accessor)
    {
        Instant instant = Instant.from(accessor);
        return getIndex(instant.toEpochMilli());
    }
    public final int getIndex()
    {
        return getIndex(System.currentTimeMillis());
    }
    public final int getIndex(long millis)
    {
        return (int) (millis/periodInMillis);
    }
    public final ZonedDateTime currentPeriod()
    {
        return period(System.currentTimeMillis());
    }
    public final ZonedDateTime period(TemporalAccessor accessor)
    {
        Instant instant = Instant.from(accessor);
        return period(instant.toEpochMilli());
    }
    public final ZonedDateTime period(long now)
    {
        long millis = periodInMillis*(now/periodInMillis);
        Instant instant = Instant.ofEpochMilli(millis);
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    public long getMillis(int index)
    {
        return index*periodInMillis;
    }

    public int getSeconds()
    {
        return period;
    }

    private interface Initializer
    {
        void init(int index);
        void waitAndInit(int index);
    }
    private Data getData(String parameter)
    {
        parameter = parameter.toLowerCase();
        Data data = map.get(parameter);
        if (data == null)
        {
            data = new Data();
            map.put(parameter, data);
        }
        return data;
    }
    private class Data
    {
        private final int[] indexes = new int[capacity];
        private final Object[] arr = new Object[capacity];
        private int maxIndex;
        private int minIndex = Integer.MAX_VALUE;
        private PredicateSynchronizer sync = new PredicateSynchronizer();
        private List<Initializer> initializers = new ArrayList<>();
        public <T> T getAndWait(int periodIndex)
        {
            try
            {
                int index = periodIndex % capacity;
                if (indexes[index] != periodIndex)
                {
                    callSuppliers(periodIndex, true);
                }
                sync.waitUntil(()->periodIndex <= getMaxIndex());
                return get(periodIndex);
            }
            catch (InterruptedException ex)
            {
                throw new RuntimeException(ex);
            }
        }
        public <T> T get(int periodIndex)
        {
            int index = periodIndex % capacity;
            if (indexes[index] != periodIndex)
            {
                callSuppliers(periodIndex, false);
            }
            if (periodIndex < minIndex || periodIndex > maxIndex)
            {
                throw new IllegalArgumentException(periodIndex+" not in range ["+minIndex+", "+maxIndex+"]");
            }
            return (T) arr[index];
        }
        public <T> void set(int periodIndex, T value)
        {
            indexes[periodIndex % capacity] = periodIndex;
            if (getSize() > getCapacity())
            {
                throw new IndexOutOfBoundsException("too much data");
            }
            int index = periodIndex % capacity;
            arr[index] = value;
            maxIndex = max(maxIndex, periodIndex);
            minIndex = min(minIndex, periodIndex);
            sync.update();
        }
        private void callSuppliers(int periodIndex, boolean wait)
        {
            indexes[periodIndex % capacity] = periodIndex;
            for (Initializer initializer : initializers)
            {
                if (wait)
                {
                    initializer.waitAndInit(periodIndex);
                }
                else
                {
                    initializer.init(periodIndex);
                }
            }
        }
        public int getMaxIndex()
        {
            return maxIndex;
        }
        public int getMinIndex()
        {
            return max(minIndex, getIndex());
        }
        public int getSize()
        {
            return getMaxIndex()-getMinIndex();
        }
    }
    private class ObjectInitializer<T> implements Initializer
    {
        private final String target;
        private final Function<Double[],T> supplier;
        private final String[] parameters;

        public ObjectInitializer(String target, Function<Double[],T> supplier, String[] parameters)
        {
            this.target = target;
            this.supplier = supplier;
            this.parameters = parameters;
        }
        @Override
        public void init(int periodIndex)
        {
            Double[] p = new Double[parameters.length];
            int ii = 0;
            for (String parameter : parameters)
            {
                p[ii++] = get(periodIndex, parameter);
            }
            set(periodIndex, target, supplier.apply(p));
        }
        @Override
        public void waitAndInit(int periodIndex)
        {
            Double[] p = new Double[parameters.length];
            int ii = 0;
            for (String parameter : parameters)
            {
                p[ii++] = getAndWait(periodIndex, parameter);
            }
            set(periodIndex, target, supplier.apply(p));
        }
    }
}
