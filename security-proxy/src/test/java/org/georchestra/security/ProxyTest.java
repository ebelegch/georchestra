package org.georchestra.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.ReflectionUtils;

import com.google.common.collect.Maps;

public class ProxyTest {
    private Proxy proxy;
    private BasicHttpResponse response;
    private boolean executed = true;
    private MockHttpServletRequest request;
    private MockHttpServletResponse httpResponse;

    @Before
    public void setUp() throws Exception {
        response = null;
        executed = false;
        proxy = new Proxy() {
            @Override
            protected HttpResponse executeHttpRequest(HttpClient httpclient, HttpRequestBase proxyingRequest) throws IOException {
                executed = true;
                return response;
            }
        };

        proxy.setProxyPermissionsFile("default-permissions.xml");
        proxy.init();

        response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        this.request = new MockHttpServletRequest("GET", "/proxy/");
        this.request.setServerName("localhost");
        this.request.setServerPort(80);
        this.httpResponse = new MockHttpServletResponse();

        Map<String, String> targets = Maps.newHashMap();
        targets.put("geonetwork", "http://www.google.com/geonetwork-private");
        targets.put("extractorapp", "http://localhost/extractorapp-private");
        proxy.setTargets(targets);

        proxy.setDefaultTarget("/mapfishapp/");

    }

    @Test
    public void testGetUrlLegalUrl() throws Exception {
        proxy.handleUrlParamRequest(request, httpResponse, "http://localhost:8080/path");
        assertTrue(executed);
    }

    /**
     * All url proxy requests (http://server/proxy?url=...) that try to directly access protected services like extractorapp should
     * be rejected.  In the cases where the protected services are required, the url parameter should be the public url of the
     * protected service (not the private one).
     */
    @Test
    public void testGetUrlTarget() throws Exception {
        proxy.handleUrlParamRequest(request, httpResponse, "http://localhost:8080/geonetwork-private");
        assertFalse(executed);

        this.httpResponse = new MockHttpServletResponse();
        proxy.handleUrlParamRequest(request, httpResponse, "http://localhost:8080/extractorapp");
        assertFalse(executed);
    }

    /**
     * Ensure that when the proxy form: http://server.com/geonetwork/srv/eng/home is used to access a protected service,
     * the request should always be allowed no matter the proxy permissions.
     */
    @Test
    public void testGetUrlLegalUrlButTarget() throws Exception {
        request = new MockHttpServletRequest("GET", "http://localhost:8080/geonetwork-private");
        proxy.handleRequest(request, httpResponse);
        assertFalse(executed);

        this.httpResponse = new MockHttpServletResponse();
        request = new MockHttpServletRequest("GET", "/geonetwork/srv/eng/something");
        proxy.handleRequest(request, httpResponse);
        assertTrue(executed);

        this.httpResponse = new MockHttpServletResponse();
        executed = false;
        request = new MockHttpServletRequest("GET", "/extractorapp/home");
        proxy.handleRequest(request, httpResponse);
        assertTrue(executed);

        this.httpResponse = new MockHttpServletResponse();
        executed = false;
        request = new MockHttpServletRequest("GET", "/unmapped/x");
        proxy.handleRequest(request, httpResponse);
        assertFalse(executed);
    }

    @Test
    public void testDefaultTarget() throws Exception {
        request = new MockHttpServletRequest("GET", "http://localhost:8080/");
        proxy.handleDefaultRequest(request, httpResponse);

        assertTrue(httpResponse.getRedirectedUrl().equals("/mapfishapp/"));

    }

    @Test
    public void testGetUrlIllegalUrl() throws Exception {
        proxy.handleUrlParamRequest(request, httpResponse, "http://www.google.com:8080/path");
        assertFalse(executed);
    }

    @Test
    public void testBuildUri() throws Exception {
        Proxy proxy = new Proxy();
        Method m = ReflectionUtils.findMethod(Proxy.class, "buildUri", URL.class);
        m.setAccessible(true);

        URI ret = (URI) ReflectionUtils.invokeMethod(m, proxy, new URL("https://dev.pigma.org/geonetwork/srv/fre/xml.keyword.get?"+
        "thesaurus=local.theme.pigma&id=http://ids.pigma.org/themes%23Eau&multiple=false&transformation=to-iso19139-keyword"));
        assertTrue(ret.toString().contains("id=http://ids.pigma.org/themes%23Eau"));

        ret = (URI) ReflectionUtils.invokeMethod(m, proxy, new URL("https://sdi.georchestra.org/console/account/recover?email=psc%2Btestuser%40georchestra.org"));
        assertTrue(ret.toString().contains("email=psc%2Btestuser%40georchestra.org"));

        // defunct URLs:
        // http://localhost:8080/geonetwork/srv/api/0.1/standards/iso19139/codelists/gmd%3ADS_InitiativeTypeCode
        // SP will re-encode %3A to %253A
        // see https://github.com/georchestra/georchestra/issues/2020#issuecomment-402449307
        ret = (URI) ReflectionUtils.invokeMethod(m, proxy, new URL("http://localhost:8080/geonetwork/srv/api/0.1/standards/iso19139/codelists/gmd%3ADS_InitiativeTypeCode"));
        assertTrue(! ret.toString().contains("%253ADS"));
    }
}