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

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Adjuster
{
    private final double min;
    private final double max;
    private double prev = Double.NaN;
    private double adj = 1.0;

    public Adjuster(double min, double max)
    {
        this.min = min;
        this.max = max;
    }
    
    public void adjust(double value, double target)
    {
        double delta = value-target;
        double abs = abs(delta);
        double sig = signum(delta);
        double a = target/value;
        adj = min(max, max(min, adj * a));
        prev = target;
    }

    public double getAdj()
    {
        return adj;
    }

    public void reset()
    {
        prev = Double.NaN;
        adj = 1.0;
    }
    
}
