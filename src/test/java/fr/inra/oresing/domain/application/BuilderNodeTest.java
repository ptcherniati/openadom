package fr.inra.oresing.domain.application;

import fr.inra.oresing.domain.application.configuration.BuilderNode;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.application.configuration.Validation;
import fr.inra.oresing.persistence.JsonRowMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.junit.jupiter.api.Tag("core.config")
class BuilderNodeTest {
    final Map<String, BuilderNode> builderNodes = Arrays.stream(new JsonRowMapper<BuilderNode>().readValue("""
            [
              {
                "nodeName": "especes",
                "parent": null,
                "children": [],
                "depends": [
                    "variables"
                ],
                "order": 3
              },
              {
                "nodeName": "variables",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 2
              },
              {
                "nodeName": "type_de_sites",
                "parent": null,
                "children": [
                  "sites"
                ],
                "depends": [],
                "order": 1
              },
              {
                "nodeName": "site_theme_datatype",
                "parent": null,
                "children": [],
                "depends": [
                  "projet",
                  "sites",
                  "themes"
                ],
                "order": 1
              },
              {
                "nodeName": "variables_et_unites_par_types_de_donnees",
                "parent": null,
                "children": [],
                "depends": [
                  "variables",
                  "unites"
                ],
                "order": 2
              },
              {
                "nodeName": "valeurs_qualitative",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 3
              },
              {
                "nodeName": "sites",
                "parent": {
                  "nodeName": "type_de_sites",
                  "parent": null,
                  "children": [
                    "sites"
                  ],
                  "depends": [],
                  "order": 1
                },
                "children": [],
                "depends": [
                  "type_de_sites"
                ],
                "order": 1
              },
              {
                "nodeName": "themes",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 1
              },
              {
                "nodeName": "unites",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 2
              },
              {
                "nodeName": "projet",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 1
              },
              {
                "nodeName": "valeurs_qualitatives",
                "parent": null,
                "children": [],
                "depends": [],
                "order": 3
              },
              {
                "nodeName": "type_de_fichiers",
                "parent": null,
                "children": [],
                "depends": [],
                "order": null
              },
              {
                "nodeName": "pem",
                "parent": null,
                "children": [],
                "depends": [
                  "sites",
                  "especes",
                  "valeurs_qualitative",
                  "unites"
                ],
                "order": null
              }
            ]
            """, BuilderNode[].class)
    ).collect(Collectors.toMap(BuilderNode::nodeName, Function.identity()));

    BuilderNodeTest() throws IOException {
        super();
    }

    @Test
    public void TestInstance() {
        Assertions.assertEquals(13, builderNodes.size());
    }

    @Test
    public void TestBuildAllDepends() {
        final List<BuilderNode> withAllDepends = builderNodes.values().stream().map(node -> node.withAllDepends(builderNodes.values())).toList();
        Assertions.assertArrayEquals(
                List.of("especes", "sites", "type_de_sites", "unites", "valeurs_qualitative", "variables").toArray(),
                withAllDepends.stream()
                        .filter(n -> "pem".equals(n.nodeName()))
                        .findFirst()
                        .map(BuilderNode::depends)
                        .orElse(Set.of()).toArray()
        );
        Assertions.assertArrayEquals(
                List.of("projet", "sites", "themes", "type_de_sites").toArray(),
                withAllDepends.stream()
                        .filter(n -> "site_theme_datatype".equals(n.nodeName()))
                        .findFirst()
                        .map(BuilderNode::depends)
                        .orElse(Set.of()).toArray()
        );
    }

    @Test
    public void TestGetGetNodeLeaves() {
        final List<BuilderNode> nodeLeaves = BuilderNode.getNodeLeaves(builderNodes.values());
        Assertions.assertEquals(8, nodeLeaves.size());
        Assertions.assertArrayEquals(List.of("variables", "valeurs_qualitative", "sites", "themes", "unites", "projet", "valeurs_qualitatives", "type_de_fichiers").toArray(), nodeLeaves.stream().map(BuilderNode::nodeName).toArray());
    }
    @Test
    public void TestBuildOrderedNodes() {
        final SortedSet<Node> orderedNodes = Node.buildNode(builderNodes.values(), new Validation(null, null, null));

        Assertions.assertEquals(
                """
                        projet
                        themes
                        type_de_sites
                        unites
                        variables
                        valeurs_qualitative
                        valeurs_qualitatives
                        type_de_fichiers
                        variables_et_unites_par_types_de_donnees
                        especes
                        site_theme_datatype
                        pem""",
                orderedNodes.stream().map(Node::nodeName).collect(Collectors.joining("\n"))
        );
    }


}