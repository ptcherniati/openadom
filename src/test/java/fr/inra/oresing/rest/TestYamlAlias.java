package fr.inra.oresing.rest;

import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class TestYamlAlias {
    String yaml1 = "- &flag Pomme\n" +
            "- Poire\n" +
            "- &flagada Fraise\n" +
            "- Tagada\n" +
            "- *flag\n" +
            "- *flag\n" +
            "- *flagada";
    String yaml1Resolved = "[Pomme, Poire, Fraise, Tagada, Pomme, Pomme, Fraise]\n";
    String yaml2 = "services:\n" +
            "  # Node.js gives OS info about the node (Host)\n" +
            "  nodeinfo: &function\n" +
            "    image: functions/nodeinfo:latest\n" +
            "    labels:\n" +
            "      function: \"true\"\n" +
            "    depends_on:\n" +
            "      - gateway\n" +
            "    networks:\n" +
            "      - functions\n" +
            "    environment:\n" +
            "      no_proxy: \"gateway\"\n" +
            "      https_proxy: $https_proxy\n" +
            "    deploy:\n" +
            "      placement:\n" +
            "        constraints:\n" +
            "          - 'node.platform.os == linux'\n" +
            "  # Uses `cat` to echo back response, fastest function to execute.\n" +
            "  echoit:\n" +
            "    <<: *function\n" +
            "    tutu: functions/alpine:health\n" +
            "    image: functions/alpine:health\n" +
            "    environment:\n" +
            "      fprocess: \"cat\"\n" +
            "      no_proxy: \"gateway\"\n" +
            "      https_proxy: $https_proxy\n";
    String yaml2Resolved = "services:\n" +
            "  nodeinfo:\n" +
            "    image: functions/nodeinfo:latest\n" +
            "    labels: &id001 {function: 'true'}\n" +
            "    depends_on: &id002 [gateway]\n" +
            "    networks: &id003 [functions]\n" +
            "    environment: {no_proxy: gateway, https_proxy: $https_proxy}\n" +
            "    deploy: &id004\n" +
            "      placement:\n" +
            "        constraints: [node.platform.os == linux]\n" +
            "  echoit:\n" +
            "    image: functions/alpine:health\n" +
            "    labels: *id001\n" +
            "    depends_on: *id002\n" +
            "    networks: *id003\n" +
            "    environment: {fprocess: cat, no_proxy: gateway, https_proxy: $https_proxy}\n" +
            "    deploy: *id004\n" +
            "    tutu: functions/alpine:health\n";

    @Test
    public void testAliasOfAValue() {
            Yaml snakeYaml = new Yaml();
            final String dump = snakeYaml.dump(snakeYaml.load(yaml1));
            // on transforme le yaml en objet puis l'objet en yaml
            System.out.println(dump);
        Assert.assertEquals(yaml1Resolved, dump);
    }

    @Test
    public void testAliasOfAnObjectWithOverload() {
            Yaml snakeYaml = new Yaml();
            final String dump = snakeYaml.dump(snakeYaml.load(yaml2));
            // on transforme le yaml en objet puis l'objet en yaml
            System.out.println(dump);
        Assert.assertEquals(yaml2Resolved, dump);
    }
}