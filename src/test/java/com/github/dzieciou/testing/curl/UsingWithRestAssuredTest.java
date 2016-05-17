package com.github.dzieciou.testing.curl;


import com.google.common.base.Throwables;
import com.jayway.restassured.config.HttpClientConfig;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.mockserver.client.server.MockServerClient;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import static com.jayway.restassured.RestAssured.config;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.HttpClientConfig.httpClientConfig;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class UsingWithRestAssuredTest {

    public static final int MOCK_PORT = 9999;
    public static final String MOCK_HOST = "localhost";
    public static final String MOCK_BASE_URI = "http://" + MOCK_HOST;
    private MockServerClient mockServer;

    @BeforeClass
    public void setupMock() {
        mockServer = startClientAndServer(MOCK_PORT);
        mockServer.when(request()).respond(response());
    }


    @Test(groups = "end-to-end-samples")
    public void basicIntegrationTest() {

        Consumer<String> curlConsumer = mock(Consumer.class);

        //@formatter:off
        given()
                .redirects().follow(false)
                .baseUri( MOCK_BASE_URI)
                .port(MOCK_PORT)
                .config(config()
                        .httpClient(httpClientConfig()
                                .reuseHttpClientInstance().httpClientFactory(new MyHttpClientFactory(curlConsumer))))
        .when()
                .get("/")
        .then()
                .statusCode(200);
        //@formatter:on

        verify(curlConsumer).accept("curl 'http://localhost:" + MOCK_PORT + "/' -H 'Accept: */*' -H 'Content-Length: 0' -H 'Host: localhost:" + MOCK_PORT + "' -H 'Connection: Keep-Alive' -H 'User-Agent: Apache-HttpClient/4.5.1 (Java/1.8.0_45)' --compressed --insecure --verbose");
    }

    @Test(groups = "end-to-end-samples")
    public void shouldPrintPostRequestWithMultipartDataProperly() {

        Consumer<String> curlConsumer = mock(Consumer.class);

        //@formatter:off
        given()
                .baseUri(MOCK_BASE_URI)
                .port(MOCK_PORT)
                .config(config()
                        .httpClient(httpClientConfig()
                                .reuseHttpClientInstance().httpClientFactory(new MyHttpClientFactory(curlConsumer)))).
                log().all()
                .multiPart(new File("README.md")).formParam("x", "yyyyyyy")
         .when().post("/");
        //@formatter:on

        verify(curlConsumer).accept("curl 'http://localhost:9999/' -F 'file=@README.md' -F 'x=yyyyyyy;type=text/plain' -X POST -H 'Accept: */*' -H 'Host: localhost:9999' -H 'Connection: Keep-Alive' -H 'User-Agent: Apache-HttpClient/4.5.1 (Java/1.8.0_45)' --compressed --insecure --verbose");

    }

    @Test(groups = "end-to-end-samples")
    public void shouldPrintMultipartWithContentTypesForTypes() {

        Consumer<String> curlConsumer = mock(Consumer.class);

        //@formatter:off
        given()
                .baseUri(MOCK_BASE_URI)
                .port(MOCK_PORT)
                .config(config()
                        .httpClient(httpClientConfig()
                                .reuseHttpClientInstance().httpClientFactory(new MyHttpClientFactory(curlConsumer)))).
                log().all()
                .multiPart("message", "{ content : \"interesting\" }", "application/json")
                .when().post("/");
        //@formatter:on

        verify(curlConsumer).accept("curl 'http://localhost:9999/' -F 'message={ content : \"interesting\" };type=application/json' -X POST -H 'Accept: */*' -H 'Host: localhost:9999' -H 'Connection: Keep-Alive' -H 'User-Agent: Apache-HttpClient/4.5.1 (Java/1.8.0_45)' --compressed --insecure --verbose");


        // TODO Mariusz example
        // http://stackoverflow.com/questions/27231031/set-content-type-of-part-of-multipart-mixed-request-in-curl
    }

    private static class MyHttpClientFactory implements HttpClientConfig.HttpClientFactory {

        public final Consumer<String> curlConsumer;

        private MyHttpClientFactory(Consumer<String> curlConsumer) {
            this.curlConsumer = curlConsumer;
        }

        @Override
        public HttpClient createHttpClient() {
            AbstractHttpClient client = new DefaultHttpClient();
            client.addRequestInterceptor(new CurlTestingInterceptor(curlConsumer));
            return client;
        }
    }

    private static class CurlTestingInterceptor implements HttpRequestInterceptor {

        public final Consumer<String> curlConsumer;

        public CurlTestingInterceptor(Consumer<String> curlConsumer) {
            this.curlConsumer = curlConsumer;
        }

        @Override
        public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
            try {
                curlConsumer.accept(Http2Curl.generateCurl(request));
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @AfterClass
    public void closeMock() {
        mockServer.stop();
    }

}
