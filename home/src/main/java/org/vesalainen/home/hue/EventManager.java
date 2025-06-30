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

import java.io.BufferedReader;
import java.io.IOException;
import static java.lang.Integer.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.prefs.Preferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.json.XML;
import org.vesalainen.home.hue.Resources.Resource;
import org.vesalainen.math.LocalTimeCubicSpline;
import org.vesalainen.util.ConvertUtility;
import org.vesalainen.util.concurrent.CachedScheduledThreadPool;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class EventManager extends JavaLogging
{
    private static final int VERSION = 1;
    private static final JSONPointer LIGHT_LEVEL = new JSONPointer("/light/light_level");
    private static final JSONPointer GROUPED_LIGHT_LEVEL = new JSONPointer("/light/light_level_report/light_level");
    private static final JSONPointer BRIGHTNESS = new JSONPointer("/dimming/brightness");
    private static final JSONPointer ONON = new JSONPointer("/on/on");
    private static final JSONPointer RID = new JSONPointer("/owner/rid");
    private static JSONPointer MOTION = new JSONPointer("/motion/motion");
    private static JSONPointer GROUPED_MOTION = new JSONPointer("/motion/motion_report/motion");
    private static JSONObject ON = JSON.build("/on/on", true).get();
    private static JSONObject OFF = JSON.build("/on/on", false).get();
    private Hue hue;
    private String start;
    private Path path;
    private CachedScheduledThreadPool pool = new CachedScheduledThreadPool();
    private HueManager hueManager;
    private Map<String,Device> deviceMap = new HashMap<>();
    private Map<String,Sensor> sensorMap = new HashMap<>();
    private Map<String,Sensor> sensorLightMap = new HashMap<>();

    public EventManager(Path path) throws IOException
    {
        super(EventManager.class);
        this.path = path;
        FileSystem fileSystem = path.getFileSystem();
    }
    public void start() throws IOException
    {
        this.hue = new Hue("testApp");
        hue.readAllResources();
        loadConfig();
        info("start reading events");
        hue.events(this::event);
    }
    public void event(JSONObject ev)
    {
        JSONArray ja = ev.getJSONArray("data");
        for (Object o : ja)
        {
            try
            {
                JSONObject jo = (JSONObject) o;
                //logEvent(jo);
                Resource res = hue.getResource((String) RID.queryFrom(o));
                if (res != null)
                {
                    String type = jo.getString("type");
                    hueManager.event(res, type, jo);
                }
            }
            catch (JSONPointerException ex)
            {
                //log(SEVERE, ex, "event: %s", ja);
            }
        }
    }
    private boolean addNode(String name, Object json)
    {
        if (name.equals("hue"))
        {
            config("add node %s", name);
            hueManager = new HueManager((JSONObject) json, null);
            JSON.walk(json, hueManager::populate);
            hueManager.init();
            return true;
        }
        else
        {
            return false;
        }
    }

    private void loadConfig() throws IOException
    {
        info("loading config");
        try (BufferedReader br = Files.newBufferedReader(path))
        {
            JSONObject jo = XML.toJSONObject(br);
            JSON.walk(jo, this::addNode);
        }
    }

    private void logEvent(JSONObject jo)
    {
        String id = (String) JSON.get(jo, "/owner/rid");
        Resource resource = hue.getResource(id);
        String name = resource!=null?resource.getName():null;
        String type = jo.getString("type");
        switch (type)
        {
            case "grouped_light":
                info("grouped light %s = %s %s", name, JSON.get(jo, "/on/on"), JSON.get(jo, "/dimming/brightness"));
                break;
            case "light":
                info("light %s = pup %s", name, JSON.get(jo, "/powerup/configured"));
                break;
            case "scene":
                info("scene %s = %s", name, JSON.get(jo, "/status/active"));
                break;
            case "smart_scene":
                info("smart scene %s = %s", name, JSON.get(jo, "/state"));
                break;
            case "temperature":
                info("temperature %s = %s", name, JSON.get(jo, "/temperature/temperature"));
                break;
            case "grouped_light_level":
                info("light level %s = %s", name, JSON.get(jo, "/light/light_level_report/light_level"));
                break;
            case "light_level":
                info("light level %s = %s", name, JSON.get(jo, "/light/light_level"));
                break;
            case "motion":
                info("motion %s = %s", name, JSON.get(jo, "/motion/motion"));
                break;
            case "grouped_motion":
                info("grouped motion %s = %s", name, JSON.get(jo, "/motion/motion_report/motion"));
                break;
            default:
                info("event %s %s", type, name);
                JSON.dump(jo);
                break;
        }
    }
    private boolean hasMotion(JSONObject jo)
    {
        String type = jo.getString("type");
        switch (type)
        {
            case "motion":
            case "grouped_motion":
                return true;
            default:
                return false;
        }
    }
    private boolean getMotion(JSONObject jo)
    {
        String type = jo.getString("type");
        switch (type)
        {
            case "motion":
                return (boolean) MOTION.queryFrom(jo);
            case "grouped_motion":
                return (boolean) GROUPED_MOTION.queryFrom(jo);
            default:
                throw new IllegalArgumentException(type+" not motion");
        }
    }
    private int getMirek(LocalTime time)
    {
        double d = hueManager.lights.temperature.spline.applyAsDouble(time);
        return (max(153,min(500, (int) d)));
    }
    private int getBrightness(LocalTime time)
    {
        double d = hueManager.lights.level.spline.applyAsDouble(time);
        return (max(0,min(100, (int) d)));
    }
    private abstract class Node
    {
        protected JSONObject json;
        protected Node parent;

        public Node(JSONObject json, Node parent)
        {
            this.json = json;
            this.parent = parent;
        }
        public Node getParent()
        {
            return parent;
        }
        public <T extends Node> T getParent(Class<T> type)
        {
            Node p = getParent();
            while (p != null && !p.getClass().equals(type))
            {
                p = p.getParent();
            }
            return (T) p;
        }
        final boolean populate(String name, Object json)
        {
            String pck = EventManager.class.getName();
            try
            {
                String s = pck+"$"+name.substring(0, 1).toUpperCase()+name.substring(1);
                Class<?> cls = Class.forName(s);
                Constructor<?> cons = cls.getDeclaredConstructor(EventManager.class, JSONObject.class, Node.class);
                Node node = (Node) cons.newInstance(EventManager.this, json, this);
                JSON.walk(json, node::populate);
                node.init();
                try
                {
                    fieldAdd(this, node);
                }
                catch (NoSuchFieldException ex)
                {
                    try
                    {
                        fieldSet(this, name, node);
                    }
                    catch (NoSuchFieldException e)
                    {
                        if (!assign(name, node))
                        {
                            throw new IllegalArgumentException(name+" not assigned");
                        }
                    }
                }
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException ex)
            {
                if (!assign(name, json))
                {
                    try
                    {
                        fieldSet(this, name, json);
                    }
                    catch (NoSuchFieldException ex1)
                    {
                        throw new RuntimeException(ex1);
                    }
                }
            }
            return true;
        }
        protected void init(){}
        protected boolean assign(String name, Object value){return false;}

        private void fieldSet(Node node, String name, Object value) throws NoSuchFieldException
        {
            Class<? extends Node> cls = node.getClass();
            while (cls != null)
            {
                try
                {
                    Field field = cls.getDeclaredField(name);
                    field.set(node, value);
                    return;
                }
                catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex)
                {
                }
                cls = (Class<? extends Node>) cls.getSuperclass();
            }
            throw new NoSuchFieldException(name+" not found");
        }
        private void fieldAdd(Node node, Node value) throws NoSuchFieldException
        {
            Class<? extends Node> nodeCls = node.getClass();
            while (nodeCls != null)
            {
                Class<?> valueCls = value.getClass();
                while (valueCls != null)
                {
                    String name = valueCls.getSimpleName();
                    name = name.toLowerCase();
                    try
                    {
                        Field field = nodeCls.getDeclaredField(name+"s");
                        List<Node> list = (List) field.get(this);
                        list.add(value);
                        return;
                    }
                    catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex)
                    {
                    }
                    valueCls = (Class<?>) valueCls.getSuperclass();
                }
                nodeCls = (Class<? extends Node>) nodeCls.getSuperclass();
            }
            throw new NoSuchFieldException(value+" not found");
        }
        
    }
    private class HueManager extends Node
    {
        protected Motions motions;
        protected Lights lights;
        public HueManager(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(Resource res, String type, JSONObject jo)
        {
            switch (type)
            {
                case "scene":
                    break;
                case "smart_scene":
                    break;
                case "temperature":
                    break;
                case "grouped_light":
                case "light":
                case "grouped_light_level":
                case "light_level":
                    lights.event(res, type, jo);
                    break;
                case "motion":
                case "grouped_motion":
                    motions.event(res, type, jo);
                    break;
                default:
                    break;
            }
        }
        
    }
    private class Lights extends Node
    {
        protected List<Sensor> sensors = new ArrayList<>();
        protected List<Device> devices = new ArrayList<>();
        protected Level level;
        protected Temperature temperature;
        
        public Lights(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        @Override
        protected void init()
        {
            HueManager mgr = (HueManager) parent;
            mgr.lights = this;
            for (Sensor sensor : sensors)
            {
                sensorMap.put(sensor.name, sensor);
                sensorLightMap.put(sensor.lightName, sensor);
            }
        }

        private void event(Resource res, String type, JSONObject jo)
        {
            Sensor sensor = null;
            String name = res.getName();
            switch (type)
            {
                case "grouped_light":
                case "light":
                    sensor = sensorLightMap.get(name);
                    if (sensor != null)
                    {
                        sensor.lightEvent(res, type, jo);
                    }
                    else
                    {
                        Device device = deviceMap.get(name);
                        if (device != null)
                        {
                            device.event((boolean) ONON.queryFrom(jo));
                        }
                    }
                    break;
                case "grouped_light_level":
                case "light_level":
                    sensor = sensorMap.get(name);
                    if (sensor != null)
                    {
                        sensor.sensorEvent(res, type, jo);
                    }
                    break;
            }
        }        
    }
    private class Circadian extends Node
    {
        protected List<Point> points = new ArrayList<>();
        protected LocalTimeCubicSpline spline;
        public Circadian(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            LocalTimeCubicSpline.Builder builder = LocalTimeCubicSpline.builder();
            for (Point point : points)
            {
                builder.add(point.time, point.value);
            }
            spline = builder.build();
        }
        
    }
    private class Level extends Circadian
    {
        public Level(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        
    }
    private class Temperature extends Circadian
    {
        public Temperature(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        
    }
    private class Point extends Node
    {
        protected LocalTime time;
        protected double value;
        public Point(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected boolean assign(String name, Object value)
        {
            switch(name)
            {
                case "time":
                    time = LocalTime.parse(value.toString());
                    return true;
                default:
                    return false;
            }
        }
        
    }
    private class Sensor extends Node
    {
        protected String name;
        protected String lightName;
        protected int target;
        private double onLevel = Double.NaN;
        private double offLevel = Double.NaN;
        private double brightness = Double.NaN;
        private boolean on;
        private LightLevelFitter dimmer;
        private Preferences preferences;
        private Stats stats;
        public Sensor(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            preferences = Preferences.userNodeForPackage(Sensor.class);
            int version = preferences.getInt(name+"_version", 0);
            if (version != VERSION)
            {
                stats = new Stats(this);
                dimmer = new LightLevelFitter(0.8, 68);
            }
            else
            {
                double a = preferences.getDouble(name+"_a", 0.8);
                double b = preferences.getDouble(name+"_b", 68);
                dimmer = new LightLevelFitter(a, b);
            }
        }
        
        private boolean isNeeded()
        {
            if (stats == null)
            {
                return Double.isNaN(offLevel) || target > offLevel;
            }
            else
            {
                return true;
            }
        }
        private int getBrightness(int base)
        {
            return base;
            /*
            if (Double.isFinite(offLevel) && Double.isFinite(onLevel) && stats == null)
            {
                double trg = target*base/100;
                double dimm = dimmer.getDimm(offLevel, trg);
                return max(0,min(100, (int) dimm));
            }
            else
            {
                if (stats == null)
                {
                    return base;
                }
                else
                {
                    Random r = new Random();
                    return r.nextInt(100);
                }
            }*/
        }
        
        private void sensorEvent(Resource res, String type, JSONObject jo)
        {
            Object o = null;
            switch (type)
            {
                case "grouped_light_level":
                    o = GROUPED_LIGHT_LEVEL.queryFrom(jo);
                    info("EVENT: %s %s %s", name, type, o);
                    if (o != null)
                    {
                        if (on)
                        {
                            onLevel = ConvertUtility.convert(double.class, o);
                        }
                        else
                        {
                            offLevel = ConvertUtility.convert(double.class, o);
                        }
                        stats();
                    }
                    break;
                case "light_level":
                    o = LIGHT_LEVEL.queryFrom(jo);
                    info("EVENT: %s %s %s", name, type, o);
                    if (o != null)
                    {
                        if (on)
                        {
                            onLevel = ConvertUtility.convert(double.class, o);
                        }
                        else
                        {
                            offLevel = ConvertUtility.convert(double.class, o);
                        }
                        stats();
                    }
                    break;
            }
            info("%s", this);
        }        
        private void lightEvent(Resource res, String type, JSONObject jo)
        {
            long limit = System.currentTimeMillis()-1000000;
            Object o = null;
            switch (type)
            {
                case "grouped_light":
                    o = ONON.queryFrom(jo);
                    info("EVENT: %s %s on=%s", name, type, o);
                    if (o != null)
                    {
                        on = ConvertUtility.convert(boolean.class, o);
                    }
                case "light":
                    o = BRIGHTNESS.queryFrom(jo);
                    info("EVENT: %s %s br=%s", name, type, o);
                    if (o != null)
                    {
                        brightness = ConvertUtility.convert(double.class, o);
                    }
                    break;
            }
        }        

        @Override
        public String toString()
        {
            double[] params = dimmer.getParams();
            return "Sensor{" + "name=" + lightName + ", on=" + onLevel + ", off=" + offLevel + ", brightness=" + brightness + ", a=" + params[0] + ", b="+params[1];
        }

        private void stats()
        {
            if (stats != null)
            {
                stats.event();
            }
        }
    }
    private class Stats
    {
        private Sensor sensor;
        private long done;
        private Boolean state;
        public Stats(Sensor sensor)
        {
            this.sensor = sensor;
            this.done = System.currentTimeMillis()+24*60*60*1000;
        }
        public void event()
        {
            if (
                    Double.isFinite(sensor.brightness) && 
                    Double.isFinite(sensor.offLevel) && 
                    Double.isFinite(sensor.onLevel) &&
                    sensor.onLevel > sensor.offLevel
                    )
            {
                if (state != null)
                {
                    if (state != sensor.on)
                    {
                        sensor.dimmer.addPoints(sensor.offLevel, sensor.onLevel, sensor.brightness);
                        info("add points to %s (%f, %f, %f);", sensor.name, sensor.offLevel, sensor.onLevel, sensor.brightness);
                        if (System.currentTimeMillis() > done)
                        {
                            double cost = sensor.dimmer.fit();
                            double[] params = sensor.dimmer.getParams();
                            sensor.preferences.putDouble(sensor.name+"_a", params[0]);
                            sensor.preferences.putDouble(sensor.name+"_b", params[1]);
                            sensor.preferences.putInt(sensor.name+"_version", VERSION);
                            info("FIT %s %f %f", sensor.name, params[0], params[1]);
                            sensor.stats = null;
                        }
                    }
                }
                state = sensor.on;
            }
        }
    }
    private class Motions extends Node
    {
        protected Enter enter;
        protected List<Motion> motions = new ArrayList<>();
        private Map<String,Motion> motionMap = new HashMap<>();
        public Motions(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            HueManager mgr = (HueManager) parent;
            mgr.motions = this;
            for (Motion motion : motions)
            {
                motionMap.put(motion.name, motion);
            }
        }

        private void event(Resource res, String type, JSONObject jo)
        {
            String name = res.getName();
            boolean act = getMotion(jo);
            Motion motion = motionMap.get(name);
            if (motion != null)
            {
                int onCount = onCount();
                motion.on(act);
                info("motion %s %s %d", name, act, onCount);
                if (act)
                {
                    if (onCount == 0)
                    {
                        enter.event(act);
                    }
                    motion.event(act);
                }
                else
                {
                    if (onCount == 1)
                    {
                        if (!motion.exit(act))
                        {
                            motion.event(act);
                        }
                    }
                    else
                    {
                        motion.event(act);
                    }
                }
            }
        }

        private int onCount()
        {
            int cnt = 0;
            for (Motion m : motions)
            {
                if (m.on)
                {
                    cnt++;
                }
            }
            return cnt;
        }
        
    }
    private class Enter extends Node
    {
        protected List<Device> devices = new ArrayList<>();
        public Enter(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            info("enter");
            for (Device device : devices)
            {
                device.event(act);
            }
        }
        
    }
    private class Motion extends Node
    {
        protected String name;
        protected List<Device> devices = new ArrayList<>();
        protected Exit exit;
        protected boolean on;
        public Motion(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            for (Device device : devices)
            {
                device.event(act);
            }
        }

        private boolean exit(boolean act)
        {
            info("exit %s", name);
            if (exit != null)
            {
                exit.event(act);
                return true;
            }
            else
            {
                return false;
            }
        }

        @Override
        protected void init()
        {
            hue.checkName(name);
        }

        private void on(boolean act)
        {
            this.on = act;
        }
        
    }
    private class Exit extends Node
    {
        protected List<Device> devices = new ArrayList<>();
        public Exit(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            info("exit");
            for (Device device : devices)
            {
                device.event(act);
            }
        }
        
    }
    private class Device extends Node
    {
        protected String name;
        protected String action;
        protected long delay;
        protected String type;
        private ScheduledFuture<?> future;
        private Collection<Resource> on;
        public Device(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        protected void event(boolean act)
        {
            info("device %s %s", name, act);
            if (action != null && "reverse".equals(action))
            {
                act = !act;
            }
            if (act)
            {
                on();
            }
            else
            {
                off();
            }
        }

        @Override
        protected void init()
        {
            hue.checkName(name);
            on = hue.getResource(name, "/on/on:true");
        }
        protected void schedule(Runnable act, long delay)
        {
            if (delay == 0)
            {
                act.run();
            }
            else
            {
                future = pool.schedule(act, delay, SECONDS);
            }
        }
        protected boolean cancel()
        {
            if (future != null)
            {
                boolean done = future.isDone();
                future.cancel(true);
                future = null;
                return done;
            }
            else
            {
                return true;
            }
        }

        protected void on()
        {
            cancel();
            hue.update(on, ON);
        }

        protected void off()
        {
            schedule(()->hue.update(on, OFF), delay);
        }
        
    }
    private class Light extends Device
    {
        private Collection<Resource> brightness;
        private Collection<Resource> temperature;
        private Sensor sensor;
        private double dim = 1.0;
        public Light(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void on()
        {
            boolean isNeeded = true;
            LocalTime now = LocalTime.now();
            int br = getBrightness(now);
            ensureSensor();
            if (sensor != null)
            {
                isNeeded = sensor.isNeeded();
                br = sensor.getBrightness(br);
            }
            if (isNeeded)
            {
                hue.update(temperature, "/color_temperature/mirek:"+getMirek(now));
                hue.update(brightness, "/dimming/brightness:"+br*dim);
                super.on();
            }
        }

        @Override
        protected void init()
        {
            super.init();
            brightness = hue.getResource(name, "/dimming/brightness:80");
            temperature = hue.getResource(name, "/color_temperature/mirek:80");
            deviceMap.put(name, this);
        }
        private Sensor ensureSensor()
        {
            if (sensor == null)
            {
                Lights lights = hueManager.lights;
                if (lights != null)
                {
                    sensor = sensorLightMap.get(name);
                }
            }
            return sensor;
        }

        private void dim(double value)
        {
            this.dim = value/100;
            LocalTime now = LocalTime.now();
            int br = getBrightness(now);
            ensureSensor();
            boolean isNeeded = false;
            if (sensor != null)
            {
                isNeeded = sensor.isNeeded();
                br = sensor.getBrightness(br);
            }
            if (isNeeded)
            {
                hue.update(brightness, "/dimming/brightness:"+br*dim);
            }
        }
    }
    private class Controller extends Device
    {
        protected String target;
        protected double value;
        public Controller(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void event(boolean act)
        {
            switch (action)
            {
                case "dim":
                    dim(act);
                    break;
                default:
                    severe("Controller action %s not found!", action);
                    break;
            }
        }

        private void dim(boolean act)
        {
            Light light = (Light) deviceMap.get(target);
            if (light == null)
            {
                severe("Light %s not found!", target);
            }
            else
            {
                if (act)
                {
                    light.dim(value);
                }
                else
                {
                    light.dim(100);
                }
            }
        }

        @Override
        protected void init()
        {
            deviceMap.put(name, this);
        }

    }
}
