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
 * ...
 */
public class BomAndXmlReader extends Reader {

    private final InputStreamReader delegate;

    public BomAndXmlReader(InputStream in) {
        this(in, Charset.defaultCharset());
    }

    public BomAndXmlReader(InputStream in, Charset defaultEncoding) {
        requireNonNull(in);
        requireNonNull(defaultEncoding);

        delegate = new InputStreamReader(in, defaultEncoding);
    }

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
