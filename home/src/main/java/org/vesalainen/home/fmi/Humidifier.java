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

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Humidifier
{
    private static final long SECONDS_IN_HOUR = Duration.ofHours(1).getSeconds();
    private static final long SECONDS_IN_DAY = Duration.ofDays(1).getSeconds();
    private static final double PRESSURE = 1014;
    private final double maxRH;
    private final double minRH;
    private final double inTemp;
    private final double vaporizingPower;
    private final double circulation;
    private final double volume;
    private final double airPressure;
    private final double outTemp;
    private final double dewPoint;
    private final double aveRH;
    public Humidifier(
            double maxRH,
            double minRH,
            double inTemp,
            double humVaporMass,    // l/d
            double vaporazingPower, // ml/h
            double volume,
            double airPressure,
            double outTemp, 
            double dewPoint
    )
    {
        this.maxRH = maxRH;
        this.minRH = minRH;
        this.aveRH = (maxRH+minRH)/2;
        this.inTemp = inTemp;
        this.volume = volume;
        this.airPressure = airPressure;
        this.outTemp = outTemp;
        this.dewPoint = dewPoint;
        double maxDewPoint = Humidity.dewPoint(maxRH, inTemp);
        double minDewPoint = Humidity.dewPoint(minRH, inTemp);
        double maxVaporWeight = Humidity.actualMixingRatio(maxDewPoint, PRESSURE);
        double minVaporWeight = Humidity.actualMixingRatio(minDewPoint, PRESSURE);
        double curVaporWeight = (maxVaporWeight+minVaporWeight)/2;
        this.circulation = 1000*humVaporMass/(SECONDS_IN_DAY*curVaporWeight);   // g/s
        this.vaporizingPower = vaporazingPower/SECONDS_IN_HOUR;         // g/s
    }

    public double relativeHumidityDeltaVaporizing(int seconds, double inRH)
    {
        double vaporized = vaporizingPower*seconds;
        double totVaporWeight = totVaporWeight(inRH, airPressure);
        double newRH = relativeHumidity(totVaporWeight+vaporized, airPressure);
        return newRH - inRH;
    }
    public double relativeHumidityDeltaCirculation(int seconds, double inRH)
    {
        double inDewPoint = Humidity.dewPoint(inRH, inTemp);
        double inWeight = Humidity.actualMixingRatio(inDewPoint, airPressure);
        double outWeight = Humidity.actualMixingRatio(dewPoint, airPressure);
        double inComing = circulation*outWeight*seconds;
        double outGoing = circulation*inWeight*seconds;
        double totVaporWeight = totVaporWeight(inRH, airPressure);
        double delta = inComing - outGoing;
        double newRH = relativeHumidity(totVaporWeight+delta, airPressure);
        return newRH - inRH;
    }
    public double totVaporWeight(double rh, double airPressure)
    {
        double airWeight = airWeight(airPressure);
        double saturatedVaporPressure = Humidity.saturatedVaporPressure(inTemp, airPressure);
        return rh*(saturatedVaporPressure*airWeight)/100;
    }
    public double relativeHumidity(double totVaporWeight, double airPressure)
    {
        double airWeight = airWeight(airPressure);
        double saturatedVaporPressure = Humidity.saturatedVaporPressure(inTemp, airPressure);
        return 100*(totVaporWeight)/(saturatedVaporPressure*airWeight);
    }
    public double airWeight(double airPressure)
    {
        return volume*Humidity.airWeight(inTemp, airPressure);
    }

    public double getAveRH()
    {
        return aveRH;
    }
    
    public double getMaxRH()
    {
        return maxRH;
    }

    public double getMinRH()
    {
        return minRH;
    }

    public double getInTemp()
    {
        return inTemp;
    }

    public double getVaporizingPower()
    {
        return vaporizingPower;
    }

    public double getCirculation()
    {
        return circulation;
    }

    public double getVolume()
    {
        return volume;
    }

}
