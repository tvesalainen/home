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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.Function;
import java.util.function.Predicate;
import static java.util.logging.Level.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.json.XML;
import org.vesalainen.home.hue.Resources.Resource;
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
    private Hue hue;
    private String start;
    private WatchService watchService;
    private Path path;
    private CachedScheduledThreadPool pool = new CachedScheduledThreadPool();
    private HueConfig hueConfig;

    public EventManager(Path path) throws IOException
    {
        super(EventManager.class);
        this.path = path;
        loadConfig();
        FileSystem fileSystem = path.getFileSystem();
        this.watchService = fileSystem.newWatchService();
        path.getParent().register(watchService, ENTRY_MODIFY);
        this.hue = new Hue("testApp");
    }
    public void start() throws IOException
    {
        hue.readAllResources();
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
                        break;
                    case "grouped_motion":
                        break;
                    default:
                        break;
                }
            }
            catch (JSONPointerException ex)
            {
                //log(INFO, ex, "event:%s", ja);
            }
        }
    }
    private boolean addNode(String name, Object json)
    {
        if (name.equals("hue"))
        {
            config("add node %s", name);
            hueConfig = new HueConfig((JSONObject) json, null);
            JSON.walk(json, hueConfig::populate);
            hueConfig.init();
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
                try
                {
                    Field field = this.getClass().getDeclaredField(name+"s");
                    List<Node> list = (List) field.get(this);
                    list.add(node);
                }
                catch (NoSuchFieldException ex)
                {
                }
                JSON.walk(json, node::populate);
                node.init();
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex)
            {
                try
                {
                    if (!assign(name, json))
                    {
                        Field field = this.getClass().getDeclaredField(name);
                        field.set(this, json);
                    }
                }
                catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex1)
                {
                    throw new RuntimeException(ex);
                }
            }
            return true;
        }
        protected void init(){}
        protected boolean assign(String name, Object value){return false;}
        
    }
    private class HueConfig extends Node
    {
        
        public HueConfig(JSONObject json, Node parent)
        {
            super(json, parent);
        }
        
    }
}
