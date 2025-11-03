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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static java.time.temporal.ChronoUnit.DAYS;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
@Deprecated public class Entsoe0
{
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    public Prices readPrices(
                Prices prices,
                String securityToken,
                String domain
    )
    {
        LocalDateTime end = LocalDateTime.now().truncatedTo(DAYS);
        return read(prices, securityToken, "A44", end, end.plusDays(1), domain);
    }
    public Prices read(
                Prices prices,
                String securityToken,
                String documentType,
                LocalDateTime periodStart,
                LocalDateTime periodEnd,
                String domain
            
    )
    {
        return read(
                prices,
                securityToken, 
                documentType, 
                periodStart.format(DATE_TIME), 
                periodEnd.format(DATE_TIME), 
                domain, 
                domain);
    }
    public Prices read(
                Prices prices,
                String securityToken,
                String documentType,
                String periodStart,
                String periodEnd,
                String outDomain,
                String inDomain
            
    )
    {
        try
        {
            URL url = new URL("https://web-api.tp.entsoe.eu/api?"+
                    "securityToken="+securityToken+"&"+
                    "documentType="+documentType+"&"+
                    "periodStart="+periodStart+"&"+
                    "periodEnd="+periodEnd+"&"+
                    "out_Domain="+outDomain+"&"+
                    "in_Domain="+inDomain
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
                    parser.parse(is, prices);
                    return prices;
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
}
