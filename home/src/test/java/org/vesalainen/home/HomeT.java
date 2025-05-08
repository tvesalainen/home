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
package org.vesalainen.home;

import io.resourcepool.ssdp.client.SsdpClient;
import io.resourcepool.ssdp.model.DiscoveryListener;
import io.resourcepool.ssdp.model.DiscoveryRequest;
import io.resourcepool.ssdp.model.SsdpRequest;
import io.resourcepool.ssdp.model.SsdpService;
import io.resourcepool.ssdp.model.SsdpServiceAnnouncement;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.vesalainen.home.hue.Hue;
import org.vesalainen.home.hue.JSON;
import org.vesalainen.home.hue.Resources.Resource;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class HomeT
{

    public HomeT()
    {
    }

    //@Test
    private void test0() throws IOException, InterruptedException
    {
        SsdpClient client = SsdpClient.create();
        DiscoveryRequest all = SsdpRequest.discoverAll();
        client.discoverServices(all, new DiscoveryListener()
        {
            @Override
            public void onServiceDiscovered(SsdpService service)
            {
                System.out.println("Found service: " + service);
            }

            @Override
            public void onServiceAnnouncement(SsdpServiceAnnouncement announcement)
            {
                System.out.println("Service announced something: " + announcement);
            }

            @Override
            public void onFailed(Exception excptn)
            {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        });
        Thread.sleep(10000);
    }

    @Test
    public void test1() throws IOException
    {
        Home home = new Home("testApp");
        Hue hue = home.getHue();
        hue.readAllResources();
        List<Resource> rss = hue.getResources("library");
        Resource res = rss.get(0);
        System.err.println(res.getOwner());
        for (Resource r : res.services())
        {
            System.err.println(r);
            Resource owner = r.getOwner();
            assertEquals(res, owner);
        }
        for (Resource r : res.childrens())
        {
            System.err.println(r);
            Resource owner = r.getOwner();
        }
        //hue.update("Hue color spot 1", "/color/xy/x:0.2", "/color/xy/y:0.4", "/on/on:true");
        //hue.events((r) -> {JSON.dump(r);System.err.println("----------------");});
    }

}
