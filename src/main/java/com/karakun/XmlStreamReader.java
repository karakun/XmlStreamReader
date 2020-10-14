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
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;

import static java.util.Optional.empty;

/**
 * This Reader will examine the wrapped input stream according to
 * appendix F of the XML specification in order to guess the encoding
 * of the XML content in the stream.
 * <p/>
 * If the encoding cannot be guessed the reader falls back to the {@code fallbackEncoding}.
 * <p/>
 * The following aspects of the input stream are examined in the order below
 * <ol>
 * <li>Byte order mark (BOM)</li>
 * <li>First 20 byes to see if content starts with "&lt;?xml"</li>
 * <li>XML encoding declaration</li>
 * <li>Fallback encoding</li>
 * </ol>
 * <p/>
 * For details see:<br/>
 * <a href="https://www.w3.org/TR/xml/#sec-guessing">XML - Appendix F</a>
 */
public class XmlStreamReader extends BomStreamReader {

    // "<?xml" byte arrays for detecting encodings
    //      < -- 0x3C
    //      ? -- 0x3F
    //      x -- 0x78
    //      m -- 0x6D
    //      l -- 0x6C

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

    // constants

    private static final int MAX_CHARS = 80;
    private static final int BUFFER_SIZE = MAX_CHARS * 4;

    /**
     * Constructor with UTF-8 as the fallback encoding.
     * This constructor is equivalent to calling:<br/>
     * {@code new XmlStreamReader(in, StandardCharsets.UTF_8)}
     *
     * @param in an input stream with XML content.
     * @throws IOException if reading from the stream failed
     */
    public XmlStreamReader(final InputStream in) throws IOException {
        this(in, StandardCharsets.UTF_8);
    }

    /**
     * Constructor with explicit in fallback encoding.
     *
     * @param in               an input stream with XML content.
     * @param fallbackEncoding the encoding to use if no encoding
     *                         can be derived from the content of the stream
     * @throws IOException if reading from the stream failed
     */
    public XmlStreamReader(final InputStream in, final Charset fallbackEncoding) throws IOException {
        super(in, fallbackEncoding, BUFFER_SIZE);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will peek into the content of the stream to further detect the encoding.
     */
    @Override
    protected Charset detectEncoding(final PushBackInputStreamWithSize pin, final Optional<Charset> fromBom, final Charset fallbackEncoding) throws IOException {
        if (pin.getBufferSize() < BUFFER_SIZE) {
            throw new IllegalStateException("Buffer fits only " + pin.getBufferSize() + " bytes but " + BUFFER_SIZE + " are required");
        }

        final Optional<Charset> fromXML = detectFromXml(pin);
        final Charset guessedEncoding = fromBom.orElseGet(() -> fromXML.orElse(fallbackEncoding));

        final Optional<Charset> fromXmlTag = readOutOfXmlTag(pin, guessedEncoding);
        return fromXmlTag.orElse(guessedEncoding);
    }

    /**
     * Reads the first 20 bytes of the input stream and detects if the bytes resemble the characters
     * {@code "<?xml"} in a unicode encoding.
     * If a match is found the corresponding {@link Charset} is returned.
     * The content in the stream is left unchanged for further processing.
     *
     * @param pin the input stream
     * @return the detected character set or empty if no {@code "<?xml"} was found
     * @throws IOException if reading or un-reading from the stream failed
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
     * Tries to read the encoding from the encoding tag in an XML (<?xml version="1.0" encoding="UTF-8"?>).
     * <p/>
     * Reads the first {@code BUFFER_SIZE} bytes and converts it to a string using the {@code guessedEncoding}.
     * The resulting string is searched for an XML encoding tag. If found the corresponding character set is returned.
     * The content in the stream is left unchanged for further processing.
     *
     * @param pin             the input stream
     * @param guessedEncoding the encoding guessed by analyzing BOM and the first 20 bytes.
     * @return the encoding found in the XML tag or empty
     * @throws IOException if reading or un-reading from the stream failed
     */
    private Optional<Charset> readOutOfXmlTag(final PushbackInputStream pin, final Charset guessedEncoding) throws IOException {
        final byte[] firstChars = new byte[BUFFER_SIZE];
        final int read = pin.read(firstChars);

        if (read < 0) {
            // nothing read
            return empty();
        }

        pin.unread(firstChars, 0, read);

        final String beginningOfXml = new String(firstChars, guessedEncoding);
        final int endStartTag = beginningOfXml.indexOf("?>");
        if (beginningOfXml.startsWith("<?xml") && endStartTag > 0) {
            final String startTag = beginningOfXml.substring(0, endStartTag + 2);
            final int beginOfEncoding = startTag.indexOf("encoding=\"");
            if (beginOfEncoding > 0) {
                final int endOfEncoding = startTag.indexOf('"', beginOfEncoding + 10);
                if (endOfEncoding > 0) {
                    final String encoding = startTag.substring(beginOfEncoding + 10, endOfEncoding);
                    return Optional.of(Charset.forName(encoding));
                }
            }
        }

        return empty();
    }
}
