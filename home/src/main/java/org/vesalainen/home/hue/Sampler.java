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

import static java.lang.Math.abs;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Sampler extends JavaLogging
{

    private final ScheduledExecutorService pool;


    private enum State
    {
        CHANGE, BRIGHTNESS, LEVEL
    };
    private final Hue hue;
    private Map<String, Light> map = new HashMap<>();
    private Random random = new Random();

    Sampler(Hue hue, ScheduledExecutorService pool)
    {
        super(Hue.class);
        this.hue = hue;
        this.pool = pool;
        add("Living room", "Living room group");
        add("Bedroom", "Bedroom motion");
        add("Bathroom", "Bathroom motion");
        add("Hallway", "Hallway motion");
    }

    void event(Resources.Resource res, String type, JSONObject jo)
    {
        String name = res.getName();
        Light light = map.get(name);
        if (light != null)
        {
            info("event %s, %s", name, type);
            light.event(res, type, jo);
        }
    }

    private void add(String name, String sensor)
    {
        Light light = new Light(name, sensor);
        map.put(name, light);
        map.put(sensor, light);
        light.dim(100);
    }

    private class Light
    {

        private State state;
        private String name;
        private String sensor;
        private Collection<Resources.Resource> on;
        private Collection<Resources.Resource> updBrightness;
        private double brightness;
        private int level;
        private int dim;

        public Light(String name, String sensor)
        {
            this.name = name;
            this.sensor = sensor;
            on = hue.getResource(name, "/on/on:true");
            updBrightness = hue.getResource(name, "/dimming/brightness:80");
        }

        private void event(Resources.Resource res, String type, JSONObject jo)
        {
            switch (type)
            {
                case "grouped_light":
                    BigDecimal bd = (BigDecimal) JSON.get(jo, "/dimming/brightness");
                    if (bd != null)
                    {
                        brightness = bd.doubleValue();
                        state = State.BRIGHTNESS;
                        info("brightness %s, %.0f", name, brightness);
                        if (abs(brightness - dim) > 5)
                        {
                            pool.schedule(() -> dim(dim), 1, TimeUnit.SECONDS);
                        }
                    }
                    break;
                case "light":
                    break;
                case "grouped_light_level":
                    Integer ii = (Integer) JSON.get(jo, "/light/light_level_report/light_level");
                    updateLevel(ii);
                    break;
                case "light_level":
                    ii = (Integer) JSON.get(jo, "/light/light_level");
                    updateLevel(ii);
                    break;
            }
        }

        private void dim(int v)
        {
            info("dim %s, %d", name, v);
            this.dim = v;
            hue.update(on, "/on/on:true");
            hue.update(updBrightness, "/dimming/brightness:" + v);
            state = State.CHANGE;
        }

        private void updateLevel(Integer ii)
        {
            int l = ii;
            if (l != level)
            {
                level = l;
                System.err.println("add(\"" + name + "\", " + dim + ", " + level + ");");
                if (dim > 0)
                {
                    pool.schedule(() -> dim(0), 1, TimeUnit.MINUTES);
                }
                else
                {
                    int d = random.nextInt(100);
                    pool.schedule(() -> dim(d), 1, TimeUnit.MINUTES);
                }
            }
        }

    }

    private void populate()
    {
        add("Living room", 100, 20546);
        add("Bathroom", 100, 14727);
        add("Hallway", 100, 19407);
        add("Bedroom", 100, 17119);
        add("Living room", 0, 20539);
        add("Bathroom", 0, 0);
        add("Hallway", 0, 14825);
        add("Bedroom", 0, 16273);
        add("Living room", 12, 20923);
        add("Bathroom", 51, 8804);
        add("Hallway", 43, 16216);
        add("Living room", 0, 20875);
        add("Bathroom", 0, 0);
        add("Hallway", 0, 14944);
        add("Bedroom", 2, 16366);
        add("Living room", 8, 20803);
        add("Hallway", 98, 19093);
        add("Bedroom", 0, 15984);
        add("Living room", 0, 20520);
        add("Hallway", 0, 14525);
        add("Bedroom", 43, 15933);
        add("Living room", 53, 20161);
        add("Hallway", 51, 15870);
        add("Bedroom", 0, 15619);
        add("Living room", 0, 19539);
        add("Hallway", 0, 14367);
        add("Bedroom", 14, 15565);
        add("Living room", 74, 20507);
        add("Hallway", 22, 14967);
        add("Bedroom", 0, 15727);
        add("Living room", 0, 20097);
        add("Hallway", 0, 14313);
        add("Bedroom", 94, 16320);
        add("Living room", 64, 20401);
        add("Hallway", 20, 14800);
        add("Bedroom", 0, 15727);
        add("Living room", 0, 20111);
        add("Hallway", 0, 14340);
        add("Bedroom", 43, 15984);
        add("Living room", 38, 20300);
        add("Hallway", 43, 15794);
        add("Bedroom", 0, 16082);
        add("Living room", 0, 20182);
        add("Hallway", 0, 14340);
        add("Bedroom", 54, 16273);
        add("Living room", 84, 20467);
        add("Hallway", 32, 15304);
        add("Bedroom", 0, 16033);
        add("Living room", 0, 19762);
        add("Hallway", 0, 14004);
        add("Bedroom", 86, 16366);
        add("Living room", 66, 20118);
        add("Hallway", 77, 17599);
        add("Bedroom", 0, 15727);
        add("Living room", 0, 19440);
        add("Hallway", 0, 13733);
        add("Bedroom", 28, 15397);
        add("Living room", 34, 19660);
        add("Hallway", 91, 17859);
        add("Bedroom", 0, 13831);
        add("Living room", 0, 19973);
        add("Hallway", 0, 11617);
        add("Bedroom", 72, 14849);
        add("Living room", 6, 20224);
        add("Hallway", 80, 17750);
        add("Bedroom", 0, 15883);
        add("Living room", 0, 19899);
        add("Hallway", 0, 14062);
        add("Bedroom", 12, 16131);
        add("Living room", 51, 20341);
        add("Hallway", 18, 14576);
        add("Living room", 0, 18357);
        add("Hallway", 0, 14175);
        add("Hallway", 99, 18535);
        add("Bathroom", 14, 5696);
        add("Bathroom", 14, 9262);
        add("Bedroom", 0, 16033);
        add("Hallway", 0, 12606);
        add("Bathroom", 0, 0);
        add("Bedroom", 73, 11502);
        add("Living room", 34, 17431);
        add("Living room", 34, 13945);
        add("Hallway", 97, 13171);
        add("Bathroom", 99, 14627);
        add("Hallway", 97, 17483);
        add("Bedroom", 0, 8349);
        add("Living room", 0, 13543);
        add("Bathroom", 0, 0);
        add("Hallway", 0, 7457);
        add("Bedroom", 76, 11212);
        add("Living room", 12, 14394);
        add("Bathroom", 41, 6896);
        add("Hallway", 58, 13575);
        add("Bedroom", 0, 8349);
        add("Living room", 0, 14004);
        add("Bathroom", 0, 0);
        add("Hallway", 0, 8993);
        add("Bedroom", 18, 9811);
        add("Living room", 57, 14752);
        add("Bathroom", 59, 10125);
        add("Hallway", 41, 12049);
        add("Bedroom", 0, 9599);
        add("Living room", 0, 13543);
        add("Bathroom", 0, 0);
        add("Hallway", 0, 10467);
        add("Bedroom", 76, 12499);
        add("Living room", 95, 17058);
        add("Hallway", 99, 17859);
        add("Bedroom", 0, 10391);
        add("Living room", 0, 15194);
        add("Hallway", 0, 9262);
        add("Bedroom", 20, 9376);
        add("Living room", 31, 15239);
        add("Hallway", 16, 8993);
        add("Bedroom", 0, 9141);
        add("Living room", 0, 13915);
        add("Hallway", 0, 9084);
        add("Bedroom", 34, 9811);
        add("Living room", 76, 15676);
        add("Living room", 0, 13310);
        add("Hallway", 9, 8706);
        add("Bedroom", 0, 9599);
        add("Living room", 35, 14119);
        add("Hallway", 0, 7954);
        add("Bedroom", 39, 9141);
        add("Living room", 0, 13241);
        add("Hallway", 47, 12442);
        add("Bedroom", 0, 8630);
        add("Living room", 25, 13575);
        add("Hallway", 0, 7713);
        add("Bedroom", 30, 8893);
        add("Living room", 0, 13733);
        add("Hallway", 80, 16146);
        add("Bedroom", 0, 9376);
        add("Living room", 21, 13445);
        add("Hallway", 0, 7713);
        add("Bedroom", 97, 12151);
        add("Living room", 0, 14367);
        add("Hallway", 11, 9174);
        add("Bedroom", 0, 8630);
        add("Living room", 37, 13639);
        add("Hallway", 0, 7954);
        add("Bedroom", 95, 11502);
        add("Living room", 0, 13825);
        add("Hallway", 79, 15983);
        add("Bedroom", 0, 9141);
        add("Living room", 31, 13795);
        add("Hallway", 0, 8293);
        add("Bedroom", 48, 9811);
        add("Living room", 0, 13856);
        add("Hallway", 14, 8706);
        add("Bedroom", 0, 8893);
        add("Living room", 31, 13915);
        add("Hallway", 0, 8183);
        add("Bedroom", 61, 10206);
        add("Living room", 0, 13136);
        add("Hallway", 10, 8706);
        add("Bedroom", 0, 9599);
        add("Living room", 34, 14033);
        add("Bedroom", 17, 9141);
        add("Living room", 0, 14367);
        add("Bedroom", 0, 9811);
        add("Hallway", 0, 9262);
    }
    private void add(String bedroom, int i, int i0)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
