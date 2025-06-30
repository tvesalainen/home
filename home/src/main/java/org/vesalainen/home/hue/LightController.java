/*
 * Copyright (C) 2024 Timo Vesalainen <timo.vesalainen@iki.fi>
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vesalainen.util.LoggingCommandLine;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class LightController extends LoggingCommandLine
{

    public LightController()
    {
        addArgument(Path.class, "configuration file");
        //addOption("-f", "force port resolv", null, Boolean.FALSE);
        //addOption("-rt", "resolv timeout", null, 2000L);
    }
    
    public static void main(String... args)
    {
        LightController lc = new LightController();
        lc.command(args);
        JavaLogging log = JavaLogging.getLogger(LightController.class);
        Path configfile = lc.getArgument("configuration file");
        while (true)
        {
            try
            {
                EventManager em = new EventManager(configfile);
                em.start();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                log.log(Level.SEVERE, ex, "command-line %s", ex.getMessage());
            }
        }
    }
}
