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

import org.vesalainen.util.CheckList;
import java.io.BufferedReader;
import java.io.IOException;
import static java.lang.Integer.*;
import static java.lang.Math.abs;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.json.XML;
import org.vesalainen.home.hue.Resources.Resource;
import org.vesalainen.math.LocalTimeCubicSpline;
import static org.vesalainen.math.UnitType.DURATION_MILLI_SECONDS;
import org.vesalainen.net.dns.Message;
import org.vesalainen.net.dns.ResourceRecord;
import org.vesalainen.util.HashMapList;
import org.vesalainen.util.MapList;
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
    private ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
    private HueManager hueManager;
    private Set<Node> nodeSet = new HashSet<>();
    private MapList<String,Device> deviceMap = new HashMapList<>();

    public EventManager(Path path) throws IOException
    {
        super(EventManager.class);
        this.path = path;
    }
    public void start() throws IOException
    {
        this.hue = new Hue("testApp");
        hue.readAllResources();
        loadConfig();
        for (Node node : nodeSet)
        {
            node.postInit();
        }
        pool.scheduleWithFixedDelay(this::updateLights, 1, 5, TimeUnit.MINUTES);
        info("start reading events");
        hue.events(this::event);
    }
    private void event(JSONObject ev)
    {
        pool.execute(()->handleEvent(ev));
    }
    private void handleEvent(JSONObject ev)
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
                    //info("vvvvvvvvvvvvvvvvvv EVENT vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
                    hueManager.event(res, type, jo);
                    //info("^^^^^^^^^^^^^^^^^^ EVENT ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                }
            }
            catch (JSONPointerException ex)
            {
                //log(SEVERE, ex, "event: %s", ja);
            }
            catch (Exception ex)
            {
                log(SEVERE, ex, "event: %s", ja);
            }
        }
    }
    private void updateLights()
    {
        for (Light light : hueManager.lights.lights)
        {
            light.updateLight();
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
    private int getMirek()
    {
        double d = hueManager.lights.temperature.spline.applyAsDouble(LocalTime.now());
        return (max(153,min(500, (int) d)));
    }
    private int getBrightness()
    {
        double d = hueManager.lights.level.spline.applyAsDouble(LocalTime.now());
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
                if (json instanceof JSONObject)
                {
                    String s = pck+"$"+name.substring(0, 1).toUpperCase()+name.substring(1);
                    Class<?> cls = Class.forName(s);
                    Constructor<?> cons = cls.getDeclaredConstructor(EventManager.class, JSONObject.class, Node.class);
                    Node node = (Node) cons.newInstance(EventManager.this, json, this);
                    nodeSet.add(node);
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
                else
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
            }
            catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
            {
                throw new RuntimeException(ex);
            }
            return true;
        }
        protected void init(){}
        protected void postInit(){}
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
                case "button":
                case "relative_rotary":
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
        protected List<Light> lights = new ArrayList<>();
        protected List<Mdns> mdnss = new ArrayList<>();
        protected Level level;
        protected Temperature temperature;
        private Light lastOn;
        private MDNS mdns;
        
        public Lights(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        @Override
        protected void init()
        {
            HueManager mgr = (HueManager) parent;
            mgr.lights = this;
            for (Light light : lights)
            {
                for (String name : light.getNames())
                {
                    deviceMap.add(name, light);
                }
                String sensor = light.sensor;
                if (sensor != null)
                {
                    deviceMap.add(sensor, light);
                }
            }
        }

        @Override
        protected void postInit()
        {
            if (!mdnss.isEmpty())
            {
                mdns = new MDNS();
                mdns.startListening(this::handleMDns);
            }
        }

        private void event(Resource res, String type, JSONObject jo)
        {
            String name = res.getName();
            switch (type)
            {
                case "grouped_light":
                case "light":
                case "grouped_light_level":
                case "light_level":
                case "button":
                case "relative_rotary":
                    for (Device device : deviceMap.get(name))
                    {
                        device.event(res, type, jo);
                    }
                    break;
            }
        }        

        private void handleMDns(Message message)
        {
            if (message.isAuthoritative() && !message.isQuery())
            {
                ResourceRecord[] answers = message.getAnswers();
                if (answers != null)
                {
                    for (ResourceRecord rr : answers)
                    {
                        String name = rr.getName().toString();
                        int ttl = rr.getTtl();
                        for (Mdns mdns : mdnss)
                        {
                            mdns.handle(name, ttl);
                        }
                    }
                }
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
    private class Named extends Node
    {
        protected String name;

        public Named(String name)
        {
            super(null, null);
            this.name = name;
            init();
        }
        
        public Named(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            super.init();
            if (!name.startsWith("_"))
            {
                hue.checkName(name);
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
            super.init();
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
                cancelExists();
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

        private void cancelExists()
        {
            for (Motion motion : motionMap.values())
            {
                motion.cancelExit();
            }
        }
        
    }
    private class Enter extends Node
    {
        protected List<Action> actions = new ArrayList<>();
        public Enter(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            info("enter");
            for (Action action : actions)
            {
                action.event(act);
            }
        }
        
    }
    private class Motion extends Named
    {
        protected List<Action> actions = new ArrayList<>();
        protected Exit exit;
        protected boolean on;
        public Motion(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            for (Action action : actions)
            {
                action.event(act);
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
            super.init();
        }

        private void on(boolean act)
        {
            this.on = act;
        }

        private void cancelExit()
        {
            if (exit != null)
            {
                exit.cancelExit();
            }
        }
        
    }
    private class Exit extends Node
    {
        protected List<Action> actions = new ArrayList<>();
        public Exit(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            for (Action action : actions)
            {
                action.event(act);
            }
        }

        private void cancelExit()
        {
            for (Action action : actions)
            {
                action.cancel();
            }
        }
        
    }
    private class Device extends Named
    {
        private Collection<Resource> updOn;
        protected String type;
        protected boolean on;
        protected boolean setOn;
        public Device(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        public Device(String name)
        {
            super(name);
        }

        protected void event(Resource res, String type, JSONObject jo)
        {
            switch (type)
            {
                case "light":
                case "grouped_light":
                    Boolean b = (Boolean) JSON.get(jo, "/on/on");
                    if (b != null)
                    {
                        setOn(b);
                    }
                    break;
            }
        }
        
        protected void on()
        {
            if (!on)
            {
                hue.update(updOn, ON);
            }
            else
            {
                fine("%s not set on because already on", name);
            }
            setOn = true;
        }

        protected void off()
        {
            if (on)
            {
                hue.update(updOn, OFF);
            }
            else
            {
                fine("%s not set off because already off", name);
            }
            setOn = false;
        }

        @Override
        protected void init()
        {
            super.init();
            updOn = hue.getResource(name, "/on/on:true");
            Boolean bb = (Boolean) hue.getValue(name, "/on/on:true");
            if (bb != null)
            {
                on = bb.booleanValue();
            }
        }

        protected void setOn(boolean b)
        {
            on = b;
        }
   }
    private enum DEEDS {SET_MIREK, SET_BRIGHTNESS, GOT_ON, GOT_BRIGHTNESS, GOT_ON_LEVEL, SET_OFF, SET_ON, GOT_OFF_LEVEL};
    private class Light extends Device
    {
        private CheckList<DEEDS> check;
        private Collection<Resource> updBrightness;
        private Collection<Resource> updTemperature;
        protected String sensor;
        protected List<Action> actions = new ArrayList<>();
        protected int target = Integer.MAX_VALUE;
        private double brightness;
        private double dim = 1.0;
        private double fineAdj = 1.0;
        private double adj = 1.0;
        private int onLevel = Integer.MAX_VALUE;
        private int offLevel;
        private double setBrightness;
        private long updated;
        private boolean manualOn;
        private int mirek;
        private double colorX;
        private double colorY;
        public Light(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        public Light(String name)
        {
            super(name);
        }

        protected void updateLight()
        {
            int trg = target();
            config("UPD %s off=%d trg=%d", name, offLevel, trg);
            int mir = getMirek();
            if (mir != mirek)
            {
                hue.update(updTemperature, "/color_temperature/mirek:"+mir);
            }
            else
            {
                fine("%s mirek not set because it stays %d", name, mirek);
            }
            check.done(DEEDS.SET_MIREK);
            if (!check.isDone(DEEDS.SET_OFF) || offLevel < trg)
            {
                setBrightness = brightness();
            }
            else
            {
                setBrightness = 0;
            }
            if (!eq(setBrightness, brightness))
            {
                hue.update(updBrightness, "/dimming/brightness:"+setBrightness);
            }
            else
            {
                fine("%s brightness not set because it stays %f", name, brightness);
            }
            check.done(DEEDS.SET_BRIGHTNESS);
            updated = System.currentTimeMillis();
        }
        private double brightness()
        {
            int trg = target();
            int br = getBrightness();
            br = max(0, min(100, (int) (br*adj*fineAdj)));
            return br*dim;
        }
        private void toggle()
        {
            if (on)
            {
                off();
            }
            else
            {
                on();
            }
        }
        private int target()
        {
            return (int) (dim*adj*target*getBrightness()/100);
        }
        @Override
        protected void init()
        {
            super.init();
            check = new CheckList<>(DEEDS.class, ()->info("%s ready!", name));
            updBrightness = hue.getResource(name, "/dimming/brightness:80");
            BigDecimal br = (BigDecimal) hue.getValue(name, "/dimming/brightness:80");
            if (br != null)
            {
                brightness = br.doubleValue();
                check.done(DEEDS.GOT_BRIGHTNESS);
            }
            updTemperature = hue.getResource(name, "/color_temperature/mirek:80");
            for (Resource r : updTemperature)
            {
                Integer m = (Integer) hue.getValue(r.getName(), "/color_temperature/mirek:80");
                if (m != null)
                {
                    mirek = m;
                }
            }
            Boolean bb = (Boolean) hue.getValue(name, "/on/on:true");
            if (bb != null)
            {
                on = bb.booleanValue();
                check.done(DEEDS.GOT_ON);
                Integer ll = (Integer) hue.getValue(name, "/light/light_level_report/light_level:0.0");
                if (ll != null)
                {
                    if (on)
                    {
                        onLevel = ll.intValue();
                        check.done(DEEDS.GOT_ON_LEVEL);
                    }
                    else
                    {
                        offLevel = ll.intValue();
                        check.done(DEEDS.GOT_OFF_LEVEL);
                    }
                }
                else
                {
                    ll = (Integer) hue.getValue(name, "/light/light_level:0.0");
                    if (ll != null)
                    {
                        if (on)
                        {
                            onLevel = ll.intValue();
                            check.done(DEEDS.GOT_ON_LEVEL);
                        }
                        else
                        {
                            offLevel = ll.intValue();
                            check.done(DEEDS.GOT_OFF_LEVEL);
                        }
                    }
                }
            }
        }

        private void dim(double value)
        {
            dim = value/100;
            updateLight();
        }

        @Override
        protected void event(Resource res, String type, JSONObject jo)
        {
            switch (type)
            {
                case "light":
                    Integer m = (Integer) JSON.get(jo, "/color_temperature/mirek");
                    if (m != null)
                    {
                        mirek = m;
                        fine("%s mirek=%d", name, mirek);
                    }
                    BigDecimal x = (BigDecimal) JSON.get(jo, "/color/xy/x");
                    if (x != null)
                    {
                        colorX = x.doubleValue();
                        fine("%s color-x=%f", name, x);
                    }
                    BigDecimal y = (BigDecimal) JSON.get(jo, "/color/xy/x");
                    if (y != null)
                    {
                        colorY = y.doubleValue();
                        fine("%s color-y=%f", name, y);
                    }
                    break;
                case "grouped_light":
                    Boolean b = (Boolean) JSON.get(jo, "/on/on");
                    if (b != null)
                    {
                        setOn(b);
                        for (Action action : actions)
                        {
                            action.event(on);
                        }
                        if (on)
                        {
                            hueManager.lights.lastOn = this;
                            info("last on %s", name);
                        }
                    }
                    BigDecimal bd = (BigDecimal) JSON.get(jo, "/dimming/brightness");
                    if (bd != null)
                    {
                        check.done(DEEDS.GOT_BRIGHTNESS);
                        setBrightness(bd.doubleValue());
                    }
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
        private void setBrightness(double v)
        {
            info("%s brightness=%f", name, v);
            brightness = v;
            if (on && abs(brightness - setBrightness) > 10)
            {
                if (System.currentTimeMillis() - updated < 3000)
                {
                    pool.execute(this::updateLight);
                }
                else
                {
                    if (check.isDone(DEEDS.GOT_BRIGHTNESS, DEEDS.SET_BRIGHTNESS))
                    {
                        if (brightness > 0 && setBrightness > 0)
                        {
                            adj = brightness / (setBrightness / adj);
                            info("BRIGHTNESS %s %f <> %f adj=%f", name, brightness, setBrightness, adj);
                            setBrightness = brightness;
                        }
                        else
                        {
                            if (brightness > 0)
                            {
                                adj = 1;
                            }
                            else
                            {
                                adj = 0;
                            }
                            info("BRIGHTNESS %s %f <> %f adj=%f", name, brightness, setBrightness, adj);
                        }
                        fineAdj = 1.0;
                    }
                }
            }
        }

        private void fineAdjust()
        {
            if (check.ready())
            {
                int trg = target();
                double bef = fineAdj;
                if (onLevel < trg && brightness < 100)
                {
                    fineAdj = Double.min(2, fineAdj+0.1);
                }
                if (onLevel > trg && brightness > 0)
                {
                    fineAdj = Double.max(0, fineAdj-0.1);
                }
                updateLight();
                config("FINE %s trg=%d on=%d %.1f -> %.1f", name, trg, onLevel, bef, fineAdj);
            }
        }

        private void updateLevel(Integer level)
        {
            if (level != null && check.isDone(DEEDS.SET_ON))
            {
                if (on)
                {
                    check.done(DEEDS.GOT_ON_LEVEL);
                    onLevel = level;
                    info("%s onLevel=%d", name, level);
                    fineAdjust();
                }
                else
                {
                    check.done(DEEDS.GOT_OFF_LEVEL);
                    offLevel = level;
                    info("%s ofLevel=%d", name, level);
                    updateLight();
                }
            }
        }

        @Override
        protected void off()
        {
            if (!manualOn)
            {
                info("%s set off", name);
                super.off();
                check.done(DEEDS.SET_OFF);
            }
            else
            {
                fine("%s not set off because manual control", name);
            }
        }

        @Override
        protected void on()
        {
            if (!on)
            {
                info("%s set on", name);
                super.on();
                check.done(DEEDS.SET_ON);
            }
            else
            {
                fine("%s not set on because already on", name);
            }
        }

        @Override
        protected void setOn(boolean b)
        {
            super.setOn(b);
            check.done(DEEDS.GOT_ON);
            info("%s on=%s", name, on);
            if (check.isDone(DEEDS.SET_ON, DEEDS.SET_OFF))
            {
                if (manualOn)
                {
                    manualOn = on;
                }
                else
                {
                    if (setOn != on)
                    {
                        info("ON %s set=%s <> on=%s manual=%s", name, setOn, on, on);
                        manualOn = on;
                    }
                }
            }
            if (on)
            {
                updated = System.currentTimeMillis();
            }
        }

        private Collection<String> getNames()
        {
            Set<String> names = new HashSet<>();
            names.add(name);
            for (Resource res : updBrightness)
            {
                names.add(res.getName());
            }
            for (Resource res : updTemperature)
            {
                names.add(res.getName());
            }
            return names;
        }

        private boolean eq(double a, double b)
        {
            return abs(a-b) < 2;
        }

    }
    private class Action<T extends Device> extends Named
    {
        protected T device;
        private long delay;
        private ScheduledFuture<?> future;
        public Action(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        protected void event(boolean act)
        {
            if (act)
            {
                cancel();
                on();
            }
            else
            {
                schedule(this::off, delay);
            }
        }

        protected void schedule(Runnable act, long delay)
        {
            if (delay == 0)
            {
                act.run();
            }
            else
            {
                future = pool.schedule(act, delay, MILLISECONDS);
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
            device.on();
        }

        protected void off()
        {
            device.off();
        }

        @Override
        protected boolean assign(String name, Object value)
        {
            switch (name)
            {
                case "delay":
                    delay = (long) DURATION_MILLI_SECONDS.parse((CharSequence) value);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        protected void postInit()
        {
            if (!name.startsWith("_"))
            {
                device = (T) deviceMap.getSingle(name);
                if (device == null)
                {
                    device = (T) newDevice(name);
                    deviceMap.add(name, device);
                }
            }
        }
        protected Device newDevice(String name)
        {
            return new Device(name);
        }
        

    }
    private class LightAction extends Action<Light>
    {
        
        public LightAction(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected Light newDevice(String name)
        {
            return new Light(name);
        }
        
        
    }
    private class Dim extends LightAction
    {
        protected int dim;
        public Dim(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        @Override
        protected void event(boolean act)
        {
            if (act)
            {
                device.dim(dim);
            }
            else
            {
                device.dim(100);
            }
        }

    }
    private class On extends Action<Device>
    {
        public On(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        @Override
        protected void event(boolean act)
        {
            super.event(true);
        }
    }
    private class Off extends Action<Device>
    {
        public Off(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        @Override
        protected void event(boolean act)
        {
            super.event(false);
        }
    }
    private class Mdns extends Node
    {
        protected String dn;
        protected List<Action> actions = new ArrayList<>();
        private boolean on;
        public Mdns(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void handle(String name, int ttl)
        {
            if (dn.equals(name))
            {
                boolean act = ttl > 0;
                if (act != on)
                {
                    for (Action action : actions)
                    {
                        action.event(act);
                    }
                    on = act;
                }
            }
        }

    }
}
