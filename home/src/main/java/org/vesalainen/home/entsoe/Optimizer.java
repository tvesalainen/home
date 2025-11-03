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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static java.util.logging.Level.SEVERE;
import org.vesalainen.home.IndexedData;
import org.vesalainen.home.BoundedPriorityQueue;
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
        quarts.addSupplier("humidifier", factory::create, "Pressure", "Temperature", "DewPoint");
        OpenData openData = new OpenData(pool, place, quarts);
        openData.startReadingAndWait();

        Entsoe entsoe = new Entsoe(pool, securityToken, domain, quarts);
        entsoe.startReadingAndWait();
        this.seconds = quarts.getSeconds();
        this.qSize = quarts.getCapacity();
        queue.offer(new Candidate());
    }
    public void start()
    {
        if (future == null)
        {
            future = pool.submit(this::optimize);
        }
        else
        {
            throw new IllegalStateException();
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
        return best;
    }
    public void commit(Candidate best)
    {
        info("remove differing candidates");
        int index = quarts.getIndex();
        boolean isOn = best.get(index);
        pool.submit(()->queue.removeIf((c)->c.get(index)!=isOn));
    }
    public void optimize()
    {
        try
        {
            while (true)
            {
                Candidate c = queue.poll();
                Candidate clone = c.clone();
                if (c.burn(true))
                {
                    queue.offer(c);
                }
                if (clone.burn(false))
                {
                    queue.offer(clone);
                }
            }
        }
        catch (Throwable ex)
        {
            log(SEVERE, ex, "optimize() error");
        }
    }
    public IndexedData getQuarts()
    {
        return quarts;
    }

    public double getPrice()
    {
        return quarts.get("price");
    }
    
    public class Candidate implements Comparable<Candidate>, Cloneable
    {
        private long ons = -1;
        private int qIndex;
        private double relativeHumidity;
        private double cost;
        private int reach0;
        public Candidate()
        {
            this.qIndex = quarts.getIndex();
            this.relativeHumidity = minRH;
        }
        public boolean burn(boolean on)
        {
            qIndex++;
            Humidifier humidifier = quarts.getAndWait(qIndex, "humidifier");
            double deltaCirc = humidifier.relativeHumidityDeltaCirculation(seconds, relativeHumidity);
            double deltaVapor = humidifier.relativeHumidityDeltaVaporizing(seconds, relativeHumidity);
            set(qIndex, on);
            if (on)
            {
                cost += (double)quarts.getAndWait(qIndex, "price");
                relativeHumidity += deltaCirc + deltaVapor;
                if (relativeHumidity > maxRH)
                {
                    return false;
                }
            }
            else
            {
                relativeHumidity += deltaCirc;
                if (relativeHumidity < minRH)
                {
                    return false;
                }
            }
            reach0 = qIndex + (int)((relativeHumidity - minRH)/abs(deltaCirc)) + (int)max(0.0, ((maxRH - relativeHumidity)/(deltaVapor+deltaCirc))) + 2;
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
            return (int) Math.signum(cost - o.cost);
        }

        public boolean failsTo(Candidate o)
        {
            return o.qIndex > reach0 && o.cost < cost;
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
            return "Candidate{ " +sb+ " level=" + idx + ", cost=" + cost + ", rh=" + (int)relativeHumidity + '}';
        }

        private void set(int index, boolean on)
        {
            if (qIndex-quarts.getIndex() > 64)
            {
                throw new IllegalStateException("level > 64");
            }
            if (on)
            {
                ons |= 1<<(index%64);
            }
            else
            {
                ons &= ~(1<<(index%64));
            }
        }

        private boolean get(int index)
        {
            if (qIndex-quarts.getIndex() > 64)
            {
                throw new IllegalStateException("level > 64");
            }
            return (ons & 1<<(index%64)) != 0;
        }
    }
}
