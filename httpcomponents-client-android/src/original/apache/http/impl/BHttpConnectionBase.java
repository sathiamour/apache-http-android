/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package original.apache.http.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import original.apache.http.Header;
import original.apache.http.HttpConnection;
import original.apache.http.HttpConnectionMetrics;
import original.apache.http.HttpEntity;
import original.apache.http.HttpException;
import original.apache.http.HttpInetConnection;
import original.apache.http.HttpMessage;
import original.apache.http.annotation.NotThreadSafe;
import original.apache.http.config.MessageConstraints;
import original.apache.http.entity.BasicHttpEntity;
import original.apache.http.entity.ContentLengthStrategy;
import original.apache.http.impl.entity.LaxContentLengthStrategyHC4;
import original.apache.http.impl.entity.StrictContentLengthStrategyHC4;
import original.apache.http.impl.io.ChunkedInputStreamHC4;
import original.apache.http.impl.io.ChunkedOutputStreamHC4;
import original.apache.http.impl.io.ContentLengthInputStreamHC4;
import original.apache.http.impl.io.ContentLengthOutputStreamHC4;
import original.apache.http.impl.io.HttpTransportMetricsImpl;
import original.apache.http.impl.io.IdentityInputStreamHC4;
import original.apache.http.impl.io.IdentityOutputStreamHC4;
import original.apache.http.impl.io.SessionInputBufferImpl;
import original.apache.http.impl.io.SessionOutputBufferImpl;
import original.apache.http.io.SessionInputBuffer;
import original.apache.http.io.SessionOutputBuffer;
import original.apache.http.protocol.HTTP;
import original.apache.http.util.Args;
import original.apache.http.util.Asserts;
import original.apache.http.util.NetUtils;

/**
 * This class serves as a base for all {@link HttpConnection} implementations and provides
 * functionality common to both client and server HTTP connections.
 *
 * @since 4.0
 */
@NotThreadSafe
public class BHttpConnectionBase implements HttpConnection, HttpInetConnection {

    private final SessionInputBufferImpl inbuffer;
    private final SessionOutputBufferImpl outbuffer;
    private final HttpConnectionMetricsImpl connMetrics;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;

    private volatile boolean open;
    private volatile Socket socket;

    /**
     * Creates new instance of BHttpConnectionBase.
     *
     * @param buffersize buffer size. Must be a positive number.
     * @param fragmentSizeHint fragment size hint.
     * @param chardecoder decoder to be used for decoding HTTP protocol elements.
     *   If <code>null</code> simple type cast will be used for byte to char conversion.
     * @param charencoder encoder to be used for encoding HTTP protocol elements.
     *   If <code>null</code> simple type cast will be used for char to byte conversion.
     * @param constraints Message constraints. If <code>null</code>
     *   {@link MessageConstraints#DEFAULT} will be used.
     * @param incomingContentStrategy incoming content length strategy. If <code>null</code>
     *   {@link LaxContentLengthStrategyHC4#INSTANCE} will be used.
     * @param outgoingContentStrategy outgoing content length strategy. If <code>null</code>
     *   {@link StrictContentLengthStrategyHC4#INSTANCE} will be used.
     */
    protected BHttpConnectionBase(
            final int buffersize,
            final int fragmentSizeHint,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy) {
        super();
        Args.positive(buffersize, "Buffer size");
        final HttpTransportMetricsImpl inTransportMetrics = new HttpTransportMetricsImpl();
        final HttpTransportMetricsImpl outTransportMetrics = new HttpTransportMetricsImpl();
        this.inbuffer = new SessionInputBufferImpl(inTransportMetrics, buffersize, -1,
                constraints != null ? constraints : MessageConstraints.DEFAULT, chardecoder);
        this.outbuffer = new SessionOutputBufferImpl(outTransportMetrics, buffersize, fragmentSizeHint,
                charencoder);
        this.connMetrics = new HttpConnectionMetricsImpl(inTransportMetrics, outTransportMetrics);
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
            LaxContentLengthStrategyHC4.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
            StrictContentLengthStrategyHC4.INSTANCE;
    }

    protected void ensureOpen() throws IOException {
        Asserts.check(this.open, "Connection is not open");
        if (!this.inbuffer.isBound()) {
            this.inbuffer.bind(getSocketInputStream(this.socket));
        }
        if (!this.outbuffer.isBound()) {
            this.outbuffer.bind(getSocketOutputStream(this.socket));
        }
    }

    protected InputStream getSocketInputStream(final Socket socket) throws IOException {
        return socket.getInputStream();
    }

    protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
        return socket.getOutputStream();
    }

    /**
     * Binds this connection to the given {@link Socket}. This socket will be
     * used by the connection to send and receive data.
     * <p/>
     * After this method's execution the connection status will be reported
     * as open and the {@link #isOpen()} will return <code>true</code>.
     *
     * @param socket the socket.
     * @throws IOException in case of an I/O error.
     */
    protected void bind(final Socket socket) throws IOException {
        Args.notNull(socket, "Socket");
        this.socket = socket;
        this.open = true;
        this.inbuffer.bind(null);
        this.outbuffer.bind(null);
    }

    protected SessionInputBuffer getSessionInputBuffer() {
        return this.inbuffer;
    }

    protected SessionOutputBuffer getSessionOutputBuffer() {
        return this.outbuffer;
    }

    protected void doFlush() throws IOException {
        this.outbuffer.flush();
    }

    public boolean isOpen() {
        return this.open;
    }

    protected Socket getSocket() {
        return this.socket;
    }

    protected OutputStream createOutputStream(
            final long len,
            final SessionOutputBuffer outbuffer) {
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedOutputStreamHC4(2048, outbuffer);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityOutputStreamHC4(outbuffer);
        } else {
            return new ContentLengthOutputStreamHC4(outbuffer, len);
        }
    }

    protected OutputStream prepareOutput(final HttpMessage message) throws HttpException {
        final long len = this.outgoingContentStrategy.determineLength(message);
        return createOutputStream(len, this.outbuffer);
    }

    protected InputStream createInputStream(
            final long len,
            final SessionInputBuffer inbuffer) {
        if (len == ContentLengthStrategy.CHUNKED) {
            return new ChunkedInputStreamHC4(inbuffer);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            return new IdentityInputStreamHC4(inbuffer);
        } else {
            return new ContentLengthInputStreamHC4(inbuffer, len);
        }
    }

    protected HttpEntity prepareInput(final HttpMessage message) throws HttpException {
        final BasicHttpEntity entity = new BasicHttpEntity();

        final long len = this.incomingContentStrategy.determineLength(message);
        final InputStream instream = createInputStream(len, this.inbuffer);
        if (len == ContentLengthStrategy.CHUNKED) {
            entity.setChunked(true);
            entity.setContentLength(-1);
            entity.setContent(instream);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            entity.setChunked(false);
            entity.setContentLength(-1);
            entity.setContent(instream);
        } else {
            entity.setChunked(false);
            entity.setContentLength(len);
            entity.setContent(instream);
        }

        final Header contentTypeHeader = message.getFirstHeader(HTTP.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            entity.setContentType(contentTypeHeader);
        }
        final Header contentEncodingHeader = message.getFirstHeader(HTTP.CONTENT_ENCODING);
        if (contentEncodingHeader != null) {
            entity.setContentEncoding(contentEncodingHeader);
        }
        return entity;
    }

    public InetAddress getLocalAddress() {
        if (this.socket != null) {
            return this.socket.getLocalAddress();
        } else {
            return null;
        }
    }

    public int getLocalPort() {
        if (this.socket != null) {
            return this.socket.getLocalPort();
        } else {
            return -1;
        }
    }

    public InetAddress getRemoteAddress() {
        if (this.socket != null) {
            return this.socket.getInetAddress();
        } else {
            return null;
        }
    }

    public int getRemotePort() {
        if (this.socket != null) {
            return this.socket.getPort();
        } else {
            return -1;
        }
    }

    public void setSocketTimeout(final int timeout) {
        if (this.socket != null) {
            try {
                this.socket.setSoTimeout(timeout);
            } catch (final SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    public int getSocketTimeout() {
        if (this.socket != null) {
            try {
                return this.socket.getSoTimeout();
            } catch (final SocketException ignore) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public void shutdown() throws IOException {
        this.open = false;
        final Socket tmpsocket = this.socket;
        if (tmpsocket != null) {
            tmpsocket.close();
        }
    }

    public void close() throws IOException {
        if (!this.open) {
            return;
        }
        this.open = false;
        final Socket sock = this.socket;
        try {
            this.inbuffer.clear();
            this.outbuffer.flush();
            try {
                try {
                    sock.shutdownOutput();
                } catch (final IOException ignore) {
                }
                try {
                    sock.shutdownInput();
                } catch (final IOException ignore) {
                }
            } catch (final UnsupportedOperationException ignore) {
                // if one isn't supported, the other one isn't either
            }
        } finally {
            sock.close();
        }
    }

    private int fillInputBuffer(final int timeout) throws IOException {
        final int oldtimeout = this.socket.getSoTimeout();
        try {
            this.socket.setSoTimeout(timeout);
            return this.inbuffer.fillBuffer();
        } finally {
            this.socket.setSoTimeout(oldtimeout);
        }
    }

    protected boolean awaitInput(final int timeout) throws IOException {
        if (this.inbuffer.hasBufferedData()) {
            return true;
        }
        fillInputBuffer(timeout);
        return this.inbuffer.hasBufferedData();
    }

    public boolean isStale() {
        if (!isOpen()) {
            return true;
        }
        try {
            final int bytesRead = fillInputBuffer(1);
            return bytesRead < 0;
        } catch (final SocketTimeoutException ex) {
            return false;
        } catch (final IOException ex) {
            return true;
        }
    }

    protected void incrementRequestCount() {
        this.connMetrics.incrementRequestCount();
    }

    protected void incrementResponseCount() {
        this.connMetrics.incrementResponseCount();
    }

    public HttpConnectionMetrics getMetrics() {
        return this.connMetrics;
    }

    @Override
    public String toString() {
        if (this.socket != null) {
            final StringBuilder buffer = new StringBuilder();
            final SocketAddress remoteAddress = this.socket.getRemoteSocketAddress();
            final SocketAddress localAddress = this.socket.getLocalSocketAddress();
            if (remoteAddress != null && localAddress != null) {
                NetUtils.formatAddress(buffer, localAddress);
                buffer.append("<->");
                NetUtils.formatAddress(buffer, remoteAddress);
            }
            return buffer.toString();
        } else {
            return "[Not bound]";
        }
    }

}
