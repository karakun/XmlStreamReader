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
import java.io.Reader;
import java.nio.charset.Charset;

import static java.util.Objects.requireNonNull;

/**
 * This Reader will examine the wrapped input stream according to
 * appendix F of the XML specification in order to guess the encoding
 * of the XML content in the stream.
 * <p/>
 * If the encoding cannot be guessed the reader fall back to the defaultEncoding.
 * <p/>
 * The following aspects of the input stream are examined in the order below
 * <ol>
 *     <li>Byte order mark (BOM)</li>
 *     <li>XML encoding declaration</li>
 *     <li>Default encoding</li>
 * </ol>
 * <p/>
 * For details see:<br/>
 * <a href="https://www.w3.org/TR/xml/#sec-guessing">XML - Appendix F</a>
 */
public class BomAndXmlReader extends Reader {

    private final InputStreamReader delegate;

    /**
     * Constructor with OS dependent default encoding.
     * This constructor is equivalent to calling:<br/>
     * {@code new BomAndXmlReader(in, Charset.defaultCharset())}
     *
     * @param in an input stream with XML content.
     */
    public BomAndXmlReader(InputStream in) {
        this(in, Charset.defaultCharset());
    }

    /**
     * Constructor with OS dependent default encoding.
     * This constructor is equivalent to calling:<br/>
     * {@code new BomAndXmlReader(in, Charset.defaultCharset())}
     *
     * @param in an input stream with XML content.
     * @param defaultEncoding the encoding to use if no encoding
     *                        can be derived from the content of the stream
     */
    public BomAndXmlReader(InputStream in, Charset defaultEncoding) {
        requireNonNull(in);
        requireNonNull(defaultEncoding);

        delegate = new InputStreamReader(in, defaultEncoding);
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
     * @return The historical name of this encoding, or
     *         <code>null</code> if the stream has been closed
     *
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
    public int read(char[] cbuf, int off, int len) throws IOException {
        return delegate.read(cbuf, off, len);
    }


    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
