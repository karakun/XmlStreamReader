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
 * This Reader will examine the wrapped input stream according to
 * appendix F of the XML specification in order to guess the encoding
 * of the XML content in the stream.
 * <p/>
 * If the encoding cannot be guessed the reader fall back to the defaultEncoding.
 * <p/>
 * The following aspects of the input stream are examined in the order below
 * <ol>
 * <li>Byte order mark (BOM)</li>
 * <li>XML encoding declaration</li>
 * <li>Default encoding</li>
 * </ol>
 * <p/>
 * For details see:<br/>
 * <a href="https://www.w3.org/TR/xml/#sec-guessing">XML - Appendix F</a>
 */
public class BomAndXmlReader extends Reader {

    // '\uFEFF' (byte order marker) byte arrays for detecting encodings

    static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    static final byte[] UTF32LE_BOM = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00};
    static final byte[] UTF32BE_BOM = {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF};

    static final byte[] BROKEN_UTF32LE_BOM = {(byte) 0xFE, (byte) 0xFF, (byte) 0x00, (byte) 0x00};
    static final byte[] BROKEN_UTF32BE_BOM = {(byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFE};

    // "<?xml" byte arrays for detecting encodings

    private static final byte[] UTF8_FIRST_CHARS = {(byte) 0x3C, (byte) 0x3F, (byte) 0x78, (byte) 0x6D, (byte) 0x6C};

    private static final byte[] UTF16LE_FIRST_CHARS = {
            (byte) 0x3C, (byte) 0x00, (byte) 0x3F, (byte) 0x00, (byte) 0x78, (byte) 0x00,
            (byte) 0x6D, (byte) 0x00, (byte) 0x6C, (byte) 0x00};
    private static final byte[] UTF16BE_FIRST_CHARS = {
            (byte) 0x00, (byte) 0x3C, (byte) 0x00, (byte) 0x3F, (byte) 0x00, (byte) 0x78,
            (byte) 0x00, (byte) 0x6D, (byte) 0x00, (byte) 0x6C};

    private static final byte[] UTF32LE_FIRST_CHARS = {
            (byte) 0x3C, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x3F, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x78, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x6D, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x6C, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    private static final byte[] UTF32BE_FIRST_CHARS = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3C,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3F,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x78,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6D,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6C};

    private static final byte[] BROKEN_UTF32LE_FIRST_CHARS = {
            (byte) 0x00, (byte) 0x3C, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x3F, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x78, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x6D, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x6C, (byte) 0x00, (byte) 0x00};
    private static final byte[] BROKEN_UTF32BE_FIRST_CHARS = {
            (byte) 0x00, (byte) 0x00, (byte) 0x3C, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x3F, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x78, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x6D, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x6C, (byte) 0x00};

    // names of encodings not found in java.nio.StandardCharsets

    static final String UTF32LE_NAME = "UTF-32LE";
    static final String UTF32BE_NAME = "UTF-32BE";

    // constants

    private static final int BUFFER_SIZE = 5 * 4;

    // fields

    private final InputStreamReader delegate;

    /**
     * Constructor with OS dependent default encoding.
     * This constructor is equivalent to calling:<br/>
     * {@code new BomAndXmlReader(in, Charset.defaultCharset())}
     *
     * @param in an input stream with XML content.
     */
    public BomAndXmlReader(final InputStream in) throws IOException {
        this(in, Charset.defaultCharset());
    }

    /**
     * Constructor with OS dependent default encoding.
     * This constructor is equivalent to calling:<br/>
     * {@code new BomAndXmlReader(in, Charset.defaultCharset())}
     *
     * @param in              an input stream with XML content.
     * @param defaultEncoding the encoding to use if no encoding
     *                        can be derived from the content of the stream
     */
    public BomAndXmlReader(final InputStream in, final Charset defaultEncoding) throws IOException {
        requireNonNull(in);
        requireNonNull(defaultEncoding);

        final PushbackInputStream pin = new PushbackInputStream(in, BUFFER_SIZE);

        final Optional<Charset> fromBom = detectFromBom(pin);
        final Optional<Charset> fromXML = detectFromXml(pin);

        final Charset encoding = fromBom.orElseGet(() -> fromXML.orElse(defaultEncoding));

        delegate = new InputStreamReader(pin, encoding);
    }

    /**
     * Reads the first 4 bytes of the input stream and detects any unicode byte order mark (BOM).
     * If a mark is detected the corresponding {@link Charset} is returned.
     * Any detected BOM is removed from the stream leaving only the content without the BOM for further processing.
     *
     * @param pin the input stream
     * @return the detected character set or empty if no BOM was found
     * @throws IOException if a unsupported BOM was detected.
     */
    private Optional<Charset> detectFromBom(final PushbackInputStream pin) throws IOException {

        final byte[] potentialBom = new byte[4];
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
     * Reads the first 4 bytes of the input stream and detects if the bytes resemble the characters
     * {@code "<?xml"} in a unicode encoding.
     * If a mark is detected the corresponding {@link Charset} is returned.
     * The content in the stream is left unchanged for further processing.
     *
     * @param pin the input stream
     * @return the detected character set or empty if no {@code "<?xml"} was found
     * @throws IOException if a unsupported BOM was detected.
     */
    private Optional<Charset> detectFromXml(final PushbackInputStream pin) throws IOException {
        final byte[] firstChars = new byte[20];
        final int read = pin.read(firstChars);

        if (read < 0) {
            // nothing read
            return empty();
        }

        pin.unread(firstChars, 0, read);

        if (startsWith(firstChars, read, BROKEN_UTF32LE_FIRST_CHARS)) {
            throw new UnsupportedCharsetException("UTF-32LE - unusual ordered");
        }
        if (startsWith(firstChars, read, BROKEN_UTF32BE_FIRST_CHARS)) {
            throw new UnsupportedCharsetException("UTF-32BE - unusual ordered");
        }
        if (startsWith(firstChars, read, UTF32BE_FIRST_CHARS)) {
            return Optional.of(Charset.forName(UTF32BE_NAME));
        }
        if (startsWith(firstChars, read, UTF32LE_FIRST_CHARS)) {
            return Optional.of(Charset.forName(UTF32LE_NAME));
        }
        if (startsWith(firstChars, read, UTF8_FIRST_CHARS)) {
            return Optional.of(StandardCharsets.UTF_8);
        }
        if (startsWith(firstChars, read, UTF16LE_FIRST_CHARS)) {
            return Optional.of(StandardCharsets.UTF_16LE);
        }
        if (startsWith(firstChars, read, UTF16BE_FIRST_CHARS)) {
            return Optional.of(StandardCharsets.UTF_16BE);
        }

        return empty();
    }

    /**
     * Detects if a given byte array starts with a prefix of bytes.
     *
     * @param bytes the bytes to search for the prefix
     * @param len the number of bytes in {@code bytes} which are filled
     * @param prefix the prefix to search for
     * @return true iff {@code len} is greater or equal to {@code prefix.length}
     * and the {@code prefix} is a prefix of {@code bytes}
     */
    private boolean startsWith(final byte[] bytes, final int len, final byte[] prefix) {
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
     * <p> If this instance was created with the {@link
     * #BomAndXmlReader(InputStream, Charset)} constructor then the returned
     * name, being unique for the encoding, may differ from the name passed to
     * the constructor. This method will return <code>null</code> if the
     * stream has been closed.
     * </p>
     *
     * @return The historical name of this encoding, or
     * <code>null</code> if the stream has been closed
     * @see java.nio.charset.Charset
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
