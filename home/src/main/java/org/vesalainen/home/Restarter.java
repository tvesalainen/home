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
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Restarter extends JavaLogging
{
    private final ScheduledExecutorService pool;
    private final long delay;
    private final TimeUnit unit ;
    private final int maxAttempts;
    private final Duration advance;

    public Restarter(ScheduledExecutorService pool, long delay, TimeUnit unit, int maxAttempts, Duration advance)
    {
        super(Restarter.class);
        this.pool = pool;
        this.delay = delay;
        this.unit = unit;
        this.maxAttempts = maxAttempts;
        this.advance = advance;
    }

    public void execute(Supplier<TemporalAccessor> act)
    {
        pool.submit(()->run(act, 1));
    }
    public void executeAndWait(Supplier<TemporalAccessor> act)
    {
        try
        {
            Future<?> future = pool.submit(()->run(act, 1));
            future.get();
        }
        catch (InterruptedException | ExecutionException ex)
        {
            throw new RuntimeException(ex);
        }
    }
    private void run(Supplier<TemporalAccessor> act, int cnt)
    {
        try
        {
            TemporalAccessor time = act.get();
            long d = Instant.from(time).getEpochSecond() - System.currentTimeMillis()/1000 - advance.getSeconds();
            d = Math.max(d, unit.toSeconds(delay));
            fine("Restarting at %s - %s delay=%d", time, advance, d);
            pool.schedule(()->run(act, 1), d, TimeUnit.SECONDS);
        }
        catch (Throwable ex)
        {
            if (cnt <= maxAttempts)
            {
                log(SEVERE, ex, "Retrying after %d %s", cnt*delay, unit);
                pool.schedule(()->run(act, cnt+1), cnt*delay, unit);
            }
            else
            {
                log(SEVERE, ex, "Giving up after %d attempts", maxAttempts);
            }
        }
    }
}
