/**
 * ***************************************************************************** Copyright (c)
 * 2009-2019 Weasis Team and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * <p>Contributors: Nicolas Roduit - initial API and implementation
 * *****************************************************************************
 */
package org.weasis.dicom.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DicomStowRS implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomStowRS.class);
    /**
     * @see <a href="https://tools.ietf.org/html/rfc2387">multipart specifications</a>
     */
    protected static final String MULTIPART_BOUNDARY = "mimeTypeBoundary";

    public enum HttpContentType {
        DICOM("application/dicom"), XML("application/dicom+xml"), JSON("application/dicom+json");

        private final String type;

        HttpContentType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    private final ContentType contentType;
    private final String requestURL;
    private final String agentName;
    private final Map<String, String> headers;
    private boolean photo = false;
    private final HttpContentType type = HttpContentType.XML;
    private final MultipartBody multipartBody;
    private final HttpClient client;
    private final ExecutorService executorService;

    /**
     * @param requestURL  the URL of the STOW service
     * @param contentType the value of the type in the Content-Type HTTP property
     * @param agentName   the value of the User-Agent HTTP property
     * @param headers     some additional header properties.
     * @throws IOException Exception during the POST initialization
     */
    public DicomStowRS(
            String requestURL,
            ContentType contentType,
            String agentName,
            Map<String, String> headers) {
        this.contentType = Objects.requireNonNull(contentType);
        this.requestURL = Objects.requireNonNull(requestURL, "requestURL cannot be null");
        this.headers = headers;
        this.agentName = agentName;
        this.multipartBody = new MultipartBody(ContentType.APPLICATION_DICOM, MULTIPART_BOUNDARY);
        this.executorService = Executors.newFixedThreadPool(5);
        this.client = HttpClient.newBuilder()
                .executor(executorService).followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    protected HttpRequest buildConnection(Flow.Publisher<? extends ByteBuffer> multipartSubscriber) throws Exception {
        ContentType partType = ContentType.APPLICATION_DICOM;
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (headers != null && !headers.isEmpty()) {
            for (Entry<String, String> element : headers.entrySet()) {
                builder.header(element.getKey(), element.getValue());
            }
        }
        builder.header("Accept", type.toString());
        builder.header("User-Agent", agentName == null ? "Weasis STOWRS" : agentName);

        HttpRequest request =
                builder.header("Content-Type", "multipart/related;type=\"" + partType.type + "\";boundary=" + MULTIPART_BOUNDARY)
                        .POST(HttpRequest.BodyPublishers.fromPublisher(multipartSubscriber))
                        .uri(new URI(requestURL))
                        .build();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("> POST {} {}", request.uri().getRawPath(), request.version().orElse(HttpClient.Version.HTTP_2));
            LOGGER.debug("> Host: {}:{}", request.uri().getHost(), request.uri().getPort());
            promptHeaders("> ", request.headers());
//            multipartBody.prompt();
        }
        return request;
    }

    protected HttpRequest buildConnection(Payload firstPlayLoad, Supplier<? extends InputStream> streamSupplier) throws Exception {
        ContentType partType = ContentType.APPLICATION_DICOM;
        multipartBody.addPart(partType.type, firstPlayLoad, null);

        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (headers != null && !headers.isEmpty()) {
            for (Entry<String, String> element : headers.entrySet()) {
                builder.header(element.getKey(), element.getValue());
            }
        }
        builder.header("Accept", type.toString());
        builder.header("User-Agent", agentName == null ? "Weasis STOWRS" : agentName);

        HttpRequest request =
                builder.header("Content-Type", multipartBody.contentType())
                        .POST(multipartBody.bodyPublisher(streamSupplier))
                        .uri(new URI(requestURL))
                        .build();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("> POST {} {}", request.uri().getRawPath(), request.version().orElse(HttpClient.Version.HTTP_2));
            LOGGER.debug("> Host: {}:{}", request.uri().getHost(), request.uri().getPort());
            promptHeaders("> ", request.headers());
            multipartBody.prompt();
        }
        return request;
    }

    <T> HttpResponse<T> send(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws Exception {
        HttpResponse<T> response = client.send(request, bodyHandler);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("< {} response code: {}", response.version(), response.statusCode());
            promptHeaders("< ", response.headers());
        }
        return response;
    }

    private static void promptHeaders(String prefix, HttpHeaders headers) {
        headers.map().forEach((k, v) -> v.stream().forEach(v1 -> LOGGER.debug("{} {}: {}", prefix, k, v1)));
        LOGGER.debug(prefix);
    }

    @Override
    public void close() throws Exception {

    }

    public ContentType getContentType() {
        return contentType;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isPhoto() {
        return photo;
    }

    public void uploadDicom(Path path) throws IOException {
        multipartBody.reset();
        Payload playload = new Payload() {
            @Override
            public long size() {
                return -1;
            }

            @Override
            public ByteBuffer newByteBuffer() {
                return ByteBuffer.wrap(new byte[] {});
            }

            @Override
            public InputStream newInputStream() {
                try {
                    return Files.newInputStream(path);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return new ByteArrayInputStream(new byte[] {});
            }
        };

        try {
            HttpRequest request = buildConnection(playload, () -> new SequenceInputStream(multipartBody.enumeration()));
            send(client, request, HttpResponse.BodyHandlers.ofLines()).body().forEach(LOGGER::info);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void uploadDicom(InputStream in, DicomObject fmi) throws IOException {
        multipartBody.reset();
        Payload playload = new Payload() {
            @Override
            public long size() {
                return -1;
            }

            @Override
            public ByteBuffer newByteBuffer() {
                return ByteBuffer.wrap(new byte[] {});
            }

            @Override
            public InputStream newInputStream() {
                List<InputStream> list = new ArrayList<>();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DicomOutputStream dos = new DicomOutputStream(out)) {
                    dos.writeFileMetaInformation(fmi);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                list.add(new ByteArrayInputStream(out.toByteArray()));
                list.add(in);
                return new SequenceInputStream(Collections.enumeration(list));
            }
        };

        try {
            HttpRequest request = buildConnection(playload, () -> new SequenceInputStream(multipartBody.enumeration()));
            send(client, request, HttpResponse.BodyHandlers.ofLines()).body().forEach(LOGGER::info);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void uploadDicom(DicomObject metadata, String tsuid) throws IOException {
        multipartBody.reset();
        Payload playload = new Payload() {
            @Override
            public long size() {
                return -1;
            }

            @Override
            public ByteBuffer newByteBuffer() {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DicomOutputStream dos = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
                    dos.writeDataSet(metadata);
                    dos.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return ByteBuffer.wrap(out.toByteArray());
            }

            @Override
            public InputStream newInputStream() {
//                PipedInputStream in = new PipedInputStream();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DicomOutputStream dos = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
                    dos.writeDataSet(metadata);
                    dos.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return new ByteArrayInputStream(out.toByteArray());
//                new Thread(
//                        new Runnable() {
//                            public void run() {
//                                try (PipedOutputStream out = new PipedOutputStream(in); DicomOutputStream dos = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
//                                    dos.writeDataSet(metadata);
//                                    dos.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        }
//                ).start();
//
//                return in;
            }
        };

        try {

            HttpRequest request = buildConnection(playload, () -> new SequenceInputStream(multipartBody.enumeration()));
            send(client, request, HttpResponse.BodyHandlers.ofLines()).body().forEach(LOGGER::info);


//            MultipartBody.Part part = new MultipartBody.Part(playload, ContentType.APPLICATION_DICOM.type, null);
//
//            SubmissionPublisher<ByteBuffer> publisher = new SubmissionPublisher<>();
//            publisher.subscribe(multipartBody);
//            HttpRequest request = buildConnection(publisher);
//            CompletableFuture
//                    <HttpResponse<Stream<String>>> responses = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
//            publisher.submit(ByteBuffer.wrap(multipartBody.getHeader(part)));
//            publisher.submit(part.newByteBuffer());
//            publisher.submit(ByteBuffer.wrap(multipartBody.getEnd()));
//            publisher.close();
//
//            HttpResponse<Stream<String>> response = responses.get();
//            if (LOGGER.isDebugEnabled()) {
//                LOGGER.debug("< {} response code: {}", response.version(), response.statusCode());
//                promptHeaders("< ", response.headers());
//            }
//            response.body().forEach(LOGGER::info);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
