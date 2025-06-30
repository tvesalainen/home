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
import org.vesalainen.math.matrix.DoubleMatrix;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class LightLevelFitterTest
{
    
    public LightLevelFitterTest()
    {
    }

    @Test
    public void test0()
    {
        DoubleMatrix m = new DoubleMatrix(0, 3);
        //m.addRow(77.87, 12049, 14447);
        //m.addRow(77.87, 12049, 14313);
        //m.addRow(77.08, 11667, 14313);
        //m.addRow(75.89, 11667, 14313);
        //m.addRow(75.1, 11667, 14147);
        //m.addRow(75.1, 11667, 14286);
        //m.addRow(73.91, 11667, 14286);
        //m.addRow(73.91, 11717, 14286);
        m.addRow(73.91, 11717, 13975);
        m.addRow(73.12, 11717, 13975);
        m.addRow(73.12, 11717, 14203);
        m.addRow(71.15, 8293, 12095);
        m.addRow(34, 53, 3411);
        m.addRow(100, 11717, 15304);
        m.addRow(49, 11717, 13100);
        m.addRow(15.42, 11717, 12228);
        m.addRow(79, 11717, 14576);
        LightLevelFitter llf = new LightLevelFitter(1, 1);
        int rows = m.rows();
        for (int ii=0;ii<rows;ii++)
        {
            llf.addPoints(
                    m.get(ii, 1),
                    m.get(ii, 2),
                    m.get(ii, 0)
                    );
        }
        double fit = llf.fit();
        double[] params = llf.getParams();
        double dimm = llf.getDimm(11667, 14286);
        assertEquals(75, llf.getDimm(11667, 14286), 5);
        assertEquals(49, llf.getDimm(11717, 13100), 5);
        assertEquals(34, llf.getDimm(53, 3411), 20);
    }
    
}
