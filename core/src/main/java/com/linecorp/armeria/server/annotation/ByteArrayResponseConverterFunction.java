/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.server.annotation;

import static com.linecorp.armeria.internal.annotation.ResponseConversionUtil.streamingFrom;
import static com.linecorp.armeria.internal.annotation.ResponseConversionUtil.toMutableHeaders;

import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.reactivestreams.Publisher;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServiceRequestContext;

/**
 * A response converter implementation which creates an {@link HttpResponse} with
 * {@code content-type: application/binary} or {@code content-type: application/octet-stream}.
 */
public class ByteArrayResponseConverterFunction implements ResponseConverterFunction {

    @Override
    public HttpResponse convertResponse(ServiceRequestContext ctx,
                                        HttpHeaders headers,
                                        @Nullable Object result,
                                        HttpHeaders trailingHeaders) throws Exception {
        final MediaType contentType = headers.contentType();
        if (contentType != null) {
            // @Produces("application/binary") or @ProducesBinary
            // @Produces("application/octet-stream") or @ProducesOctetStream
            if (contentType.is(MediaType.APPLICATION_BINARY) ||
                contentType.is(MediaType.OCTET_STREAM)) {
                // We assume that the publisher and stream produces HttpData or byte[].
                // An IllegalStateException will be raised for other types due to conversion failure.
                if (result instanceof Publisher) {
                    return streamingFrom((Publisher<?>) result, headers, trailingHeaders,
                                         ByteArrayResponseConverterFunction::toHttpData);
                }
                if (result instanceof Stream) {
                    return streamingFrom((Stream<?>) result, headers, trailingHeaders,
                                         ByteArrayResponseConverterFunction::toHttpData,
                                         ctx.blockingTaskExecutor());
                }
                if (result instanceof HttpData) {
                    return HttpResponse.of(headers, (HttpData) result, trailingHeaders);
                }
                if (result instanceof byte[]) {
                    return HttpResponse.of(headers, HttpData.of((byte[]) result), trailingHeaders);
                }

                return ResponseConverterFunction.fallthrough();
            }
        } else if (result instanceof HttpData) {
            return HttpResponse.of(toMutableHeaders(headers).contentType(MediaType.OCTET_STREAM),
                                   (HttpData) result, trailingHeaders);
        } else if (result instanceof byte[]) {
            return HttpResponse.of(toMutableHeaders(headers).contentType(MediaType.OCTET_STREAM),
                                   HttpData.of((byte[]) result), trailingHeaders);
        }

        return ResponseConverterFunction.fallthrough();
    }

    private static HttpData toHttpData(@Nullable Object value) {
        if (value instanceof HttpData) {
            return (HttpData) value;
        }
        if (value instanceof byte[]) {
            return HttpData.of((byte[]) value);
        }
        if (value == null) {
            return HttpData.EMPTY_DATA;
        }
        throw new IllegalStateException("Failed to convert an object to an HttpData: " +
                                        value.getClass().getName());
    }
}
