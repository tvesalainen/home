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
package org.vesalainen.home.entsoe;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;
import org.vesalainen.home.IndexedData;
import org.vesalainen.home.BoundedPriorityQueue;
import org.vesalainen.home.OutOfDataException;
import org.vesalainen.home.Restarter;
import org.vesalainen.home.fmi.Humidifier;
import org.vesalainen.home.fmi.HumidifierFactory;
import org.vesalainen.home.fmi.OpenData;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Optimizer extends JavaLogging
{
    private final IndexedData quarts;
    private int quartCount;
    private BoundedPriorityQueue<Candidate> queue = new BoundedPriorityQueue<>(100000);
    private ScheduledExecutorService pool;
    private Future<?> future;
    private final double maxRH;
    private final double minRH;
    private final int seconds;
    private final int qSize;

    public Optimizer(
            String securityToken, 
            String domain, 
            String place,
            double maxRH,
            double minRH,
            double inTemp,
            double vaporMass,
            double vaporizingPower,
            double volume
    )
    {
        super(Optimizer.class);
        this.pool = Executors.newScheduledThreadPool(2);
        Duration ofDays = Duration.ofDays(2);
        this.maxRH = maxRH;
        this.minRH = minRH;
        this.quarts = new IndexedData(Duration.ofMinutes(15), ofDays);
        HumidifierFactory factory = new HumidifierFactory(maxRH, minRH, inTemp, vaporMass, vaporizingPower, volume);
        quarts.addSupplier("humidifier", factory::create, "Pressure", "Temperature", "DewPoint", "Humidity");
        OpenData openData = new OpenData(pool, place, quarts);
        openData.startReadingAndWait();

        Entsoe entsoe = new Entsoe(pool, securityToken, domain, quarts);
        entsoe.startReadingAndWait();
        this.seconds = quarts.getSeconds();
        this.qSize = quarts.getCapacity();
    }
    public void reStart()
    {
        if (future == null || future.isDone())
        {
            future = pool.submit(this::optimize);
        }
    }
    public void stop()
    {
        if (future != null)
        {
            future.cancel(true);
        }
        else
        {
            throw new IllegalStateException();
        }
    }
    public Candidate best()
    {
        Candidate best = queue.peek();
        info("queue %s", queue);
        if (best != null)
        {
            return best;
        }
        else
        {
            try
            {
                return new Candidate();
            }
            catch (OutOfDataException ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }
    public void commit(boolean isOn)
    {
        info("remove differing candidates");
        int index = quarts.getIndex();
        Future<Boolean> f = pool.submit(()->queue.removeIf((c)->c.get(index)!=isOn));
        pool.submit(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    f.get();
                }
                catch (InterruptedException | ExecutionException ex)
                {
                    throw new RuntimeException(ex);
                }
                reStart();  
            }
        });
    }
    public void optimize()
    {
        Candidate[] cands = new Candidate[2];
        try
        {
            while (queue.free() >= 2)
            {
                Candidate polled = queue.poll();
                if (polled == null)
                {
                    polled = new Candidate();
                }
                cands[0] = polled.clone();
                cands[1] = polled.clone();
                for (int ii=0;ii<2;ii++)
                {
                    try
                    {
                        if (!cands[ii].burn(ii%2==0))
                        {
                            cands[ii] = null;
                        }
                    }
                    catch (OutOfDataException ex)
                    {
                        queue.offer(polled);
                        return;
                    }
                }
                for (Candidate c  : cands)
                {
                    if (c != null)
                    {
                        queue.offer(c);
                    }
                }
            }
        }
        catch (OutOfDataException ex)
        {
            fine("out of data");
        }
        catch (Throwable ex)
        {
            log(SEVERE, ex, "optimize() error");
        }
        fine("optimizer stopping");
    }
    public IndexedData getQuarts()
    {
        return quarts;
    }

    public double getPrice()
    {
        try
        {
            return quarts.get("price");
        }
        catch (OutOfDataException ex)
        {
            throw new RuntimeException(ex);
        }
    }
    
    public class Candidate implements Comparable<Candidate>, Cloneable
    {
        private long ons = -1;
        private int qIndex;
        private float rh;
        private double cost;
        public Candidate() throws OutOfDataException
        {
            this.qIndex = quarts.getIndex();
            Humidifier humidifier = quarts.get(qIndex, "humidifier");
            this.rh = (float) humidifier.inRHUsingOutAir();
        }
        public boolean burn(boolean on) throws OutOfDataException
        {
            qIndex++;
            Humidifier humidifier = quarts.get(qIndex, "humidifier");
            double deltaCirc = humidifier.relativeHumidityDeltaCirculation(seconds, rh);
            double deltaVapor = humidifier.relativeHumidityDeltaVaporizing(seconds, rh);
            set(qIndex, on);
            if (on)
            {
                cost += (double)quarts.get(qIndex, "price");
                rh += deltaCirc + deltaVapor;
                if (rh > maxRH)
                {
                    return false;
                }
            }
            else
            {
                rh += deltaCirc;
                if (rh < minRH)
                {
                    return false;
                }
            }
            return true;
        }
        @Override
        public Candidate clone()
        {
            try
            {
                Candidate clone = (Candidate) super.clone();
                return clone;
            }
            catch (CloneNotSupportedException ex)
            {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public int compareTo(Candidate o)
        {
            if (qIndex == o.qIndex)
            {
                return (int) Math.signum(cost - o.cost);
            }
            else
            {
                return qIndex - o.qIndex;
            }
        }

        public boolean isOn()
        {
            return get(quarts.getIndex());
        }
        public boolean isOn(int index)
        {
            return get(index);
        }

        public int getqIndex()
        {
            return qIndex;
        }
        
        @Override
        public String toString()
        {
            int start = quarts.getIndex();
            StringBuilder sb = new StringBuilder();
            for (int ii=start;ii<=qIndex;ii++)
            {
                if (isOn(ii))
                {
                    sb.append('+');
                }
                else
                {
                    sb.append('-');
                }
            }
            int idx = qIndex-start;
            return "Candidate{ " +sb+ " level=" + idx + ", cost=" + cost + ", rh=" + (int)rh + '}';
        }

        private void set(int index, boolean on)
        {
            if (qIndex-quarts.getIndex() > 64)
            {
                throw new IllegalStateException("level > 64");
            }
            if (on)
            {
                ons |= 1L<<(index%64);
            }
            else
            {
                ons &= ~(1L<<(index%64));
            }
        }

        private boolean get(int index)
        {
            if (qIndex-quarts.getIndex() > 64)
            {
                throw new IllegalStateException("level > 64");
            }
            return (ons & 1L<<(index%64)) != 0;
        }
    }
}
