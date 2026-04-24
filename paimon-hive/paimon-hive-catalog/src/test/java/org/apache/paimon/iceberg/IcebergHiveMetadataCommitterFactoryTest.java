/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.iceberg;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link IcebergHiveMetadataCommitterFactory}. */
public class IcebergHiveMetadataCommitterFactoryTest {

    @Test
    public void testIdentifier() {
        IcebergHiveMetadataCommitterFactory factory = new IcebergHiveMetadataCommitterFactory();
        assertThat(factory.identifier()).isEqualTo("hive-catalog");
    }

    @Test
    public void testSplitAndTrimNull() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim(null);
        assertThat(result).isEmpty();
    }

    @Test
    public void testSplitAndTrimEmpty() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("");
        assertThat(result).isEmpty();
    }

    @Test
    public void testSplitAndTrimSingleValue() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("mydb");
        assertThat(result).containsExactly("mydb");
    }

    @Test
    public void testSplitAndTrimMultipleValues() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("db1;db2;db3");
        assertThat(result).containsExactly("db1", "db2", "db3");
    }

    @Test
    public void testSplitAndTrimWhitespace() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim(" db1 ; db2 ; db3 ");
        assertThat(result).containsExactly("db1", "db2", "db3");
    }

    @Test
    public void testSplitAndTrimEmptySegments() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("db1;;db2");
        assertThat(result).containsExactly("db1", "db2");
    }

    @Test
    public void testSplitAndTrimTrailingSemicolon() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("db1;db2;");
        assertThat(result).containsExactly("db1", "db2");
    }

    @Test
    public void testSplitAndTrimWhitespaceOnlySegments() {
        List<String> result = IcebergHiveMetadataCommitterFactory.splitAndTrim("db1; ;db2");
        assertThat(result).containsExactly("db1", "db2");
    }
}
