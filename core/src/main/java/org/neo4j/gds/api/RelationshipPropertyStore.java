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
package org.neo4j.gds.api;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@ValueClass
public interface RelationshipPropertyStore {

    Map<String, RelationshipProperty> relationshipProperties();

    default RelationshipProperty get(String propertyKey) {
        return relationshipProperties().get(propertyKey);
    }

    default Set<String> keySet() {
        return relationshipProperties().keySet();
    }

    default Collection<RelationshipProperty> values() {
        return relationshipProperties().values();
    }

    default boolean containsKey(String propertyKey) {
        return relationshipProperties().containsKey(propertyKey);
    }

    @Value.Check
    default void validate() {
        if (relationshipProperties().isEmpty()) {
            throw new IllegalStateException("Relationship property store must not be empty.");
        }
    }

    static Builder builder() {
        // need to initialize with empty map due to `deferCollectionAllocation = true`
        return new Builder().relationshipProperties(Collections.emptyMap());
    }

    @org.immutables.builder.Builder.AccessibleFields
    final class Builder extends ImmutableRelationshipPropertyStore.Builder {

        public Builder putIfAbsent(String propertyKey, RelationshipProperty relationshipProperty) {
            relationshipProperties.putIfAbsent(propertyKey, relationshipProperty);
            return this;
        }
    }

}
