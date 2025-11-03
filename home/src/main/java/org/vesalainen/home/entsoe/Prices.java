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
package org.vesalainen.home.entsoe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.vesalainen.util.logging.AttachedLogger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Prices extends DefaultHandler implements AttachedLogger
{
    private final String securityToken;
    private final ScheduledExecutorService executor;
    private final String domain;
    private final Duration backward;
    private final Duration forward;
    private final Consumer<Prices> act;
    private final Entsoe0 entsoe = new Entsoe0();
    private final Deque<StringBuilder> stack = new ArrayDeque<>();
    private final TreeMap<ZonedDateTime,Double> map = new TreeMap<>();
    private boolean timeSeries;
    private ZonedDateTime start = ZonedDateTime.now().truncatedTo(DAYS);
    private ZonedDateTime end = start;
    private Duration resolution;
    private int position;
    private String currency;
    private String unit;
    private double average;

    public Prices(
            ScheduledExecutorService executor, 
            String securityToken, 
            String domain,
            Duration backward,
            Duration forward,
            Consumer<Prices> act
    )
    {
        this.executor = executor;
        this.securityToken = securityToken;
        this.domain = domain;
        this.backward = backward;
        this.forward = forward;
        this.act = act;
    }
    public void init()
    {
        refresh();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime first = map.firstKey();
        while (first.isBefore(now))
        {
            first = first.plus(resolution);
        }
        long delay = secondsTo(first);
        fine("first price at %s delay=%d", first, delay);
        executor.scheduleAtFixedRate(()->act.accept(this), delay, resolution.getSeconds(), TimeUnit.SECONDS);
    }

    protected void load(Path path) throws IOException
    {
        try (InputStream is = Files.newInputStream(path))
        {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, this);
        }
        catch (ParserConfigurationException | SAXException ex)
        {
            throw new IOException(ex);
        }
        
    }
    private void refresh()
    {
        map.clear();
        entsoe.readPrices(this, securityToken, domain);
        ZonedDateTime next = map.lastKey().minus(forward);
        long delay = secondsTo(next);
        executor.schedule(this::refresh, delay, TimeUnit.SECONDS);
        fine("next price refresh at %s delay=%ds", next, delay);
        average = quartAverage(start, end);
    }
    private long secondsTo(TemporalAccessor time)
    {
        return Instant.from(time).getEpochSecond() - System.currentTimeMillis()/1000;
    }
    public double getAverage()
    {
        return average;
    }
    
    public double currentPrice(ZonedDateTime now)
    {
        Map.Entry<ZonedDateTime, Double> floorEntry = map.floorEntry(now);
        return floorEntry.getValue();
    }
    public double currentAverage(ZonedDateTime now)
    {
        ZonedDateTime b = now.minus(backward);
        ZonedDateTime e = now.plus(forward);
        return quartAverage(b, e);
    }
    private double quartAverage(ZonedDateTime b, ZonedDateTime e)
    {
        double sum = 0;
        int count = 0;
        while (e.isAfter(b))
        {
            Map.Entry<ZonedDateTime, Double> entry = map.floorEntry(b);
            if (entry != null)
            {
                sum += entry.getValue();
                count++;
            }
            b = b.plus(resolution);
        }
        return sum/count;
    }
    public TreeMap<ZonedDateTime, Double> getMap()
    {
        return map;
    }

    public ZonedDateTime getStart()
    {
        return map.firstKey();
    }

    public Duration getResolution()
    {
        return resolution;
    }

    public ZonedDateTime getEnd()
    {
        return map.lastKey();
    }

    public String getCurrency()
    {
        return currency;
    }

    public String getUnit()
    {
        return unit;
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
        stack.push(new StringBuilder());
        if ("TimeSeries".equals(qName))
        {
            timeSeries = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException
    {
        String text = stack.pop().toString().trim();
        switch (qName)
        {
            case "currency_Unit.name":
                currency = text;
                break;
            case "price_Measure_Unit.name":
                unit = text;
                break;
            case "start":
                start = ZonedDateTime.parse(text).withZoneSameInstant(ZoneId.systemDefault());
                break;
            case "end":
                end = ZonedDateTime.parse(text).withZoneSameInstant(ZoneId.systemDefault());
                break;
            case "resolution":
                resolution = Duration.parse(text);
                break;
            case "position":
                position = Integer.parseInt(text);
                break;
            case "price.amount":
                ZonedDateTime zdt = start.plus(resolution.multipliedBy(position-1)).withZoneSameInstant(ZoneId.systemDefault());
                double v = Double.parseDouble(text);
                map.put(zdt, v);
                fine("price %s %f", zdt, v);
                break;
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException
    {
        stack.peek().append(ch, start, length);
    }

    
}
