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

package org.apache.paimon.flink.compact;

import org.apache.paimon.append.AppendCompactCoordinator;
import org.apache.paimon.append.MultiTableAppendCompactTask;
import org.apache.paimon.catalog.CatalogLoader;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.table.BucketMode;
import org.apache.paimon.table.FileStoreTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This class is responsible for implementing the scanning logic {@link MultiTableScanBase} for the
 * table with fix single bucket such as unaware bucket table.
 */
public class MultiAppendCompactTableScan extends MultiTableScanBase<MultiTableAppendCompactTask> {

    protected transient Map<Identifier, AppendCompactCoordinator> tablesMap;

    public MultiAppendCompactTableScan(
            CatalogLoader catalogLoader,
            Pattern includingPattern,
            Pattern excludingPattern,
            Pattern databasePattern,
            boolean isStreaming,
            Map<String, String> tableOptions) {
        super(
                catalogLoader,
                includingPattern,
                excludingPattern,
                databasePattern,
                isStreaming,
                tableOptions);
        tablesMap = new HashMap<>();
    }

    @Override
    List<MultiTableAppendCompactTask> doScan() {
        // do scan and plan action, emit append-only compaction tasks.
        List<MultiTableAppendCompactTask> tasks = new ArrayList<>();
        for (Map.Entry<Identifier, AppendCompactCoordinator> tableIdAndCoordinator :
                tablesMap.entrySet()) {
            Identifier tableId = tableIdAndCoordinator.getKey();
            AppendCompactCoordinator compactionCoordinator = tableIdAndCoordinator.getValue();
            compactionCoordinator.run().stream()
                    .map(
                            task ->
                                    new MultiTableAppendCompactTask(
                                            task.partition(), task.compactBefore(), tableId))
                    .forEach(tasks::add);
        }
        return tasks;
    }

    @Override
    public boolean checkTableScanned(Identifier identifier) {
        return tablesMap.containsKey(identifier);
    }

    @Override
    public void addScanTable(FileStoreTable fileStoreTable, Identifier identifier) {
        if (fileStoreTable.bucketMode() == BucketMode.BUCKET_UNAWARE) {
            tablesMap.put(identifier, new AppendCompactCoordinator(fileStoreTable, isStreaming));
        }
    }
}
