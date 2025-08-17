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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class AdjusterTest
{
    
    public AdjusterTest()
    {
    }

    @Test
    public void test2()
    {
        int[][] m = new int[][]{
            {16910, 3411},
            {10830, 14873},
            {10830, 10533},
            {10830, 11193},
            {5510, 9831},
            {5510, 0},
            {5510, 4033},
            {5130, 4313},
            {4560, 3411},
            {4560, 7835},
            {4560, 2686},
            {4560, 7713},
            {4560, 3064},
            {4560, 6586},
            {4560, 3064},
            {4560, 6744},
            {4560, 3411},
            {4560, 5889},
            {4560, 3733},
            {13300, 15774},
            {13490, 14849},
            {13680, 14499},
            {16910, 12879}};
        Adjuster a = new Adjuster(0, 2);
        for (int[] r : m)
        {
            a.adjust(r[1], r[0]);
            System.err.println(r[1]+", "+r[0]+"="+a.getAdj());
        }
    }    
}
