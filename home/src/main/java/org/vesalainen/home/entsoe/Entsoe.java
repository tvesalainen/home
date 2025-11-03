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
import java.time.format.DateTimeFormatter;
import static java.time.temporal.ChronoUnit.DAYS;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
public class Entsoe extends DefaultHandler implements AttachedLogger
{
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private final IndexedData data;
    private final Deque<StringBuilder> stack = new ArrayDeque<>();
    private boolean timeSeries;
    private String unit;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private Duration resolution;
    private String currency;
    private int position;
    private final String securityToken;
    private final String domain;
    private final String documentType;
    private final Restarter restarter;
    private final ScheduledExecutorService executor;
    private int lastPosition;
    private double lastPrice;

    public Entsoe(ScheduledExecutorService executor, String securityToken, String domain, IndexedData data)
    {
        this(executor, securityToken, "A44", domain, data);
    }
    public Entsoe(ScheduledExecutorService executor, String securityToken, String documentType, String domain, IndexedData data)
    {
        this.executor = executor;
        this.restarter = new Restarter(executor, 5, TimeUnit.MINUTES, 10, Duration.ofHours(6));
        this.securityToken = securityToken;
        this.documentType = documentType;
        this.domain = domain;
        this.data = data;
    }
    
    public void startReading()
    {
        restarter.execute(()->read());
    }
    public void startReadingAndWait()
    {
        restarter.executeAndWait(()->read());
    }
    private long secondsTo(TemporalAccessor time)
    {
        return Instant.from(time).getEpochSecond() - System.currentTimeMillis()/1000;
    }
    private ZonedDateTime read()
    {
        ZonedDateTime e = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("Z")).truncatedTo(DAYS);
        return read(e, e.plusDays(1));
    }
    private ZonedDateTime read(
                ZonedDateTime periodStart,
                ZonedDateTime periodEnd
            
    )
    {
        fine("read prices %s - %s", periodStart, periodEnd);
        return read(
                periodStart.format(DATE_TIME), 
                periodEnd.format(DATE_TIME));
    }
    private ZonedDateTime read(
                String periodStart,
                String periodEnd
            
    )
    {
        lastPosition = 0;
        try
        {
            URL url = new URL("https://web-api.tp.entsoe.eu/api?"+
                    "securityToken="+securityToken+"&"+
                    "documentType="+documentType+"&"+
                    "periodStart="+periodStart+"&"+
                    "periodEnd="+periodEnd+"&"+
                    "out_Domain="+domain+"&"+
                    "in_Domain="+domain
            );
            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("accept", "text/xml");
            urlConnection.connect();
            int rc = urlConnection.getResponseCode();
            if (rc >= 200 && rc < 300)
            {
                try (InputStream is = urlConnection.getInputStream();
                        )
                {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser parser = factory.newSAXParser();
                    parser.parse(is, this);
                    return end;
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
        catch (SAXException | ParserConfigurationException | IOException ex)
        {
            throw new RuntimeException(ex);
        }
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
                double price = Double.parseDouble(text);
                for (int ii=lastPosition;ii<position-1;ii++)
                {
                    ZonedDateTime zdt = start.plus(resolution.multipliedBy(ii)).withZoneSameInstant(ZoneId.systemDefault());
                    data.set(zdt, "price", lastPrice);
                    fine("price %s %f", zdt, lastPrice);
                }
                ZonedDateTime zdt = start.plus(resolution.multipliedBy(position-1)).withZoneSameInstant(ZoneId.systemDefault());
                data.set(zdt, "price", price);
                fine("price %s %f", zdt, price);
                lastPosition = position;
                lastPrice = price;
                break;
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException
    {
        stack.peek().append(ch, start, length);
    }

}
