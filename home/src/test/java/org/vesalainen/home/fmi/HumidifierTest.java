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
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class HumidifierTest
{
    
    public HumidifierTest()
    {
    }

    @Test
    public void test1()
    {
        double t = 22;
        double rh = 50;
        double dewPoint = Humidity.dewPoint(rh, t);
        Humidifier h = new Humidifier(60, 40, 22, 10, 400, 76, 1014, t, dewPoint);
        assertEquals(0.0, h.relativeHumidityDeltaCirculation(900, 50), 1e-4);
    }
    @Test
    public void test2()
    {
        double t = -22;
        double rh = 30;
        double dewPoint = Humidity.dewPoint(rh, t);
        Humidifier h = new Humidifier(60, 40, 22, 10, 400, 76, 1014, t, dewPoint);
        assertEquals(-6.7, h.relativeHumidityDeltaCirculation(900, 50), 1e-1);
    }
    @Test
    public void test3()
    {
        double t = -22;
        double rh = 30;
        double dewPoint = Humidity.dewPoint(rh, t);
        Humidifier h = new Humidifier(60, 40, 22, 10, 400, 76, 1014, t, dewPoint);
        assertEquals(6.6, h.relativeHumidityDeltaVaporizing(900, 50), 1e-1);
    }
    @Test
    public void testReal()
    {
        double t = 6.6;
        double rh = 93;
        double dp = Humidity.dewPoint(rh, t);
        assertEquals(5.5, dp, 1e-1);
        Humidifier h = new Humidifier(60, 40, 22, 16, 400, 85.4, 1000, t, dp);
        double irh = 42;
        for (int ii=0;ii<7*900;ii++)
        {
            double dRHV = h.relativeHumidityDeltaVaporizing(1, irh);
            double dRHC = h.relativeHumidityDeltaCirculation(1, irh);
            irh += dRHV+dRHC;
        }
        assertEquals(59, irh, 1);
    }
}
