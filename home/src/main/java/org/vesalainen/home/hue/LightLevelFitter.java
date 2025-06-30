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

import org.vesalainen.math.AbstractFitter;
import org.vesalainen.math.matrix.DoubleMatrix;
import org.vesalainen.math.matrix.ReadableDoubleMatrix;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class LightLevelFitter extends AbstractFitter
{

    public LightLevelFitter(double... initialParams)
    {
        super(2, initialParams);
    }

    public double getDimm(double off, double on)
    {
        double[] p = this.getParams();
        double a = p[0];
        double b = p[1];
        return (on-a*off)/b;
    }
    @Override
    public void compute(DoubleMatrix param, ReadableDoubleMatrix x, DoubleMatrix y)
    {
        double a = param.get(0, 0);
        double b = param.get(1, 0);
        int rows = x.rows();
        for (int ii=0;ii<rows;ii++)
        {
            double off = x.get(ii, 0);
            double on = x.get(ii, 1);
            double bri = (on-a*off)/b;
            y.set(ii, 0, bri);
        }
    }
    
}
