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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class JSON
{
    public static final String DELIM = "/";
    
    public static void walk(Object obj, BiPredicate<String,Object> act)
    {
        if (obj instanceof JSONObject)
        {
            JSONObject jo = (JSONObject) obj;
            for (String key : jo.keySet())
            {
                Object o = jo.get(key);
                if (o instanceof JSONArray)
                {
                    JSONArray ja = (JSONArray) o;
                    ja.forEach((oo)->
                    {
                        if (!act.test(key, oo))
                        {
                            walk(oo, act);
                        }
                    });
                }
                else
                {
                    if (!act.test(key, o))
                    {
                        walk(o, act);
                    }
                }
            }
        }
        else
        {
            if (obj instanceof JSONArray)
            {
                JSONArray ja = (JSONArray) obj;
                ja.forEach((o)->walk(o, act));
            }
        }
    }
    public static Object get(Object ob, String key)
    {
        JSONPointer jp = new JSONPointer(key);
        try
        {
            return jp.queryFrom(ob);
        }
        catch (JSONPointerException ex)
        {
            return null;
        }
    }
    public static Set<String> keySet(Object ob)
    {
        Set<String> set = new HashSet<>();
        keySet(set, "", ob, false);
        return set;
    }
    public static Set<String> valueSet(Object ob)
    {
        Set<String> set = new HashSet<>();
        keySet(set, "", ob, true);
        return set;
    }
    private static void keySet(Set<String> set, String prefix, Object target, boolean value)
    {
        if (target instanceof JSONObject)
        {
            JSONObject jo = (JSONObject) target;
            for (String key : jo.keySet())
            {
                keySet(set, prefix+DELIM+key, jo.get(key), value);
            }
        }
        else
        {
            if (target instanceof JSONArray)
            {
                JSONArray ja = (JSONArray) target;
                int len = ja.length();
                for (int ii=0;ii<len;ii++)
                {
                    keySet(set, prefix+DELIM+ii, ja.get(ii), value);
                }
            }
            else
            {
                if (value)
                {
                    set.add(prefix+":"+target);
                }
                else
                {
                    set.add(prefix);
                }
            }
        }
    }
    public static void dump(Object ob)
    {
        dump("", ob);
    }
    private static void dump(String prefix, Object target)
    {
        if (target instanceof JSONObject)
        {
            JSONObject jo = (JSONObject) target;
            for (String key : jo.keySet())
            {
                dump(prefix+DELIM+key, jo.get(key));
            }
        }
        else
        {
            if (target instanceof JSONArray)
            {
                JSONArray ja = (JSONArray) target;
                int len = ja.length();
                for (int ii=0;ii<len;ii++)
                {
                    dump(prefix+DELIM+ii, ja.get(ii));
                }
            }
            else
            {
                System.err.println(prefix+":"+target);
            }
        }
    }
    public static Builder builder()
    {
        Builder builder = new Builder();
        return builder;
    }
    public static Builder build(String key, Object value)
    {
        Builder builder = new Builder();
        return builder.set(key, value);
    }

    static JSONObject build(String... upd)
    {
        return build(Arrays.asList(upd));
    }
    static JSONObject build(Collection<String> upd)
    {
        JSON.Builder builder = JSON.builder();
        for (String u : upd)
        {
            int li = u.lastIndexOf(':');
            if (li != -1)
            {
                builder.set(u.substring(0, li), JSONObject.stringToValue(u.substring(li+1)));
            }
            else
            {
                throw new IllegalArgumentException(": missing in "+u);
            }
        }
        return builder.get();
    }
    public static class Builder
    {
        private JSONObject obj = new JSONObject();

        public Builder set(String key, Object value)
        {
            return set(obj, value, key.substring(1).split(DELIM));
        }
        private Builder set(Object target, Object value, String... keys)
        {
            if (keys.length > 0)
            {
                String n = keys[0];
                if (target instanceof JSONObject)
                {
                    JSONObject jo = (JSONObject) target;
                    if (jo.isNull(n))
                    {
                        Object nt = createTarget(target, value, keys);
                        jo.put(n, nt);
                        return set(nt, value, Arrays.copyOfRange(keys, 1, keys.length));
                    }
                    else
                    {
                        Object nt = jo.get(n);
                        return set(nt, value, Arrays.copyOfRange(keys, 1, keys.length));
                    }
                }
                else
                {
                    int idx = Integer.parseInt(n);
                    if (target instanceof JSONArray)
                    {
                        JSONArray ja = (JSONArray) target;
                        if (ja.isNull(idx))
                        {
                            Object nt = createTarget(target, value, keys);
                            ja.put(nt);
                            return set(nt, value, Arrays.copyOfRange(keys, 1, keys.length));
                        }
                        else
                        {
                            Object nt = ja.get(idx);
                            return set(nt, value, Arrays.copyOfRange(keys, 1, keys.length));
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException(target+" illegal");
                    }
                }
            }
            return this;
        }
        public JSONObject get()
        {
            return obj;
        }

        private Object createTarget(Object target, Object value, String... keys)
        {
            if (keys.length > 1)
            {
                if (isArray(keys[1]))
                {
                    return new JSONArray();
                }
                else
                {
                    return new JSONObject();
                }
            }
            else
            {
                return value;
            }
        }

        private boolean isArray(String key)
        {
            try
            {
                Integer.parseInt(key);
                return true;
            }
            catch (NumberFormatException ex)
            {
                return false;
            }
        }
    }
}
