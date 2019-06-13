/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright 2019 the original author or authors.
 */

package com.karakun;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

/**
 * This Reader will examine the wrapped input stream for byte order marker in order to
 * guess the encoding of the content in the stream.
 * <p/>
 * If the encoding cannot be guessed the reader falls back to the {@code defaultEncoding}.
 * <p/>
 * Subclasses can override the methdo {@link #detectEncoding(PushBackInputStreamWithSize, Optional, Charset)}
 * to provide further detection methods besides the byte order marker.
 */
public class BomStreamReader extends Reader {

    // '\uFEFF' (byte order marker) byte arrays for detecting encodings

    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    static final byte[] UTF32LE_BOM = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00};
    static final byte[] UTF32BE_BOM = {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF};

    static final byte[] BROKEN_UTF32LE_BOM = {(byte) 0xFE, (byte) 0xFF, (byte) 0x00, (byte) 0x00};
    static final byte[] BROKEN_UTF32BE_BOM = {(byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFE};

    // names of encodings not found in java.nio.StandardCharsets

    static final String UTF32LE_NAME = "UTF-32LE";
    static final String UTF32BE_NAME = "UTF-32BE";

    // constants

    private static final int BUFFER_SIZE = 4;

    // once the encoding has been detected all reading is delegated to this input stream reader.
    private final InputStreamReader delegate;

    /**
     * Constructor with passed in default encoding.
     *
     * @param in              an input stream with XML content.
     * @param defaultEncoding the encoding to use if no encoding
     *                        can be derived from the content of the stream
     * @throws IOException if reading from the stream failed
     */
    public BomStreamReader(final InputStream in, final Charset defaultEncoding) throws IOException {
        this(in, defaultEncoding, BUFFER_SIZE);
    }

    /**
     * Constructor with passed in default encoding.
     *
     * @param in              an input stream with XML content.
     * @param defaultEncoding the encoding to use if no encoding
     *                        can be derived from the content of the stream
     * @param bufferSize      the number of bytes to allocate for the {@link PushBackInputStreamWithSize}
     * @throws IOException if reading from the stream failed
     */
    protected BomStreamReader(final InputStream in, final Charset defaultEncoding, int bufferSize) throws IOException {
        requireNonNull(in, "input stream");
        requireNonNull(defaultEncoding, "default encoding");

        final PushBackInputStreamWithSize pin = new PushBackInputStreamWithSize(in, bufferSize);

        final Optional<Charset> fromBom = detectFromBom(pin);
        final Charset encoding = detectEncoding(pin, fromBom, defaultEncoding);

        requireNonNull(encoding, "encoding");

        delegate = new InputStreamReader(pin, encoding);
    }

    /**
     * Determines the final encoding used by the reader.
     * <p/>
     * This method is meant to be overridden by subclasses in order to
     * provide special logic in determining the encoding.
     *
     * @param pin             the input stream to read data from without a BOM (if it was present).
     *                        Be sure to push back any data which should be available to the consumer of the reader
     * @param fromBom         the encoding guessed by a byte order marker.
     *                        If this is {@link Optional#empty()} then no valid BOM was found
     * @param defaultEncoding the default encoding to use in case no encoding could be detected.
     * @return the final encoding which will be used by the reader. Must not be {@code null}
     * @throws IOException if reading or un-reading from the stream failed
     */
    protected Charset detectEncoding(final PushBackInputStreamWithSize pin, final Optional<Charset> fromBom, final Charset defaultEncoding) throws IOException {
        return fromBom.orElse(defaultEncoding);
    }

    /**
     * Reads the first 4 bytes of the input stream and detects any unicode byte order mark (BOM).
     * If a mark is detected the corresponding {@link Charset} is returned.
     * Any detected BOM is removed from the stream leaving only the content without the BOM for further processing.
     *
     * @param pin the input stream
     * @return the detected character set or empty if no BOM was found
     * @throws IOException if reading or un-reading from the stream failed
     */
    private Optional<Charset> detectFromBom(final PushbackInputStream pin) throws IOException {

        final byte[] potentialBom = new byte[BUFFER_SIZE];
        final int read = pin.read(potentialBom);

        if (read < 0) {
            // nothing read
            return empty();
        }

        if (startsWith(potentialBom, read, BROKEN_UTF32LE_BOM)) {
            throw new UnsupportedCharsetException("UTF-32LE - unusual ordered BOM");
        }
        if (startsWith(potentialBom, read, BROKEN_UTF32BE_BOM)) {
            throw new UnsupportedCharsetException("UTF-32BE - unusual ordered BOM");
        }
        if (startsWith(potentialBom, read, UTF32BE_BOM)) {
            return Optional.of(Charset.forName(UTF32BE_NAME));
        }
        if (startsWith(potentialBom, read, UTF32LE_BOM)) {
            return Optional.of(Charset.forName(UTF32LE_NAME));
        }
        if (startsWith(potentialBom, read, UTF8_BOM)) {
            pin.unread(potentialBom, 3, read - 3);
            return Optional.of(StandardCharsets.UTF_8);
        }
        if (startsWith(potentialBom, read, UTF16LE_BOM)) {
            pin.unread(potentialBom, 2, read - 2);
            return Optional.of(StandardCharsets.UTF_16LE);
        }
        if (startsWith(potentialBom, read, UTF16BE_BOM)) {
            pin.unread(potentialBom, 2, read - 2);
            return Optional.of(StandardCharsets.UTF_16BE);
        }

        pin.unread(potentialBom, 0, read);
        return empty();
    }

    /**
     * Detects if a given byte array starts with a prefix of bytes.
     *
     * @param bytes  the bytes to search for the prefix
     * @param len    the number of bytes in {@code bytes} which are filled
     * @param prefix the prefix to search for
     * @return true iff {@code len} is greater or equal to {@code prefix.length}
     * and the {@code prefix} is a prefix of {@code bytes}
     */
    protected final boolean startsWith(final byte[] bytes, final int len, final byte[] prefix) {
        if (len < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the name of the character encoding being used by this stream.
     *
     * <p> If the encoding has an historical name then that name is returned;
     * otherwise the encoding's canonical name is returned.
     *
     * <p> The returned name, being unique for the encoding, may differ
     * from the name passed to the constructor. This method will return
     * <code>null</code> if the stream has been closed.
     * </p>
     *
     * @return The historical name of this encoding, or
     * <code>null</code> if the stream has been closed
     * @see Charset
     */
    public String getEncoding() {
        return delegate.getEncoding();
    }

    @Override
    public boolean ready() throws IOException {
        return delegate.ready();
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        return delegate.read(cbuf, off, len);
    }


    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
