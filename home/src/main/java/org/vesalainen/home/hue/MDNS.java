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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import static java.net.StandardSocketOptions.IP_MULTICAST_LOOP;
import static java.net.StandardSocketOptions.SO_BROADCAST;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import java.nio.channels.Selector;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.vesalainen.net.dns.Message;
import org.vesalainen.net.dns.Question;
import org.vesalainen.net.dns.RCodeException;
import org.vesalainen.net.dns.ResourceRecord;
import org.vesalainen.net.dns.TruncatedException;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class MDNS
{
    private Thread thread;
    private Map<Question,ResourceRecord> map = new HashMap<>();
    public void startListening()
    {
        listenAndWait(this::handle);
    }
    private void handle(Message message)
    {
        if (message.isAuthoritative() && !message.isQuery())
        {
            ResourceRecord[] answers = message.getAnswers();
            if (answers != null)
            {
                for (ResourceRecord rr : answers)
                {
                    handle(rr);
                }
            }
        }
    }
    private void handle(ResourceRecord rr)
    {
        Question question = rr.getQuestion();
        ResourceRecord old = map.put(question, rr);
        System.err.println(question+" ttl="+rr.getTtl());
    }
    public void startListening(Consumer<Message> act)
    {
        thread = new Thread(()->listenAndWait(act));
        thread.start();
    }
    public void stopListening()
    {
        if (thread != null)
        {
            thread.interrupt();
            thread = null;
        }
    }
    public static void listenAndWait(Consumer<Message> act)
    {
        find((m)->{act.accept(m);return false;});
    }
    public static Message find(Predicate<Message> test)
    {
        InetSocketAddress group4 = new InetSocketAddress("224.0.0.251", 5353);
        InetSocketAddress group6 = new InetSocketAddress("ff02::fb", 5353);
        try (
                DatagramChannel m4 = DatagramChannel.open(StandardProtocolFamily.INET);
                DatagramChannel m6 = DatagramChannel.open(StandardProtocolFamily.INET);
                Selector s = Selector.open();
                )
        {
            m4.configureBlocking(false);
            m4.register(s, OP_READ);
            m4.setOption(SO_BROADCAST, true);
            m4.setOption(SO_REUSEADDR, true);
            m4.setOption(IP_MULTICAST_LOOP, false);
            m4.bind(new InetSocketAddress(5353));
            m6.configureBlocking(false);
            m6.register(s, OP_READ);
            m6.setOption(SO_BROADCAST, true);
            m6.setOption(SO_REUSEADDR, true);
            m6.setOption(IP_MULTICAST_LOOP, false);
            m6.bind(new InetSocketAddress(5353));
            InetAddress ia4 = group4.getAddress();
            InetAddress ia6 = group6.getAddress();
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements())
            {
                NetworkInterface ni = nis.nextElement();
                if (ni.supportsMulticast() && ni.isUp())
                {
                    m4.join(ia4, ni);
                    m6.join(ia4, ni);
                }
            }
            ByteBuffer bb = ByteBuffer.allocate(8192);
            while (true)
            {
                int sc = s.select();
                for (SelectionKey k : s.selectedKeys())
                {
                    DatagramChannel dc = (DatagramChannel) k.channel();
                    bb.clear();
                    SocketAddress peer = dc.receive(bb);
                    if (peer != null)
                    {
                        try
                        {
                            Message msg = new Message(bb.array(), 0, bb.position());
                            if (test.test(msg))
                            {
                                return msg;
                            }
                        }
                        catch (RCodeException | TruncatedException ex)
                        {
                            Logger.getLogger(MDNS.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

}
