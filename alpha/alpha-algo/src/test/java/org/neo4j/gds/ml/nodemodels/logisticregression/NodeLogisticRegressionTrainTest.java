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
package org.neo4j.gds.ml.nodemodels.logisticregression;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.ml.core.Dimensions.COLUMNS_INDEX;
import static org.neo4j.gds.ml.core.Dimensions.ROWS_INDEX;

@GdlExtension
class NodeLogisticRegressionTrainTest {

    @GdlGraph
    private static final String DB_QUERY =
        "CREATE " +
        "  (n1:N {a: 2.0, b: 1.2, x: 999999999.0, t: 1})" +
        ", (n2:N {a: 1.3, b: 0.5, x: -999999999.0, t: 0})" +
        ", (n3:N {a: 0.0, b: 2.8, x: -999999999.0, t: 2})" +
        ", (n4:N {a: 1.0, b: 0.9, x: 999999999.0, t: 1})";

    private static final double NO_PENALTY = 0.0;

    @Inject
    private Graph graph;

    @Test
    void shouldHandleLargeValuedFeatures() {
        var config = new NodeLogisticRegressionTrainConfigImpl(
            List.of("a", "x"),
            "t",
            CypherMapWrapper.create(Map.of("penalty", NO_PENALTY))
        );

        var nodeIds = HugeLongArray.newArray(graph.nodeCount());
        nodeIds.setAll(i -> i);
        var algo = new NodeLogisticRegressionTrain(
            graph,
            nodeIds,
            config,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            1
        );
        var result = algo.compute();

        var data = result.weights().data().data();

        assertThat(data).containsExactly(
            -4.056062949146722E-4,
            -0.0016700582679913354,
            -0.001670058020395462,
            0.0016700582044043917,
            0.0016700583328979716,
            0.0016700581372659949,
            -0.0019176488928370093,
            -0.0016700582679913354,
            -0.001670058020395462
        );
    }

    @Test
    void shouldComputeWithDefaultAdamOptimizerAndStreakStopper() {
        var config = new NodeLogisticRegressionTrainConfigImpl(
            List.of("a", "b"),
            "t",
            CypherMapWrapper.create(Map.of(
                "penalty", NO_PENALTY,
                "maxEpochs", 100000,
                "tolerance", 1e-4
            ))
        );

        var nodeIds = HugeLongArray.newArray(graph.nodeCount());
        nodeIds.setAll(i -> i);
        var algo = new NodeLogisticRegressionTrain(
            graph,
            nodeIds,
            config,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            1
        );

        var result = algo.compute();

        assertThat(result).isNotNull();

        var trainedWeights = result.weights();
        assertThat(trainedWeights.dimension(ROWS_INDEX)).isEqualTo(3);
        assertThat(trainedWeights.dimension(COLUMNS_INDEX)).isEqualTo(3);

        assertThat(trainedWeights.data().data()).containsExactly(
            new double[]{
                33.80331989615432, -35.5612283371057, 10.826705193820422,
                13.565878816092354, 35.45071542666095, -15.413572599075732,
                -29.644105601850793, 32.70379721264568, 11.382482785703171
            },
            Offset.offset(1e-8)
        );

        // check that classIdMap is strictly increasing
        long[] classes = result.classIdMap().originalIds();
        for (int i = 0; i < classes.length - 1; i++) {
            assertThat(classes[i]).isLessThan(classes[i+1]);
        }
    }

    @Test
    void shouldComputeWithDefaultAdamOptimizerAndStreakStopperConcurrently() {
        var config = new NodeLogisticRegressionTrainConfigImpl(
            List.of("a", "b"),
            "t",
            CypherMapWrapper.create(Map.of(
                "penalty", 1.0,
                "maxEpochs", 1000000,
                "tolerance", 1e-10
            ))
        );

        var nodeIds = HugeLongArray.newArray(graph.nodeCount());
        nodeIds.setAll(i -> i);
        var algo = new NodeLogisticRegressionTrain(
            graph,
            nodeIds,
            config,
            ProgressTracker.NULL_TRACKER,
            TerminationFlag.RUNNING_TRUE,
            4
        );

        var result = algo.compute();

        assertThat(result).isNotNull();

        var trainedWeights = result.weights();
        assertThat(trainedWeights.dimension(ROWS_INDEX)).isEqualTo(3);
        assertThat(trainedWeights.dimension(COLUMNS_INDEX)).isEqualTo(3);

        var trainedData = trainedWeights.data().data();
        var expectedData = new double[] {
            0.02291542809512843, -0.09769301587228878, -0.21129220750800162,
            0.0916257450149858, -0.0577815055288917, 0.35335255481509065,
            -0.11454111373490988, 0.15547180246829373, -0.42841313034448925
        };

        assertThat(trainedData).containsExactly(expectedData, Offset.offset(1e-9));
    }
}
