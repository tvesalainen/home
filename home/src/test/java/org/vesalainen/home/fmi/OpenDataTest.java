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
package org.vesalainen.home.fmi;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.vesalainen.home.IndexedData;
import org.vesalainen.util.logging.AttachedLogger;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class OpenDataTest implements AttachedLogger
{
    
    public OpenDataTest()
    {
        JavaLogging.setConsoleHandler("", Level.ALL);
    }

    @Test
    public void test1() throws InterruptedException
    {
        /*
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
        IndexedData hrs = new IndexedData(Duration.ofMinutes(15), Duration.ofDays(1));
        OpenData od = new OpenData(pool, "helsinki", "Pressure,Temperature,Dewpoint,Humidity", hrs, Duration.ofMinutes(15), 15, TimeUnit.MINUTES, 15, Duration.ofHours(25));
        od.startReading();
        int start = hrs.getIndex();
        for (int ii=0;ii<100;ii++)
        {
            double v = hrs.getAndWait(start+ii, "Temperature");
            info("temp=%f", v);
        }
        */
    }
}
