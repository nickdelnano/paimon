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

import org.apache.paimon.options.Options;
import org.apache.paimon.table.FileStoreTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Factory to create {@link IcebergHiveMetadataCommitter}. */
public class IcebergHiveMetadataCommitterFactory implements IcebergMetadataCommitterFactory {

    @Override
    public String identifier() {
        return IcebergOptions.StorageType.HIVE_CATALOG.toString();
    }

    @Override
    public IcebergMetadataCommitter create(FileStoreTable table) {
        Options options = new Options(table.options());
        String databaseConfig = options.get(IcebergOptions.METASTORE_DATABASE);
        String tableConfig = options.get(IcebergOptions.METASTORE_TABLE);

        List<String> databases = splitAndTrim(databaseConfig);
        List<String> tables = splitAndTrim(tableConfig);

        if (databases.size() <= 1 && tables.size() <= 1) {
            return new IcebergHiveMetadataCommitter(table);
        }

        if (databases.size() > 1 && tables.size() > 1 && databases.size() != tables.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "When both '%s' and '%s' specify multiple values, "
                                    + "they must have the same number of entries. Got %d databases and %d tables.",
                            IcebergOptions.METASTORE_DATABASE.key(),
                            IcebergOptions.METASTORE_TABLE.key(),
                            databases.size(),
                            tables.size()));
        }

        int count = Math.max(databases.size(), tables.size());
        List<IcebergHiveMetadataCommitter> committers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, String> overrides = new HashMap<>();
            if (!databases.isEmpty()) {
                overrides.put(
                        IcebergOptions.METASTORE_DATABASE.key(),
                        databases.get(i < databases.size() ? i : 0));
            }
            if (!tables.isEmpty()) {
                overrides.put(
                        IcebergOptions.METASTORE_TABLE.key(),
                        tables.get(i < tables.size() ? i : 0));
            }
            committers.add(new IcebergHiveMetadataCommitter(table.copy(overrides)));
        }
        return new IcebergMultiTargetHiveMetadataCommitter(committers);
    }

    static List<String> splitAndTrim(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
