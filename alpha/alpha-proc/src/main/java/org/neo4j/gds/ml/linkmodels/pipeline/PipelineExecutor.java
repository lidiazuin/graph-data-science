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
package org.neo4j.gds.ml.linkmodels.pipeline;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureExtractor;
import org.neo4j.gds.ml.linkmodels.pipeline.procedureutils.ProcedureReflection;
import org.neo4j.gds.ml.splitting.SplitRelationshipsBaseConfig;
import org.neo4j.kernel.database.NamedDatabaseId;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureExtractor.extractFeatures;

public class PipelineExecutor {
    private final PipelineModelInfo pipeline;
    private final String userName;
    private final NamedDatabaseId databaseId;
    private final BaseProc caller;
    private final String graphName;

    public PipelineExecutor(
        PipelineModelInfo pipeline,
        BaseProc caller,
        NamedDatabaseId databaseId,
        String userName,
        String graphName
    ) {
        this.pipeline = pipeline;
        this.caller = caller;
        this.userName = userName;
        this.databaseId = databaseId;
        this.graphName = graphName;
    }

    public HugeObjectArray<double[]> computeFeatures(
        Collection<NodeLabel> nodeLabels,
        RelationshipType relationshipType,
        int concurrency
    ) {
        var graph = GraphStoreCatalog.get(userName, databaseId, graphName)
            .graphStore()
            .getGraph(nodeLabels, List.of(relationshipType), Optional.empty());

        pipeline.validate(graph);

        return extractFeatures(graph, pipeline.featureSteps(), concurrency);
    }

    public LinkFeatureExtractor linkFeatureExtractor(Graph graph) {
        return LinkFeatureExtractor.of(graph, pipeline.featureSteps());
    }

    public void executeNodePropertySteps(Collection<NodeLabel> nodeLabels, RelationshipType relationshipType) {
        executeNodePropertySteps(nodeLabels, List.of(relationshipType));
    }

    public void executeNodePropertySteps(
        Collection<NodeLabel> nodeLabels,
        Collection<RelationshipType> relationshipTypes
    ) {
        for (NodePropertyStep step : pipeline.nodePropertySteps()) {
            step.execute(caller, graphName, nodeLabels, relationshipTypes);
        }
    }

    public void splitRelationships(
        GraphStore graphStore,
        List<String> relationshipTypes,
        List<String> nodeLabels,
        Optional<Long> randomSeed
    ) {
        LinkPredictionSplitConfig splitConfig = pipeline.splitConfig();
        var testComplementRelationshipType = splitConfig.testComplementRelationshipType();

        // Relationship sets: test, train, feature-input, test-complement. The nodes are always the same.
        // 1. Split base graph into test, test-complement
        //      Test also includes newly generated negative links, that were not in the base graph (and positive links).
        relationshipSplit(splitConfig.testSplit(), nodeLabels, relationshipTypes, randomSeed);

        // 2. Split test-complement into (labeled) train and feature-input.
        //      Train relationships also include newly generated negative links, that were not in the base graph (and positive links).
        relationshipSplit(splitConfig.trainSplit(), nodeLabels, List.of(testComplementRelationshipType), randomSeed);

        graphStore.deleteRelationships(RelationshipType.of(testComplementRelationshipType));
    }

    private void relationshipSplit(
        SplitRelationshipsBaseConfig splitConfig,
        List<String> nodeLabels,
        List<String> relationshipTypes,
        Optional<Long> randomSeed
    ) {
        var splitRelationshipProcConfig = new HashMap<>(splitConfig.toSplitMap()) {{
            put("nodeLabels", nodeLabels);
            put("relationshipTypes", relationshipTypes);
            randomSeed.ifPresent(seed -> put("randomSeed", seed));
        }};

        ProcedureReflection.INSTANCE.invokeProc(caller, graphName, "splitRelationships", splitRelationshipProcConfig);
    }
}