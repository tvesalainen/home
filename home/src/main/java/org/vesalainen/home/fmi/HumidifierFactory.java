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

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class HumidifierFactory
{
    public final double maxRH;
    public final double minRH;
    public final double inTemp;
    public final double humVaporMass;    // l/d
    public final double vaporazingPower; // ml/h
    public final double volume;

    public HumidifierFactory(double maxRH, double minRH, double inTemp, double humVaporMass, double vaporazingPower, double volume)
    {
        this.maxRH = maxRH;
        this.minRH = minRH;
        this.inTemp = inTemp;
        this.humVaporMass = humVaporMass;
        this.vaporazingPower = vaporazingPower;
        this.volume = volume;
    }
    public Humidifier create(Double... p)
    {
        if (p.length != 3)
        {
            throw new IllegalArgumentException("illegal count of parameters");
        }
        return create(p[0], p[1], p[2]);
    }
    public Humidifier create(
            double airPressure,
            double outTemp, 
            double dewPoint
    )
    {
        return new Humidifier(maxRH, minRH, inTemp, humVaporMass, vaporazingPower, volume, airPressure, outTemp, dewPoint);
    }
}
