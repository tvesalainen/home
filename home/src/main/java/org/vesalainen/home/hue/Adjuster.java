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

import static java.lang.Math.*;
import static java.util.concurrent.TimeUnit.*;
import org.vesalainen.math.sliding.DoubleTimeoutSlidingAverage;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Adjuster extends JavaLogging
{
    private static final long DURATION = MINUTES.toMillis(18);
    private static final long DURATION2 = DURATION/2;
    private final double min;
    private final double max;
    private DoubleTimeoutSlidingAverage ave;
    private double adj = 1.0;
    private long lastTime;

    public Adjuster(double min, double max)
    {
        super(Adjuster.class);
        this.min = min;
        this.max = max;
        this.ave = new DoubleTimeoutSlidingAverage(8, DURATION);
    }
    
    public void adjust(double value, double target)
    {
        if (value > 0)
        {
            long now = System.currentTimeMillis();
            long gap = now - lastTime;
            lastTime = now;
            if (ave.size() == 0)
            {
                if (gap < DURATION)
                {
                    severe("gap %d < DURATION %d", gap, DURATION);
                }
                ave.accept(pow(adj, DURATION/gap), now - DURATION2);
                fine("fill ave with %f", ave.last());
            }
            adj = min(max, max(min, adj*target/value));
            ave.accept(adj);
        }
    }

    public double getAdj()
    {
        if (ave.size() > 0)
        {
            return ave.fast();
        }
        else
        {
            return adj;
        }
    }

    public void reset()
    {
        adj = 1.0;
        ave.clear();
    }
    
}
