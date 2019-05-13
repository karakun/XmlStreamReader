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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.Set;

import static com.karakun.BomAndXmlReader.BROKEN_UTF32BE_BOM;
import static com.karakun.BomAndXmlReader.BROKEN_UTF32LE_BOM;
import static com.karakun.BomAndXmlReader.UTF16BE_BOM;
import static com.karakun.BomAndXmlReader.UTF16LE_BOM;
import static com.karakun.BomAndXmlReader.UTF32BE_BOM;
import static com.karakun.BomAndXmlReader.UTF32BE_NAME;
import static com.karakun.BomAndXmlReader.UTF32LE_BOM;
import static com.karakun.BomAndXmlReader.UTF32LE_NAME;
import static com.karakun.BomAndXmlReader.UTF8_BOM;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link BomAndXmlReader}.
 */
class BomAndXmlReaderTest {

    private static final String BOM = Character.toString('\uFEFF');

    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

    @Test
    void constructorThrowsOnNullArgument() {
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(null)
        );
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(null, defaultCharset())
        );
        assertThrows(NullPointerException.class, () ->
                new BomAndXmlReader(EMPTY_STREAM, null)
        );
    }

    @Test
    void singleArgumentConstructorUsesLocaleCharset() throws IOException {
        // when
        final BomAndXmlReader reader = new BomAndXmlReader(EMPTY_STREAM);

        // then
        assertReaderHasExpectedEncoding(reader, defaultCharset());
    }

    @Test
    void doubleArgumentConstructorUsesPassedCharset() throws IOException {
        // given
        final Charset charset = defaultCharset().equals(UTF_8) ? ISO_8859_1 : UTF_8;

        // when
        final BomAndXmlReader reader = new BomAndXmlReader(EMPTY_STREAM, charset);

        // then
        assertReaderHasExpectedEncoding(reader, charset);
    }

    @Test
    void detectEncodingFromBom() throws IOException {
        detectEncodingFromBom(UTF8_BOM, StandardCharsets.UTF_8);
        detectEncodingFromBom(UTF16LE_BOM, StandardCharsets.UTF_16LE);
        detectEncodingFromBom(UTF16BE_BOM, StandardCharsets.UTF_16BE);
        detectEncodingFromBom(UTF32LE_BOM, Charset.forName(UTF32LE_NAME));
        detectEncodingFromBom(UTF32BE_BOM, Charset.forName(UTF32BE_NAME));
    }

    private void detectEncodingFromBom(byte[] bom, Charset charset) throws IOException {
        // when
        final BomAndXmlReader reader = new BomAndXmlReader(new ByteArrayInputStream(bom), ISO_8859_1);

        // then
        assertReaderHasExpectedEncoding(reader, charset);
    }

    @Test
    void throwsExceptionForUnsupportedEncodings() {
        assertThrows(UnsupportedCharsetException.class, () ->
                new BomAndXmlReader(new ByteArrayInputStream(BROKEN_UTF32LE_BOM))
        );
        assertThrows(UnsupportedCharsetException.class, () ->
                new BomAndXmlReader(new ByteArrayInputStream(BROKEN_UTF32BE_BOM))
        );
    }

    @Test
    void bomBytesCorrelatesToEncodings() {
        assertThat(UTF8_BOM).containsExactly(BOM.getBytes(StandardCharsets.UTF_8));
        assertThat(UTF16LE_BOM).containsExactly(BOM.getBytes(StandardCharsets.UTF_16LE));
        assertThat(UTF16BE_BOM).containsExactly(BOM.getBytes(StandardCharsets.UTF_16BE));
        assertThat(UTF32LE_BOM).containsExactly(BOM.getBytes(Charset.forName(UTF32LE_NAME)));
        assertThat(UTF32BE_BOM).containsExactly(BOM.getBytes(Charset.forName(UTF32BE_NAME)));
    }

    private void assertReaderHasExpectedEncoding(BomAndXmlReader reader, Charset charset) {
        final Set<String> aliases = new HashSet<>(charset.aliases());
        aliases.add(charset.name());
        assertThat(aliases).contains(reader.getEncoding());
    }
}
