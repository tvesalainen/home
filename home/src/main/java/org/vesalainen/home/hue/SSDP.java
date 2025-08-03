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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import static java.net.StandardSocketOptions.IP_MULTICAST_LOOP;
import static java.net.StandardSocketOptions.SO_BROADCAST;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class SSDP
{
    public static Map<String, Object> observeRootDevices(Consumer<Map<String,Object>> res) throws IOException
    {
        return searchRootDevice(4, (m)->{res.accept(m);return false;});
    }
    public static Map<String, Object> searchRootDevice(Predicate<Map<String,Object>> test) throws IOException
    {
        return searcDevice("upnp:rootdevice", 4, test);
    }
    public static Map<String, Object> observeRootDevices(int mx, Consumer<Map<String,Object>> res) throws IOException
    {
        return searcDevice("upnp:rootdevice", mx, (m)->{res.accept(m);return false;});
    }
    public static Map<String, Object> searchRootDevice(int mx, Predicate<Map<String,Object>> test) throws IOException
    {
        return searcDevice("upnp:rootdevice", mx, test);
    }
    public static Map<String, Object> observeDevices(String st, int mx, Consumer<Map<String,Object>> res) throws IOException
    {
        return searcDevice(st, mx, (m)->{res.accept(m);return false;});
    }
    public static Map<String, Object> searcDevice(String st, int mx, Predicate<Map<String,Object>> test) throws IOException
    {   // TO DO ipv6
        InetSocketAddress group = new InetSocketAddress("239.255.255.250", 1900);
        try (
                DatagramChannel c = DatagramChannel.open(StandardProtocolFamily.INET);
                DatagramChannel m = DatagramChannel.open(StandardProtocolFamily.INET);
                Selector s = Selector.open();
                )
        {
            c.configureBlocking(false);
            m.configureBlocking(false);
            c.register(s, OP_READ);
            m.register(s, OP_READ);
            c.setOption(SO_BROADCAST, true);
            c.setOption(SO_REUSEADDR, true);
            m.setOption(SO_BROADCAST, true);
            m.setOption(SO_REUSEADDR, true);
            m.setOption(IP_MULTICAST_LOOP, false);
            c.bind(null);
            m.bind(new InetSocketAddress(1900));
            InetAddress ia = group.getAddress();
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements())
            {
                NetworkInterface ni = nis.nextElement();
                if (ni.supportsMulticast() && ni.isUp())
                {
                    m.join(ia, ni);
                }
            }
            String search =
                    "M-SEARCH * HTTP/1.1\r\n"+
                    "HOST: 239.255.255.250:1900\r\n"+
                    "MAN: ssdp:discover\r\n"+
                    "MX: "+mx+"\r\n"+
                    "ST: "+st+"\r\n"+
                    "USER-AGENT:"+System.getProperty("os.name")+":"+System.getProperty("os.version");
            ByteBuffer src = ByteBuffer.wrap(search.getBytes(US_ASCII));
            c.send(src, group);
            ByteBuffer bb = ByteBuffer.allocate(1024);
            while (true)
            {
                int sc = s.select();
                for (SelectionKey k : s.selectedKeys())
                {
                    DatagramChannel dc = (DatagramChannel) k.channel();
                    bb.clear();
                    SocketAddress peer = dc.receive(bb);
                    String msg = new String(bb.array(), 0, bb.position());
                    String[] flds = msg.split("\r\n");
                    Map<String,Object> map = new HashMap<>();
                    map.put("", flds[0]);
                    map.put("PEER", peer);
                    for (int ii=1;ii<flds.length;ii++)
                    {
                        String fld = flds[ii];
                        int idx = fld.indexOf(':');
                        if (idx != -1)
                        {
                            String n = fld.substring(0, idx);
                            String v = fld.substring(idx+1).trim();
                            switch (n)
                            {
                                case "LOCATION":
                                    map.put(n, new URL(v));
                                    break;
                                case "NTS":
                                case "USN":
                                    map.put(n, URI.create(v));
                                default:
                                    map.put(n, v);
                                    break;
                            }
                        }
                    }
                    if (test.test(map))
                    {
                        return map;
                    }
                }
            }
        }
    }

}
