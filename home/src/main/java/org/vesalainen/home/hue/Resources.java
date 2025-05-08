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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.vesalainen.util.HashMapList;
import org.vesalainen.util.MapList;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Resources
{
    private static final JSONPointer NAME = new JSONPointer("/metadata/name");
    private static final JSONPointer OWNER = new JSONPointer("/owner/rid");
    private Map<String,Resource> resourceMap = new HashMap<>();
    private MapList<String,Resource> nameMap = new HashMapList<>();

    public Resources(JSONArray jsonArray)
    {
        jsonArray.forEach((Object o) ->
        {
            JSONObject jo = (JSONObject) o;
            Resource res = new Resource(jo);
            resourceMap.put(res.getId(), res);
            String name = res.getName();
            if (name != null)
            {
                nameMap.add(name.toLowerCase(), res);
            }
        });
    }
    
    public List<Resource> getResources(String name)
    {
        return nameMap.get(name.toLowerCase());
    }
    public Resource getResource(String id)
    {
        return resourceMap.get(id);
    }
    public class Resource
    {
        private JSONObject json;
        private Set<String> keySet;
        private Set<String> valueSet;

        public Resource(JSONObject json)
        {
            this.json = json;
        }
        
        public String getName()
        {
            return query(NAME);
        }
        
        public String getId()
        {
            return json.optString("id", null);
        }
        
        public String getType()
        {
            return json.optString("type", null);
        }
        
        public String get(String key)
        {
            Object ob = JSON.get(json, key);
            if (ob != null)
            {
                return ob.toString();
            }
            else
            {
                return null;
            }
        }

        public Resource getOwner()
        {
            String rid = query(OWNER);
            if (rid != null)
            {
                return resourceMap.get(rid);
            }
            return null;
        }
        public Iterable<Resource> childrens()
        {
            return iterable("children");
        }
        public Iterable<Resource> services()
        {
            return iterable("services");
        }
        private ArrayIterator iterable(String name)
        {
            JSONArray array = json.optJSONArray(name);
            return new ArrayIterator(array);
        }
        public Set<String> keySet()
        {
            if (keySet == null)
            {
                keySet = JSON.keySet(json);
            }
            return keySet;
        }

        public Set<String> valueSet()
        {
            if (valueSet == null)
            {
                valueSet = JSON.valueSet(json);
            }
            return valueSet;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.getId());
            return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            final Resource other = (Resource) obj;
            if (!Objects.equals(this.getId(), other.getId()))
            {
                return false;
            }
            return true;
        }
        
        @Override
        public String toString()
        {
            return "Resource{" + getName()+ ":" + getType() + '}';
        }

        private String query(JSONPointer ptr)
        {
            try
            {
                return (String) ptr.queryFrom(json);
            }
            catch (JSONPointerException ex)
            {
                return null;
            }
        }
        
    }
    private class ArrayIterator implements Iterator<Resource>, Iterable<Resource>
    {
        private JSONArray array;
        private int index;

        public ArrayIterator(JSONArray array)
        {
            this.array = array;
        }
        
        @Override
        public boolean hasNext()
        {
            if (array != null)
            {
                return index < array.length();
            }
            return false;
        }

        @Override
        public Resource next()
        {
            JSONObject jo = array.getJSONObject(index++);
            String rid = jo.getString("rid");
            return resourceMap.get(rid);
        }

        @Override
        public Iterator<Resource> iterator()
        {
            return this;
        }
        
    }
}
