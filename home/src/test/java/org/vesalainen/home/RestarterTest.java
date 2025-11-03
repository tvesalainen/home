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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class RestarterTest
{
    
    public RestarterTest()
    {
    }
    @Test
    public void test1() throws InterruptedException
    {
        /*
        ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
        Restarter r = new Restarter(pool, 10, TimeUnit.SECONDS, 2, Duration.ofHours(0));
        r.execute(()->{return Instant.now().plusSeconds(10);});
        pool.awaitTermination(200, TimeUnit.SECONDS);
        */
    }
}
