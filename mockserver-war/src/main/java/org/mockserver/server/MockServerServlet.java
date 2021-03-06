package org.mockserver.server;

import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.LogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private LogFilter logFilter = new LogFilter();
    private ActionHandler actionHandler = new ActionHandler(logFilter);
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    @Override
    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doHead(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doDelete(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doOptions(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doTrace(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        try {
            String requestPath = retrieveRequestPath(httpServletRequest);
            if (requestPath.equals("/status")) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());

            } else if (requestPath.equals("/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
                mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
                httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());

            } else if (requestPath.equals("/clear")) {

                HttpRequest httpRequest = httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
                logFilter.clear(httpRequest);
                mockServerMatcher.clear(httpRequest);
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/reset")) {

                logFilter.reset();
                mockServerMatcher.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);

            } else if (requestPath.equals("/verify")) {

                String result = logFilter.verify(verificationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/verifySequence")) {

                String result = logFilter.verify(verificationSequenceSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/stop")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());

            } else {

                mockResponse(httpServletRequest, httpServletResponse);

            }
        } catch (Exception e) {
            logger.error("Exception processing " + httpServletRequest, e);
            httpServletResponse.setStatus(HttpStatusCode.BAD_REQUEST_400.code());
        }
    }

    private String retrieveRequestPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
    }

    private void mockResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
        HttpResponse httpResponse = actionHandler.processAction(mockServerMatcher.handle(httpRequest), httpRequest);
        mapResponse(httpResponse, httpServletResponse);
    }


    private void mapResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse != null) {
            mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_FOUND_404.code());
        }
    }
}
