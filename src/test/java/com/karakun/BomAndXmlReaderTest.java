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
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.nio.charset.Charset.defaultCharset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link BomAndXmlReader}.
 */
class BomAndXmlReaderTest {

    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final String DEFAULT_ENCODING = encodingName(defaultCharset());

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
    void defaultConstructorUsesLocaleCharset() {
        // given
        final BomAndXmlReader reader = new BomAndXmlReader(EMPTY_STREAM);

        // when
        final String encoding = reader.getEncoding();

        // then
        assertThat(encoding).isEqualTo(DEFAULT_ENCODING);
    }

    private static String encodingName(Charset charset) {
        return charset.name().replaceAll("[^a-zA-Z0-9]", "");
    }
}
