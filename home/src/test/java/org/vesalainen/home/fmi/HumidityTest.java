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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class HumidityTest
{
    
    public HumidityTest()
    {
    }

    @Test
    public void testγ()
    {
        assertEquals(0.9507, Humidity.γ(50, 25), 1e-4);
    }
    @Test
    public void testDewPoint()
    {
        double rh = 50;
        double ot = 25;
        assertEquals(13.85, Humidity.dewPoint(rh, ot), 1e-2);
    }
    @Test
    public void testRelativeHumidity()
    {
        double rh = 50;
        double ot = 25;
        double dewPoint = Humidity.dewPoint(rh, ot);
        assertEquals(50, Humidity.relativeHumidity(dewPoint, ot), 1e-2);
    }
    @Test
    public void testVaporDensity()
    {
        double rh = 50;
        double ot = 25;
        double airPressure = 1016;
        double dewPoint = Humidity.dewPoint(rh, ot);
        double actualMixingRatio = Humidity.actualMixingRatio(dewPoint, airPressure);
        assertEquals(9.86, actualMixingRatio, 1e-2);
        double saturatedVaporPressure = Humidity.saturatedVaporPressure(ot, airPressure);
        assertEquals(0.5, actualMixingRatio/saturatedVaporPressure, 1e-1);
    }
    @Test
    public void testInsideRelativeHumidity()
    {
        double rh = 50;
        double ot = 25;
        double airPressure = 1016;
        assertEquals(60.3, Humidity.insideRelativeHumidity(ot, 22, rh, airPressure), 1.5);
    }    
    @Test
    public void testAirWeight()
    {
        double temperature = 25;
        double airPressure = 1013.25;
        assertEquals(1.184, Humidity.airWeight(temperature, airPressure), 1e-3);
    }
}
