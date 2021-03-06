/*  Copyright 2015 Fabrizio Cannizzo
 *
 *  This file is part of RestFixture.
 *
 *  RestFixture (http://code.google.com/p/rest-fixture/) is free software:
 *  you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation,
 *  either version 3 of the License, or (at your option) any later version.
 *
 *  RestFixture is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with RestFixture.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  If you want to contact the author please leave a comment here
 *  http://smartrics.blogspot.com/2008/08/get-fitnesse-with-some-rest.html
 */
package smartrics.rest.test.fitnesse.fixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;

/**
 * The controller.
 *
 * @author smartrics
 */
public class ResourcesServlet extends HttpServlet {
    public static final String CONTEXT_ROOT = "/resources";
    private static final Logger LOG = LoggerFactory.getLogger(ResourcesServlet.class);
    private static final long serialVersionUID = -7012866414216034826L;
    private static final String DEF_CHARSET = "ISO-8859-1";
    private final Resources resources = Resources.getInstance();

    public ResourcesServlet() {
        LOG.info("Resources: " + resources.toString());
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Resource GET REQUEST ========= " + req.toString());
        String uri = sanitise(req.getRequestURI());
        boolean isRedirect = isRedirect(uri);
        if (isRedirect) {
            uri = redirectTo(uri);
            doRedirect(resp, uri);
            return;
        }
        if (uri.startsWith("/files/support")) {
            final String name = "FitNesseRoot" + uri;
            if (name.endsWith("html")) {
                resp.addHeader("Content-Type", "text/html");
            } else if (name.endsWith("txt")) {
                resp.addHeader("Content-Type", "text/plain");
            } else if (name.endsWith("json")) {
                resp.addHeader("Content-Type", "application/json");
            } else if (name.endsWith("xml")) {
                resp.addHeader("Content-Type", "application/xml");
            }
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
            resp.getOutputStream().write(convertStreamToString(is).getBytes());
        } else if (uri.contains("/large")) {
            final String size = req.getParameter("size");
            int bytes = 1024 * 1024; // bytes
            if (size != null) {
                bytes = Integer.parseInt(size);
            }
            resp.addHeader("Content-Type", "application/json");
            resp.getOutputStream().write(largeResource(bytes).getBytes());
        } else if(uri.contains("/map")){
            resp.addHeader("Content-Type", "application/json");
            resp.getOutputStream().write(mapResource().getBytes());
        } else {
            String id = getId(uri);
            String type = getType(uri);
            String extension = getExtension(uri);
            echoHeader(req, resp);
            echoQString(req, resp);
            setCookieHeaderIssue118(req, resp);
            try {
                if (id == null) {
                    list(resp, type, extension);
                    headers(resp, extension, DEF_CHARSET);
                } else if (resources.get(type, id) == null) {
                    notFound(resp);
                } else {
                    if (resources.get(type, id).isDeleted()) {
                        notFound(resp);
                    } else {
                        found(resp, type, id);
                        headers(resp, extension, DEF_CHARSET);
                    }
                }
            } catch (RuntimeException e) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            } finally {
                LOG.debug("Resource GET RESPONSE ========= " + resp.toString());
            }
        }
    }

    private String mapResource() {
        StringBuilder b = new StringBuilder(
                "{\n" +
                "    '1' : { 'name': 'fred', 'surname' : 'stone' },\n" +
                "    '2' : { 'name': 'marta', 'surname' : 'bogart' },\n" +
                "    '3' : { 'name': 'foo', 'surname' : 'bar' }\n" +
                "  }\n"
        );
        return "{ 'map' : " + b.toString() + "}";
    }

    private String largeResource(int bytes) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bytes; i++) {
            b.append("A");
        }
        return "{ 'content' : '" + b.toString() + "' }";
    }

    private String redirectTo(String uri) {
        return uri.replace("/redirect", "");
    }

    private boolean isRedirect(String uri) {
        return uri.contains("/redirect");
    }

    private void echoQString(HttpServletRequest req, HttpServletResponse resp) {
        String qstring = req.getQueryString();
        if (qstring != null) {
            resp.setHeader("Query-String", qstring);
        }
    }

    private String sanitise(String rUri) {
        String uri = rUri;
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private void headers(HttpServletResponse resp, String extension, String optCharset) {
        resp.setStatus(HttpServletResponse.SC_OK);
        String s = "";
        if (optCharset != null) {
            s = ";charset=" + optCharset;
        }
        resp.addHeader("Content-Type", "application/" + extension + s);
    }

    private void doRedirect(HttpServletResponse resp, String uri) {
        resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        resp.addHeader("Location", uri);
    }

    private void list(HttpServletResponse resp, String type, String extension) throws IOException {
        if (type.contains("root-context")) {
            list(resp, extension);
        } else {
            StringBuffer buffer = new StringBuffer();
            String slashremoved = type.substring(1);
            if ("json".equals(extension)) {
                buffer.append("{ \"" + slashremoved + "\" : ");
            } else {
                buffer.append("<" + slashremoved + ">");
            }
            for (Resource r : resources.asCollection(type)) {
                buffer.append(r.getPayload());
            }
            if ("json".equals(extension)) {
                buffer.append("}");
            } else {
                buffer.append("</" + slashremoved + ">");
            }
            resp.getOutputStream().write(buffer.toString().getBytes());
        }
    }

    private void list(HttpServletResponse resp, String extension) throws IOException {
        StringBuffer buffer = new StringBuffer();
        if ("json".equals(extension)) {
            buffer.append("{ \"root-context\" : ");
        } else {
            buffer.append("<root-context>");
        }
        resp.getOutputStream().write(buffer.toString().getBytes());
        for (String s : resources.contexts()) {
            list(resp, s, extension);
        }
        buffer = new StringBuffer();
        if ("json".equals(extension)) {
            buffer.append("}");
        } else {
            buffer.append("</root-context>");
        }
        resp.getOutputStream().write(buffer.toString().getBytes());
    }

    private String getExtension(String uri) {
        int extensionPoint = uri.lastIndexOf(".");
        if (extensionPoint != -1) {
            return uri.substring(extensionPoint + 1);
        } else {
            return "xml";
        }
    }

    private void found(HttpServletResponse resp, String type, String id) throws IOException {
        StringBuffer buffer = new StringBuffer();
        Resource r = resources.get(type, id);
        buffer.append(r);
        resp.getOutputStream().write(buffer.toString().getBytes());
        // resp.setHeader("Content-Lenght",
        // Integer.toString(buffer.toString().getBytes().length));
    }

    private String getType(String uri) {
        if (uri.length() <= 1) {
            return "/root-context";
        }
        int pos = uri.substring(1).indexOf('/');
        String ret = uri;
        if (pos >= 0) {
            ret = uri.substring(0, pos + 1);
        }
        return ret;
    }

    private void notFound(HttpServletResponse resp) throws IOException {
        resp.getOutputStream().write("".getBytes());
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        // resp.setHeader("Content-Lenght", "0");
    }

    private void setCookieHeaderIssue118(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Set-Cookie", "JID=\"ABC.${a.y}\"; ");
    }

    private void echoHeader(HttpServletRequest req, HttpServletResponse resp) {
        String s = req.getHeader("Echo-Header");
        if (s != null) {
            resp.setHeader("Echo-Header", s);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Resource DELETE REQUEST ========= " + req.toString());
        String uri = sanitise(req.getRequestURI());
        String type = getType(uri);
        echoHeader(req, resp);
        echoQString(req, resp);
        String id = getId(uri);
        Resource resource = resources.get(type, id);
        if (resource != null) {
            // resource.setDeleted(true);
            resources.remove(type, id);
            resp.getOutputStream().write("".getBytes());
            resp.setStatus(HttpServletResponse.SC_OK);
        } else {
            notFound(resp);
        }
        resp.getOutputStream().write("".getBytes());
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        LOG.debug("Resource DELETE RESPONSE ========= " + req.toString());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Resource PUT REQUEST ========= " + req.toString());
        echoHeader(req, resp);
        echoQString(req, resp);
        String uri = sanitise(req.getRequestURI());
        String id = getId(uri);
        String type = getType(uri);
        String content = getContent(req.getInputStream());
        Resource resource = resources.get(type, id);
        if (resource != null) {
            resource.setPayload(content);
            resp.getOutputStream().write("".getBytes());
            resp.setStatus(HttpServletResponse.SC_OK);
        } else {
            notFound(resp);
        }
        LOG.debug("Resource PUT RESPONSE ========= " + req.toString());
    }

    private String getId(String uri) {
        if (uri.length() <= 1) {
            return null;
        }
        int pos = uri.substring(1).lastIndexOf("/");
        String sId = null;
        if (pos > 0) {
            sId = uri.substring(pos + 2);
        }
        if (sId != null) {
            int pos2 = sId.lastIndexOf('.');
            if (pos2 >= 0) {
                sId = sId.substring(0, pos2);
            }
        }
        return sId;
    }

    private void processMultiPart(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();
        resp.setContentType("text/plain");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOG.debug("Resource POST REQUEST ========= " + req.toString());
        echoHeader(req, resp);
        echoQString(req, resp);
        String uri = sanitise(req.getRequestURI());
        String type = getType(uri);
        try {
            String contentType = req.getContentType();
            if (contentType.equals("application/octet-stream")) {
                LOG.debug("Resource POST REQUEST is a file upload");
                writeUploaded(req, resp);
            } else if (contentType.startsWith("multipart")) {
                LOG.debug("Resource POST REQUEST is a multipart file upload");
                writeUploaded(req, resp);
            } else {
                String content = getContent(req.getInputStream());
                if (contentType.contains("application/x-www-form-urlencoded")) {
                    try {
                        generateResponse(resp, type, noddyKvpToXml(content, "UTF-8"));
                    } catch (Exception e) {
                        LOG.warn("the content passed in isn't encoded as application/x-www-form-urlencoded: " + content);
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    }
                } else if (content.trim().startsWith("<") || content.trim().endsWith("}")) {
                    generateResponse(resp, type, content);
                } else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        } catch (RuntimeException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        LOG.debug("Resource POST RESPONSE ========= " + req.toString());
    }

    private String noddyKvpToXml(String content, String encoding) throws UnsupportedEncodingException {
        StringBuffer sb = new StringBuffer();
        sb.append("<resource>").append("\n");
        String[] kvpArray = content.split("&");
        for (String e : kvpArray) {
            String[] kvp = e.split("=");
            sb.append("<").append(kvp[0]).append(">");
            if (kvp.length > 1) {
                sb.append(URLDecoder.decode(kvp[1], encoding));
            }
            sb.append("</").append(kvp[0]).append(">").append("\n");
        }
        sb.append("</resource>");
        return sb.toString();
    }

    private void generateResponse(HttpServletResponse resp, String type, String content) {
        Resource newResource = new Resource(content);
        resources.add(type, newResource);
        // TODO: should put the ID in
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.addHeader("Location", type + "/" + newResource.getId());
    }

    private void writeUploaded(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        InputStream is = req.getInputStream();
        PrintWriter out = resp.getWriter();
        resp.setContentType("text/plain");
        String fileContents = getContent(is);
        out.print(fileContents.trim());
        out.flush();
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private String getContent(InputStream is) throws IOException {
        StringBuffer sBuff = new StringBuffer();
        int c;
        while ((c = is.read()) != -1) {
            sBuff.append((char) c);
        }
        String content = sBuff.toString();
        return content;
    }
}
