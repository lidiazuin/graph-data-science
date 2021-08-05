/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.loading;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.NodeMapping;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;

import java.util.Map;

public interface NodeMappingBuilder<BUILDER extends InternalIdMappingBuilder<? extends IdMappingAllocator>> {

    NodeMapping build(
        BUILDER idMapBuilder,
        Map<NodeLabel, HugeAtomicBitSet> labelInformation,
        long highestNodeId,
        int concurrency,
        boolean checkDuplicateIds,
        AllocationTracker tracker
    );

    default Capturing capture(BUILDER idMapBuilder) {
        return ((labelInformation, highestNodeId, concurrency, checkDuplicateIds, tracker) -> this.build(
            idMapBuilder,
            labelInformation,
            highestNodeId,
            concurrency,
            checkDuplicateIds,
            tracker
        ));
    }

    interface Capturing {

        NodeMapping build(
            Map<NodeLabel, HugeAtomicBitSet> labelInformation,
            long highestNodeId,
            int concurrency,
            boolean checkDuplicateIds,
            AllocationTracker tracker
        );
    }

}