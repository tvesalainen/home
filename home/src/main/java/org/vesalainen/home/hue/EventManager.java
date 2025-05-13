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
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.logging.Level;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.json.XML;
import org.vesalainen.home.hue.Resources.Resource;
import org.vesalainen.math.LocalTimeCubicSpline;
import org.vesalainen.util.concurrent.CachedScheduledThreadPool;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class EventManager extends JavaLogging
{
    private static final JSONPointer RID = new JSONPointer("/owner/rid");
    private static JSONPointer MOTION = new JSONPointer("/motion/motion");
    private static JSONPointer GROUPED_MOTION = new JSONPointer("/motion/motion_report/motion");
    private static JSONObject ON = JSON.build("/on/on", true).get();
    private static JSONObject OFF = JSON.build("/on/on", false).get();
    private Hue hue;
    private String start;
    private WatchService watchService;
    private Path path;
    private CachedScheduledThreadPool pool = new CachedScheduledThreadPool();
    private HueManager hueManager;
    private LocalTimeCubicSpline mirek;
    private LocalTimeCubicSpline brightness;

    public EventManager(Path path) throws IOException
    {
        super(EventManager.class);
        this.path = path;
        FileSystem fileSystem = path.getFileSystem();
        this.watchService = fileSystem.newWatchService();
        path.getParent().register(watchService, ENTRY_MODIFY);
        this.brightness = LocalTimeCubicSpline.builder()
                .add(LocalTime.of(3, 0), 10)
                .add(LocalTime.of(6, 0), 30)
                .add(LocalTime.of(9, 0), 70)
                .add(LocalTime.of(12, 0), 80)
                .add(LocalTime.of(15, 0), 70)
                .add(LocalTime.of(18, 0), 30)
                .add(LocalTime.of(21, 0), 10)
                .add(LocalTime.of(0, 0), 10)
                .build();        
        this.mirek = LocalTimeCubicSpline.builder()
                .add(LocalTime.of(3, 0), 490)
                .add(LocalTime.of(6, 0), 400)
                .add(LocalTime.of(9, 0), 300)
                .add(LocalTime.of(12, 0), 153)
                .add(LocalTime.of(15, 0), 300)
                .add(LocalTime.of(18, 0), 400)
                .add(LocalTime.of(21, 0), 490)
                .add(LocalTime.of(0, 0), 490)
                .build();        
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
                logEvent(jo);
                Resource res = hue.getResource((String) RID.queryFrom(o));
                String type = jo.getString("type");
                hueManager.event(res, type, jo);
            }
            catch (JSONPointerException ex)
            {
                log(SEVERE, ex, "event: %s", ja);
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

    private void refresh() throws IOException
    {
        WatchKey wk = watchService.poll();
        if (wk != null)
        {
            info("refreshing config");
            loadConfig();
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
        double d = mirek.applyAsDouble(LocalTime.now());
        return (max(153,min(500, (int) d)));
    }
    private int getBrightness()
    {
        double d = brightness.applyAsDouble(LocalTime.now());
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
            Class<?> cls = value.getClass();
            while (cls != null)
            {
                String name = cls.getSimpleName();
                name = name.toLowerCase();
                try
                {
                    Field field = node.getClass().getDeclaredField(name+"s");
                    List<Node> list = (List) field.get(this);
                    list.add(value);
                    return;
                }
                catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex)
                {
                }
                cls = (Class<?>) cls.getSuperclass();
            }
            throw new NoSuchFieldException(value+" not found");
        }
        
    }
    private class HueManager extends Node
    {
        protected Motions motions;
        public HueManager(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(Resource res, String type, JSONObject jo)
        {
            switch (type)
            {
                case "grouped_light":
                    break;
                case "light":
                    break;
                case "scene":
                    break;
                case "smart_scene":
                    break;
                case "temperature":
                    break;
                case "grouped_light_level":
                    break;
                case "light_level":
                    break;
                case "motion":
                case "grouped_motion":
                    motions.event(res, jo);
                    break;
                default:
                    break;
            }
        }
        
    }
    private class Motions extends Node
    {
        protected Enter enter;
        protected List<Motion> motions = new ArrayList<>();
        private Map<String,Motion> motionMap = new HashMap<>();
        private Motion lastOn;
        private long lastTime;
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

        private void event(Resource res, JSONObject jo)
        {
            String name = res.getName();
            Motion motion = motionMap.get(name);
            if (motion != null)
            {
                long now = System.currentTimeMillis();
                boolean act = getMotion(jo);
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
                    lastOn = motion;
                    lastTime = now;
                }
                else
                {
                    long lo = now - 10000;
                    if (lo > lastTime)
                    {
                        lastOn = motion;
                        lastTime = lo;
                    }
                    if (onCount == 1 && lastOn != null)
                    {
                        lastOn.exit(act);
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

        private void exit(boolean act)
        {
            info("exit %s", name);
            if (exit != null)
            {
                exit.event(act);
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
        protected Boolean action;
        protected long delay;
        protected String type;
        private ScheduledFuture<?> future;
        private Collection<Resource> on;
        public Device(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        private void event(boolean act)
        {
            info("device %s", act);
            if (action == null)
            {
                if (act)
                {
                    on();
                }
                else
                {
                    off();
                }
            }
            else
            {
                if (action)
                {
                    on();
                }
                else
                {
                    off();
                }
            }
        }

        @Override
        protected boolean assign(String name, Object value)
        {
            switch (name)
            {
                case "action":
                    action = (Boolean) value;
                    return true;
                default:
                    return false;
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
            if (cancel())
            {
                hue.update(on, ON);
            }
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
        public Light(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void on()
        {
            hue.update(temperature, "/color_temperature/mirek:"+getMirek());
            hue.update(brightness, "/dimming/brightness:"+getBrightness());
            super.on();
        }

        @Override
        protected void init()
        {
            super.init();
            brightness = hue.getResource(name, "/dimming/brightness:80");
            temperature = hue.getResource(name, "/color_temperature/mirek:80");
        }
        
    }
}
