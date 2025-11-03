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
package org.vesalainen.home.fmi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.vesalainen.home.IndexedData;
import org.vesalainen.home.Restarter;
import org.vesalainen.util.logging.AttachedLogger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class OpenData extends DefaultHandler implements AttachedLogger
{
    private final Deque<StringBuilder> stack = new ArrayDeque<>();
    private final List<String> fields = new ArrayList<>();
    private ZonedDateTime begin;
    private ZonedDateTime end;
    private double[] arr;
    private final IndexedData dat;
    private final Restarter restarter;
    private final ScheduledExecutorService executor;
    private final String place;
    private final Duration duration;
    private final String parameters;
    private final Duration timestep;

    public OpenData(ScheduledExecutorService executor, String place, IndexedData data)
    {
        this(executor, place, "Pressure,Temperature,Dewpoint,Humidity", data, Duration.ofHours(12), 5, TimeUnit.MINUTES, 10, Duration.ofHours(6));
    }
    public OpenData(
            ScheduledExecutorService executor, 
            String place,
            String parameters,
            IndexedData data,
            Duration duration, 
            long restartDelay,
            TimeUnit restartDelayUnit,
            int restartCount,
            Duration advance)
    {
        this.executor = executor;
        this.place = place;
        this.parameters = parameters;
        this.timestep = Duration.ofSeconds(data.getSeconds());
        this.duration = duration;
        this.restarter = new Restarter(executor, restartDelay, restartDelayUnit, restartCount, advance);
        this.dat = data;
    }
    
    public void startReading()
    {
        restarter.execute(()->readForecast());
    }
    public void startReadingAndWait()
    {
        restarter.executeAndWait(()->readForecast());
    }
    public ZonedDateTime readForecast()
    {
        ZonedDateTime start = dat.currentPeriod().withZoneSameInstant(ZoneId.of("Z")).truncatedTo(HOURS); //ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Z")).truncatedTo(HOURS);
        ZonedDateTime end = start.plus(duration);
        fine("read forecast %s - %s", start, end);
        return readForecast(
                place, 
                start.toString(), 
                end.toString());
    }
    public ZonedDateTime readForecast(
            String place, 
            String starttime,
            String endtime)
    {
        return read("https://opendata.fmi.fi/wfs?"
                + "service=WFS&version=2.0.0&"
                + "request=getFeature&storedquery_id=fmi::forecast::harmonie::surface::point::multipointcoverage&"
                + "place="+place+"&"
                + "parameters="+parameters+"&"
                + "timestep="+timestep.getSeconds()/60+"&"
                + "starttime="+starttime+"&"
                + "endtime="+endtime+"&"
        );
    }
    private ZonedDateTime read(String urlString)
    {
        try
        {
            stack.clear();
            fields.clear();
            URL url = new URL(urlString);
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("accept", "text/xml");
            urlConnection.connect();
            int rc = urlConnection.getResponseCode();
            if (rc >= 200 && rc < 300)
            {
                try (InputStream is = urlConnection.getInputStream();
                        )
                {
                    finest("start parsing rc=%d", rc);
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser parser = factory.newSAXParser();
                    parser.parse(is, this);
                }
                catch (SAXException | ParserConfigurationException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
            else
            {
                try (InputStream is = urlConnection.getErrorStream();
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr))
                {
                    StringBuilder sb = new StringBuilder();
                    String line = br.readLine();
                    while (line != null)
                    {
                        sb.append(line);
                        line = br.readLine();
                    }
                    throw new RuntimeException("rc = "+rc+" "+sb.toString());
                }
            }
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
        int count = arr.length/fields.size();
        finest("set %d %s", count, timestep);
        int index = 0;
        for (int ii=0;ii<count;ii++)
        {
            for (String field : fields)
            {
                double v = arr[index++];
                fine("set %s: %s = %f", begin, field, v);
                dat.set(begin, field, v);
            }
            begin = begin.plus(timestep);
        }
        return begin;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException
    {
        stack.peek().append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException
    {
        String text = stack.pop().toString().trim();
        switch (qName)
        {
            case "gml:beginPosition":
                begin = ZonedDateTime.parse(text).withZoneSameInstant(ZoneId.systemDefault());
                break;
            case "gml:endPosition":
                end = ZonedDateTime.parse(text).withZoneSameInstant(ZoneId.systemDefault());
                break;
            case "gml:doubleOrNilReasonTupleList":
                String[] split = text.split("[ \n\r]+");
                int len = split.length;
                arr = new double[len];
                for (int ii=0;ii<len;ii++)
                {
                    String v = split[ii];
                    arr[ii] = Double.parseDouble(v);
                }
                break;
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
        stack.push(new StringBuilder());
        switch (qName)
        {
            case "swe:field":
                fields.add(attributes.getValue("name"));
                break;
        }
    }
    
}
