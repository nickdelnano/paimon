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

import org.apache.paimon.fs.Path;
import org.apache.paimon.iceberg.metadata.IcebergMetadata;

import javax.annotation.Nullable;

import java.util.List;

/**
 * A delegating {@link IcebergMetadataCommitter} that commits Iceberg metadata to multiple Hive
 * targets. Each delegate is a standard {@link IcebergHiveMetadataCommitter} configured for a single
 * database/table pair.
 */
public class IcebergMultiTargetHiveMetadataCommitter implements IcebergMetadataCommitter {

    private final List<IcebergHiveMetadataCommitter> delegates;

    public IcebergMultiTargetHiveMetadataCommitter(List<IcebergHiveMetadataCommitter> delegates) {
        this.delegates = delegates;
    }

    @Override
    public String identifier() {
        return "hive";
    }

    @Override
    public void commitMetadata(Path newMetadataPath, @Nullable Path baseMetadataPath) {
        for (IcebergHiveMetadataCommitter delegate : delegates) {
            delegate.commitMetadata(newMetadataPath, baseMetadataPath);
        }
    }

    @Override
    public void commitMetadata(
            IcebergMetadata newIcebergMetadata, @Nullable IcebergMetadata baseIcebergMetadata) {
        throw new UnsupportedOperationException();
    }
}
