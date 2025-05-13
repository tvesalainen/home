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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.vesalainen.home.hue.Resources.Resource;
import org.vesalainen.util.logging.JavaLogging;

/**
 *
 * @author Timo Vesalainen <timo.vesalainen@iki.fi>
 */
public class Hue extends JavaLogging
{

    private static final TrustManager[] trustAllCerts;
    private static final HostnameVerifier allHostsValid;
    private static final SSLSocketFactory sslSocketFactory;

    static
    {
        try
        {
            // Create a trust manager that does not validate certificate chains
            trustAllCerts = new TrustManager[]
            {
                new X509TrustManager()
                {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers()
                    {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType)
                    {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType)
                    {
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            sslSocketFactory = sc.getSocketFactory();

            // Create all-trusting host name verifier
            allHostsValid = new HostnameVerifier()
            {
                public boolean verify(String hostname, SSLSession session)
                {
                    return true;
                }
            };

        }
        catch (NoSuchAlgorithmException | KeyManagementException ex)
        {
            throw new RuntimeException(ex);
        }
    }
    private final String appName;
    private String bridgeIp;
    private String appKey;
    private Resources resources;

    public Hue(String appName) throws IOException
    {
        super(Hue.class);
        this.appName = appName;
        Preferences prefs = Preferences.userNodeForPackage(Hue.class);
        bridgeIp = prefs.get("hue-bridge-ip", null);
        if (bridgeIp == null)
        {
            bridgeIp = searchBridge();
            prefs.put("hue-bridge-ip", bridgeIp);
        }
        config("hue-bridge-ip %s", bridgeIp);
        appKey = prefs.get("hue-bridge-key", null);
        if (appKey == null)
        {
            appKey = authenticate();
            prefs.put("hue-bridge-key", appKey);
        }
        config("hue-bridge-key %s", appKey);
    }

    public void readAllResources() throws IOException
    {
        JSONArray jsonArray = getAllResources();
        JSON.dump(jsonArray);
        this.resources = new Resources(jsonArray);
    }

    public JSONArray getAllResources()
    {
        try
        {
            HttpsURLConnection urlConnection = getAuthenticatedConnection("/clip/v2/resource");
            JSONObject res = (JSONObject) fetch(urlConnection);
            return res.getJSONArray("data");
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void checkName(String name)
    {
        List<Resource> list = getResources(name);
        if (list.size() == 0)
        {
                throw new IllegalArgumentException(name+" not found");
        }
    }
    public List<Resource> getResources(String name)
    {
        return resources.getResources(name);
    }

    public Resource getResource(String id)
    {
        return resources.getResource(id);
    }
    public void update(String name, Collection<String> upd)
    {
        update(name, JSON.build(upd));
    }
    public void update(String name, String... upd)
    {
        update(name, JSON.build(upd));
    }
    public void events(Consumer<JSONObject> consumer) throws IOException
    {
        HttpsURLConnection urlConnection = getAuthenticatedConnection("/eventstream/clip/v2");
        urlConnection.setRequestProperty("Accept", "text/event-stream");
        urlConnection.connect();
        int responseCode = urlConnection.getResponseCode();
        try (InputStream is = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);)
        {
            while (true)
            {
                String id = br.readLine();
                if (id.startsWith("data:"))
                {
                    JSONTokener jsonTokener = new JSONTokener(id.substring(5));
                    JSONArray nextValue = (JSONArray) jsonTokener.nextValue();
                    nextValue.forEach((o) ->
                    {
                        JSONObject jo = (JSONObject) o;
                        consumer.accept(jo);
                    });
                }
            }
        }
    }

    private String authenticate() throws IOException
    {
        UUID uuid = UUID.randomUUID();
        while (true)
        {
            config("trying to authenticate...");
            HttpsURLConnection urlConnection = getHttpsUrlConnection("/api");
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            OutputStream os = urlConnection.getOutputStream();
            JSONObject req = JSON.build("/devicetype", uuid.toString().replace("-", "")+"#"+appName)
                    .set("/generateclientkey", true)
                    .get();
            config("req: %s", req);
            PrintWriter pw = new PrintWriter(os);
            req.write(pw);
            pw.flush();
            urlConnection.connect();
            int rc = urlConnection.getResponseCode();
            try (InputStream is = urlConnection.getInputStream())
            {
                JSONTokener jsonTokener = new JSONTokener(is);
                Object obj = jsonTokener.nextValue();
                if (rc == 200)
                {
                    config("%s", obj);
                    String key = (String) JSON.get(obj, "/0/success/username");
                    if (key != null)
                    {
                        config("authenticated!");
                        return key;
                    }
                    String err = (String) JSON.get(obj, "/0/error/description");
                    config(err);
                    Thread.sleep(1000);
                }
                else
                {
                    String err = (String) JSON.get(obj, "/errors/0/description");
                    throw new IOException(err);
                }
            }
            catch (InterruptedException ex)
            {
                log(Level.SEVERE, null, ex);
            }
        }
    }

    private HttpsURLConnection getPutConnection(String path, String type, String id, JSONObject upd) throws IOException
    {
        HttpsURLConnection urlConnection = getAuthenticatedConnection(path+"/"+type+"/"+id);
        urlConnection.setDoOutput(true);
        urlConnection.setRequestMethod("PUT");
        urlConnection.setRequestProperty("Content-Type", "application/json");
        OutputStream os = urlConnection.getOutputStream();
        PrintWriter pw = new PrintWriter(os);
        upd.write(pw);
        pw.flush();
        return urlConnection;
    }
    private HttpsURLConnection getAuthenticatedConnection(String path) throws IOException
    {
        HttpsURLConnection urlConnection = getHttpsUrlConnection(path);
        urlConnection.setRequestProperty("hue-application-key", appKey);
        return urlConnection;
    }

    private HttpsURLConnection getHttpsUrlConnection(String path) throws IOException
    {
        URL url = new URL("https://" + bridgeIp + path);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setSSLSocketFactory(sslSocketFactory);
        urlConnection.setHostnameVerifier(allHostsValid);
        urlConnection.setRequestProperty("accept", "application/json");
        return urlConnection;
    }

    private String searchBridge() throws IOException
    {
        InetAddress group = InetAddress.getByName("239.255.255.250");
        try (MulticastSocket s = new MulticastSocket();)
        {
            s.joinGroup(group);
            String search =
                    "M-SEARCH * HTTP/1.1\r\n"+
                    "HOST: 239.255.255.250:1900\r\n"+
                    "MAN: ssdp:discover\r\n"+
                    "MX: 4\r\n"+
                    "ST: upnp:rootdevice\r\n"+
                    "USER-AGENT:"+System.getProperty("os.name")+":"+System.getProperty("os.version");
            DatagramPacket src = new DatagramPacket(search.getBytes(), search.length(),
                             group, 1900);
            byte[] buf = new byte[1000];
            s.send(src);
            while (true)
            {
                boolean ok = false;
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                s.receive(recv);
                String msg = new String(recv.getData(), recv.getOffset(), recv.getLength());
                System.err.println(msg);
                String[] flds = msg.split("\r\n");
                URL url = null;
                for (String fld : flds)
                {
                    int idx = fld.indexOf(':');
                    if (idx != -1)
                    {
                        String n = fld.substring(0, idx);
                        String v = fld.substring(idx+1).trim();
                        switch (n)
                        {
                            case "LOCATION":
                                url = new URL(v);
                                break;
                            case "SERVER":
                                if (v.startsWith("Hue"))
                                {
                                    ok = true;
                                }
                                break;
                        }
                    }
                }
                if (ok && url != null)
                {
                    return url.getHost();
                }
            }
        }
    }

    private Object fetch(HttpsURLConnection con) throws IOException
    {
        con.connect();
        int rc = con.getResponseCode();
        if (rc >= 200 && rc < 300)
        {
            try (InputStream is = con.getInputStream())
            {
                JSONTokener jsonTokener = new JSONTokener(is);
                Object obj = jsonTokener.nextValue();
                if (rc == 200)
                {
                    return obj;
                }
                else
                {
                    String err = (String) JSON.get(obj, "errors:0:description");
                    throw new IOException(err);
                }
            }
        }
        else
        {
            try (InputStream is = con.getErrorStream();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr))
            {
                String line = br.readLine();
                while (line != null)
                {
                    System.err.println(line);
                    line = br.readLine();
                }
            }
            throw new IOException("rc = "+rc);
        }
    }

    public void update(String name, JSONObject upd)
    {
        Set<String> keySet = JSON.keySet(upd);
        update(name, (ks)->upd, (s)->s.containsAll(keySet));
    }
    public Collection<Resource> getResource(String name, String... upd)
    {
        return getResource(name, JSON.build(upd));
    }
    public Collection<Resource> getResource(String name, JSONObject upd)
    {
        List<Resource> list = new ArrayList<>();
        Set<String> updSet = JSON.keySet(upd);
        for (Resource res : getResources(name))
        {
            getResources(res, updSet, list);
        }
        return list;
    }
    private void getResources(Resource res, Set<String> updSet, List<Resource> list)
    {
        if (res != null)
        {
            Set<String> keySet = res.keySet();
            if (keySet.containsAll(updSet))
            {
                list.add(res);
            }
            else
            {
                for (Resource r : res.services())
                {
                    getResources(r, updSet, list);
                }
                if (list.isEmpty())
                {
                    for (Resource r : res.childrens())
                    {
                        getResources(r, updSet, list);
                    }
                }
            }
        }
    }
    public void update(String name, Function<Set<String>,JSONObject> upd, Predicate<Set<String>> predicate)
    {
        info("update %s", name);
        for (Resource res : getResources(name))
        {
            Set<String> ks1 = res.keySet();
            fine("trying.. %s", ks1);
            if (predicate.test(ks1))
            {
                JSONObject d = upd.apply(res.valueSet());
                update(res, d);
                return;
            }
            for (Resource r : res.services())
            {
                Set<String> ks2 = r.keySet();
                fine("trying.. %s", ks2);
                if (r != null && predicate.test(ks2))
                {
                    JSONObject d = upd.apply(ks2);
                    update(r, d);
                    return;
                }
            }
            for (Resource r : res.childrens())
            {
                Set<String> ks2 = r.keySet();
                fine("trying.. %s", ks2);
                if (r != null && predicate.test(ks2))
                {
                    JSONObject d = upd.apply(ks2);
                    update(r, d);
                    return;
                }
            }
        }
        return;
    }

    public void update(Collection<Resource> res, String... upd)
    {
        update(res, JSON.build(upd));
    }
    public void update(Collection<Resource> res, JSONObject u)
    {
        for (Resource r : res)
        {
            update(r, u);
        }
    }
    public void update(Resource res, Collection<String>upd)
    {
        update(res, JSON.build(upd));
    }
    public void update(Resource res, JSONObject upd)
    {
        try
        {
            info("update %s %s", res, upd);
            HttpsURLConnection putConnection = getPutConnection("/clip/v2/resource", res.getType(), res.getId(), upd);
            fetch(putConnection);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

}
