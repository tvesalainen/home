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

import static java.lang.Math.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Humidity
{
    private static final double b = 17.625;
    private static final double c = 243.04;
    public static final double inRH(double outTemp, double inTemp, double outRH, double airPressure)
    {
        double outDP = dewPoint(outRH, outTemp);
        double outMR = actualMixingRatio(outDP, airPressure);
        double inSVP = saturatedVaporPressure(inTemp, airPressure);
        return 100*outMR/inSVP;
    }
    public static double γ(double rh, double t)
    {
        return log(rh/100) + b*t/(c+t);
    } 
    public static double dewPoint(double rh, double t)
    {
        double γ = γ(rh, t);
        return c*γ/(b-γ);
    }
    public static double rh(double dewPoint, double outTemp)
    {
        return actualVaporPressure(dewPoint)/saturatedVaporPressure(outTemp)*100;
    }
    /**
     * Calculated grams of H2O per kg of air
     * @param dewPoint
     * @param airPressure
     * @return 
     */
    public static double actualMixingRatio(double dewPoint, double airPressure)
    {
        double e = actualVaporPressure(dewPoint);
        return 621.97*e/(airPressure-e);
    }
    public static double saturatedVaporPressure(double temperature, double airPressure)
    {
        double e = saturatedVaporPressure(temperature);
        return 621.97*e/(airPressure-e);
    }
    public static double actualVaporPressure(double dewPoint)
    {
        return 6.11 * pow(10, 7.5 * dewPoint / (237.3 + dewPoint));
    }
    public static double saturatedVaporPressure(double temp)
    {
        return 6.11 * pow(10, 7.5 * temp / (237.3 + temp));
    }
    /**
     * Returns how much cubic meter of air weights in Kg
     * @param temperature
     * @param airPressure
     * @return 
     */
    public static double airWeight(double temperature, double airPressure)
    {
        return 100*airPressure/(287.058*(273.15+temperature));
    }
}
