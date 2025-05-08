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
public class StateManager extends JavaLogging
{
    private static final JSONPointer RID = new JSONPointer("/owner/rid");
    private static JSONPointer MOTION = new JSONPointer("/motion/motion");
    private static JSONPointer GROUPED_MOTION = new JSONPointer("/motion/motion_report/motion");
    private Hue hue;
    private Map<String,State> stateMap = new HashMap<>();
    private String start;
    private Deque<State> stack = new ArrayDeque<>();
    private WatchService watchService;
    private Path path;
    private CachedScheduledThreadPool pool = new CachedScheduledThreadPool();

    public StateManager(Path path) throws IOException
    {
        super(StateManager.class);
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
        pushState(start);
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
                logEvent((JSONObject) o);
                Resource res = hue.getResource((String) RID.queryFrom(o));
                current().event((JSONObject) o, res);
            }
            catch (IOException ex)
            {
                log(INFO, ex, "event:%s", ja);
            }
            catch (JSONPointerException ex)
            {
                //log(INFO, ex, "event:%s", ja);
            }
        }
    }
    private boolean addState(String name, Object json)
    {
        if (name.equals("state"))
        {
            config("addinf state %s", name);
            State state = new State((JSONObject) json, null);
            JSON.walk(json, state::populate);
            state.init();
            if (start == null)
            {
                start = state.getName();
            }
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
            stateMap.clear();
            start = null;
            loadConfig();
        }
    }
    private void loadConfig() throws IOException
    {
        info("loading config");
        try (BufferedReader br = Files.newBufferedReader(path))
        {
            JSONObject jo = XML.toJSONObject(br);
            JSON.walk(jo, this::addState);
        }
    }

    private void pushState(String name)
    {
        info("push state %s", name);
        State state = stateMap.get(name);
        if (!stack.isEmpty())
        {
            stack.peek().exit();
        }
        stack.push(state);
        state.enter();
    }
    private void popState()
    {
        info("pop state %s", stack.peek());
        State state = stack.pop();
        state.exit();
        stack.peek().enter();
    }
    private State current()
    {
        return stack.peek();
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
            String pck = StateManager.class.getName();
            try
            {
                String s = pck+"$"+name.substring(0, 1).toUpperCase()+name.substring(1);
                Class<?> cls = Class.forName(s);
                Constructor<?> cons = cls.getDeclaredConstructor(StateManager.class, JSONObject.class, Node.class);
                Node node = (Node) cons.newInstance(StateManager.this, json, this);
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
    protected class State extends Node
    {
        protected List<Event> events = new ArrayList<>();
        protected List<Update> updates = new ArrayList<>();
        protected String name;
        private Map<String,JSONObject> safe = new HashMap<>();
        public State(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            State old = stateMap.put(name, this);
            if (old != null)
            {
                throw new RuntimeException(old+" exists already!");
            }
        }
        
        public String getName()
        {
            return name;
        }

        public State getParentState()
        {
            return getParent(State.class);
        }
        
        @Override
        public String toString()
        {
            return "State{" + name + '}';
        }

        private void event(JSONObject jo, Resource res) throws IOException
        {
            if (parent != null)
            {
                State p = (State) parent;
                p.event(jo, res);
            }
            for (Event ev : events)
            {
                ev.event(jo, res);
            }
        }
        public void enter()
        {
            info("enter state %s", name);
            JSONArray res = hue.getAllResources();
            res.forEach((Object j)->
            {
                JSONObject jo = (JSONObject) j;
                String id = jo.getString("id");
                safe.put(id, jo);
            });
            for (Update upd : updates)
            {
                upd.update();
            }
        }
        public void exit()
        {
            info("exit state %s", name);
            for (Event ev : events)
            {
                ev.cancel();
            }
            safe.clear();
        }

    }
    private class Event extends Node
    {
        protected String name;
        protected List<On> ons = new ArrayList<>();
        protected ScheduledFuture<?> future;
        
        public Event(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        protected void event(JSONObject jo, Resource res)
        {
            if (name == null || name.equalsIgnoreCase(res.getName()))
            {
                cancel();
                for (On on : ons)
                {
                    on.event(jo, res);
                }
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
                future = pool.schedule(act, delay, SECONDS);
            }
        }
        protected void cancel()
        {
            for (On on : ons)
            {
                on.cancel();
            }
            if (future != null)
            {
                future.cancel(true);
                future = null;
            }
        }
        
    }
    private class OnOff extends Event
    {
        protected String name;
        protected long delay;
        protected String motion;
        private JSONObject on = JSON.build("/on/on", true).get();
        private JSONObject off = JSON.build("/on/on", false).get();
        public OnOff(JSONObject json, Node parent)
        {
            super(json, parent);
            if (parent instanceof State)
            {
                State p = (State) parent;
                p.events.add(this);
            }
        }
        @Override
        protected void event(JSONObject jo, Resource res)
        {
            if (hasMotion(jo))
            {
                if (motion.equalsIgnoreCase(res.getName()))
                {
                    cancel();
                    boolean m = getMotion(jo);
                    if (m)
                    {
                        hue.update(name, on);
                    }
                    else
                    {
                        schedule(()->hue.update(name, off), delay);
                    }
                }
            }
        }

    }
    private class ExitPoint extends Event
    {
        protected String name;
        protected long delay;
        protected boolean on;
        protected Runnable action = ()->{};
        public ExitPoint(JSONObject json, Node parent)
        {
            super(json, parent);
            if (parent instanceof State)
            {
                State p = (State) parent;
                p.events.add(this);
            }
        }
        @Override
        protected boolean assign(String name, Object value)
        {
            switch (name)
            {
                case "enter":
                    this.action = ()->{pushState((String) value);};
                    return true;
                default:
                    return false;
            }
        }

        @Override
        protected void event(JSONObject jo, Resource res)
        {
            if (hasMotion(jo))
            {
                boolean m = getMotion(jo);
                if (name.equalsIgnoreCase(res.getName()))
                {
                    if (on)
                    {
                        if (!m)
                        {
                            schedule(action, delay);
                            on = false;
                        }
                    }
                    else
                    {
                        if (m)
                        {
                            on = true;
                        }
                    }
                }
                else
                {
                    if (m)
                    {
                        cancel();
                        on = false;
                    }
                }
            }
        }

    }
    private class On extends Node
    {
        protected List<Update> updates = new ArrayList<>();
        protected JSONPointer key;
        protected Object value;
        protected Predicate predicate;
        protected Runnable action = ()->{};
        protected long delay;
        
        public On(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected boolean assign(String name, Object value)
        {
            switch (name)
            {
                case "eq":
                    this.predicate = (o)->{return value.equals(o);};
                    return true;
                case "key":
                    this.key = new JSONPointer((String) value);
                    return true;
                case "enter":
                    this.action = ()->{pushState((String) value);};
                    return true;
                case "action":
                    switch ((String)value)
                    {
                        case "exit":
                        this.action = ()->{popState();};
                        break;
                    }
                    return true;
                default:
                    return false;
            }
        }

        
        private void event(JSONObject jo, Resource res)
        {
            try
            {
                Object ob = key.queryFrom(jo);
                if (predicate.test(ob))
                {
                    for (Update upd : updates)
                    {
                        upd.update();
                    }
                    action.run();
                }
            }
            catch (JSONPointerException ex)
            {
                return;
            }
        }

        private void cancel()
        {
        }
        
    }
    private class Update extends Node
    {
        protected String name;
        protected String has;
        protected String content;
        protected Function<Set<String>,JSONObject> upd;
        protected Predicate<Set<String>> predicate;
        
        public Update(JSONObject json, Node parent)
        {
            super(json, parent);
        }

        @Override
        protected void init()
        {
            JSONObject data = JSON.build(content.split("[ \r\n]+"));
            if (has != null)
            {
                Set<String> valueSet = JSON.valueSet(data);
                ArrayMerger am = new ArrayMerger(has);
                upd = (s)->JSON.build(am.merge(s, valueSet));
                predicate = (s)->am.matches(s);
            }
            else
            {
                Set<String> keySet = JSON.keySet(data);
                upd = (s)->data;
                predicate = (s)->s.containsAll(keySet);
            }
        }

        private void update()
        {
            hue.update(name, upd, predicate);
        }
        
    }
}
