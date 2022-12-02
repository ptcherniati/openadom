package fr.inra.oresing.rest;

import lombok.Getter;
import lombok.Setter;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.List;

public class TestYamlAlias {

    @Autowired
    private ApplicationConfigurationService service;
    static String yaml1 = "\n" +
            "defaults: &defaults\n" +
            "  A: 1\n" +
            "  B: 2\n" +
            "mapping:\n" +
            "  << : *defaults\n" +
            "  A: 23\n" +
            "  C: 99\n" +
            "légumes : [chou, carotte, poireaux]\n" +
            "fruits: \n" +
            "  - &flag Pomme\n" +
            "  - Poire\n" +
            "  - &flagada Fraise\n" +
            "  - Tagada\n" +
            "  - *flag\n" +
            "  - *flag\n" +
            "  - *flagada";
    static String yaml1Resolved = "defaults: {A: 1, B: 2}\n" +
            "mapping: {A: 23, B: 2, C: 99}\n" +
            "légumes: [chou, carotte, poireaux]\n" +
            "fruits: [Pomme, Poire, Fraise, Tagada, Pomme, Pomme, Fraise]\n";
    static String yaml2 = "services:\n" +
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
    static String yaml2Resolved = "services:\n" +
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
    static String fakeYaml = "version: 1\n" +
            "application:\n" +
            "  name: Sites\n" +
            "  version: 1\n" +
            "  internationalizationName:\n" +
            "    fr: Fausse application\n" +
            "    en: Fake application\n" +
            "compositeReferences:\n" +
            "  localizations:\n" +
            "    components:\n" +
            "      - reference: typeSites\n" +
            "      - parentKeyColumn: \"nom du type de site\"\n" +
            "        reference: sites\n" +
            "      - parentKeyColumn: \"nom du site\"\n" +
            "        reference: plateformes\n" +
            "  taxon:\n" +
            "    components:\n" +
            "      - parentRecursiveKey: \"nom du taxon superieur\"\n" +
            "        reference: taxon\n" +
            "references:\n" +
            "  projets:\n" +
            "    separator:\n" +
            "    keyColumns: [nom du projet_key]\n" +
            "    internationalizationName:\n" +
            "      fr: projet\n" +
            "      en: Project\n" +
            "    internationalizedColumns:\n" +
            "      nom du projet_key:\n" +
            "        fr: nom du projet_fr\n" +
            "        en: nom du projet_en\n" +
            "    internationalizationDisplay:\n" +
            "      pattern:\n" +
            "        fr: '{nom du projet_fr}'\n" +
            "        en: '{nom du projet_en}'\n" +
            "    columns:\n" +
            "      nom du projet_key:\n" +
            "      nom du projet_fr:\n" +
            "      nom du projet_en:\n" +
            "      description du projet_fr:\n" +
            "      description du projet_en:\n" +
            "  platform_type:\n" +
            "    internationalizationName:\n" +
            "      fr: Types de plateforme\n" +
            "      en: Plateform types\n" +
            "    internationalizedColumns:\n" +
            "      nom du type de plateforme_key:\n" +
            "        fr: nom du type de plateforme_fr\n" +
            "        en: nom du type de plateforme_en\n" +
            "      description_fr:\n" +
            "        fr: description_fr\n" +
            "        en: description_en\n" +
            "    internationalizationDisplay:\n" +
            "      pattern:\n" +
            "        fr: '{nom du type de plateforme_key}'\n" +
            "        en: '{nom du type de plateforme_key}'\n" +
            "    keyColumns:\n" +
            "      - nom du type de plateforme_key\n" +
            "    columns:\n" +
            "      nom du type de plateforme_key: null\n" +
            "      nom du type de plateforme_fr: null\n" +
            "      nom du type de plateforme_en: null\n" +
            "      description_fr: null\n" +
            "      description_en: null\n" +
            "      code sandre: null\n" +
            "      code sandre du contexte: null\n" +
            "  plateformes:\n" +
            "    separator:\n" +
            "    keyColumns: [nom de la plateforme_key]\n" +
            "    columns:\n" +
            "      nom de la plateforme_key:\n" +
            "      nom du site:\n" +
            "      nom de la plateforme_fr:\n" +
            "      nom de la plateforme_en:\n" +
            "      latitude:\n" +
            "      longitude:\n" +
            "      altitude:\n" +
            "      nom du type de plateforme:\n" +
            "        checker:\n" +
            "          name: Reference\n" +
            "          params:\n" +
            "            refType: platform_type\n" +
            "            required: true\n" +
            "            transformation:\n" +
            "              codify: true\n" +
            "      code sandre:\n" +
            "      code sandre du contexte:\n" +
            "  typeSites:\n" +
            "    separator:\n" +
            "    keyColumns: [nom_key]\n" +
            "    columns:\n" +
            "      nom_key:\n" +
            "      nom_fr:\n" +
            "      nom_en:\n" +
            "      description_fr:\n" +
            "      description_en:\n" +
            "  sites:\n" +
            "    internationalizationName:\n" +
            "      fr: Site\n" +
            "      en: Site\n" +
            "    internationalizedColumns:\n" +
            "      nom du site_key:\n" +
            "        fr: nom du site_fr\n" +
            "        en: nom du site_en\n" +
            "      description du site_fr:\n" +
            "        fr: description du site_fr\n" +
            "        en: description du site_en\n" +
            "    internationalizationDisplay:\n" +
            "      pattern:\n" +
            "        fr: '{nom du site_fr}'\n" +
            "        en: '{nom du site_en}'\n" +
            "    separator:\n" +
            "    keyColumns: [nom du site_key]\n" +
            "    columns:\n" +
            "      nom du type de site:\n" +
            "      nom du site_key:\n" +
            "      nom du site_fr:\n" +
            "      nom du site_en:\n" +
            "      description du site_fr:\n" +
            "      description du site_en:\n" +
            "      code sandre du Plan d'eau:\n" +
            "      code sandre de la Masse d'eau plan d'eau:\n" +
            "  units:\n" +
            "    keyColumns: [name]\n" +
            "    columns:\n" +
            "      name:\n" +
            "  proprietes_taxon:\n" +
            "    validations:\n" +
            "      floats:\n" +
            "        internationalizationName:\n" +
            "          fr: les décimaux\n" +
            "        columns: [ isFloatValue ]\n" +
            "        checker:\n" +
            "          name: Float\n" +
            "      integer:\n" +
            "        internationalizationName:\n" +
            "          fr: les entiers\n" +
            "        columns: [ ordre d'affichage ]\n" +
            "        checker:\n" +
            "          name: Integer\n" +
            "    internationalizationName:\n" +
            "      fr: Proprétés de Taxon\n" +
            "      en: Properties of Taxa\n" +
            "    internationalizedColumns:\n" +
            "      nom de la propriété_key:\n" +
            "        fr: nom de la propriété_fr\n" +
            "        en: nom de la propriété_en\n" +
            "      définition_fr:\n" +
            "        fr: définition_fr\n" +
            "        en: définition_en\n" +
            "    internationalizationDisplay:\n" +
            "      pattern:\n" +
            "        fr: '{nom de la propriété_key}'\n" +
            "        en: '{nom de la propriété_key}'\n" +
            "    keyColumns: [nom de la propriété_key]\n" +
            "    columns:\n" +
            "      Date:\n" +
            "        checker:\n" +
            "          name: Date\n" +
            "          params:\n" +
            "            pattern: dd/MM/yyyy\n" +
            "            duration: \"1 MINUTES\"\n" +
            "            required: true\n" +
            "      nom de la propriété_key:\n" +
            "      nom de la propriété_fr:\n" +
            "      nom de la propriété_en:\n" +
            "      définition_fr:\n" +
            "      définition_en:\n" +
            "      isFloatValue:\n" +
            "      isQualitative:\n" +
            "      type associé:\n" +
            "      ordre d'affichage:\n" +
            "  taxon:\n" +
            "    internationalizationName:\n" +
            "      fr: Taxons\n" +
            "      en: Taxa\n" +
            "    internationalizationDisplay:\n" +
            "      pattern:\n" +
            "        fr: '{nom du taxon déterminé}'\n" +
            "        en: '{nom du taxon déterminé}'\n" +
            "    keyColumns: [nom du taxon déterminé]\n" +
            "    validations:\n" +
            "      nom du taxon déterminé:\n" +
            "        internationalizationName:\n" +
            "          fr: \"nom du taxon déterminé\"\n" +
            "        checker:\n" +
            "          name: RegularExpression\n" +
            "          params:\n" +
            "            pattern: .*\n" +
            "            required: true\n" +
            "            transformation:\n" +
            "              codify: true\n" +
            "        columns: [ nom du taxon déterminé ]\n" +
            "      nom du taxon superieur:\n" +
            "        internationalizationName:\n" +
            "          fr: \"nom du taxon superieur\"\n" +
            "        checker:\n" +
            "          name: Reference\n" +
            "          params:\n" +
            "            required: false\n" +
            "            transformation:\n" +
            "              codify: true\n" +
            "            refType: taxon\n" +
            "        columns: [ nom du taxon superieur ]\n" +
            "    columns:\n" +
            "      nom du taxon déterminé:\n" +
            "      theme:\n" +
            "      nom du niveau de taxon:\n" +
            "      nom du taxon superieur:\n" +
            "      code sandre du taxon:\n" +
            "      code sandre du taxon supérieur:\n" +
            "      niveau incertitude de détermination:\n" +
            "      Auteur de la description:\n" +
            "      Année de la description:\n" +
            "      Référence de la description:\n" +
            "      Références relatives à ce taxon:\n" +
            "      Synonyme ancien:\n" +
            "      Synonyme récent:\n" +
            "      Classe algale sensu Bourrelly:\n" +
            "      Code Sandre:\n" +
            "      Notes libres:\n" +
            "    dynamicColumns:\n" +
            "      propriétés de taxons:\n" +
            "        internationalizationName:\n" +
            "          fr: Proprétés de Taxons\n" +
            "          en: Properties of Taxa\n" +
            "        headerPrefix: \"pt_\"\n" +
            "        reference: proprietes_taxon\n" +
            "        referenceColumnToLookForHeader: nom de la propriété_key\n" +
            "dataTypes:\n" +
            "  site:\n" +
            "    internationalizationName:\n" +
            "      fr: Le site\n" +
            "      en: the good place\n" +
            "    internationalizationDisplays:\n" +
            "      sites:\n" +
            "          pattern:\n" +
            "            fr: 'le nom du site {nom du site_fr}'\n" +
            "            en: 'the very good place {nom du site_en}'\n" +
            "    authorization:\n" +
            "      dataGroups:\n" +
            "        referentiel:\n" +
            "          label: \"Référentiel\"\n" +
            "          data:\n" +
            "            - localization\n" +
            "            - date\n" +
            "        qualitatif:\n" +
            "          label: \"Données qualitatives\"\n" +
            "          data:\n" +
            "            - Couleur des individus\n" +
            "            - Nombre d'individus\n" +
            "      authorizationScopes:\n" +
            "        localization:\n" +
            "          variable: localization\n" +
            "          component: site\n" +
            "      timeScope:\n" +
            "        variable: date\n" +
            "        component: day\n" +
            "    uniqueness:\n" +
            "      - variable: date\n" +
            "        component: day\n" +
            "      - variable: date\n" +
            "        component: time\n" +
            "      - variable: localization\n" +
            "        component: site\n" +
            "    data:\n" +
            "      date:\n" +
            "        components:\n" +
            "          day:\n" +
            "            checker:\n" +
            "              name: Date\n" +
            "              params:\n" +
            "                pattern: dd/MM/yyyy\n" +
            "          time:\n" +
            "            checker:\n" +
            "              name: Date\n" +
            "              params:\n" +
            "                pattern: HH:mm:ss\n" +
            "          datetime:\n" +
            "            defaultValue:\n" +
            "              expression: >\n" +
            "                return datum.date.day +\" \" +datum.date.time+ \":00\"\n" +
            "            checker:\n" +
            "              name: Date\n" +
            "              params:\n" +
            "                pattern: \"dd/MM/yyyy HH:mm:ss\"\n" +
            "                duration: \"1 MINUTES\"\n" +
            "      localization:\n" +
            "        components:\n" +
            "          site:\n" +
            "            checker:\n" +
            "              name: Reference\n" +
            "              params:\n" +
            "                refType: sites\n" +
            "          typeSite:\n" +
            "            checker:\n" +
            "              name: Reference\n" +
            "              params:\n" +
            "                refType: typeSites\n" +
            "      Couleur des individus:\n" +
            "        components:\n" +
            "          value:\n" +
            "      Nombre d'individus:\n" +
            "        chartDescription:\n" +
            "          value: \"value\"\n" +
            "          aggregation:\n" +
            "            variable: Couleur des individus\n" +
            "            component: value\n" +
            "          unit: \"unit\"\n" +
            "          standardDeviation: \"standardDeviation\"\n" +
            "        components:\n" +
            "          value:\n" +
            "          unit:\n" +
            "            checker:\n" +
            "              name: Reference\n" +
            "              params:\n" +
            "                refType: units\n" +
            "                transformation:\n" +
            "                  codify: true\n" +
            "          standardDeviation:\n" +
            "    validations:\n" +
            "      exempledeDeRegleDeValidation:\n" +
            "        internationalizationName:\n" +
            "          fr: \"Juste un exemple\"\n" +
            "        checker:\n" +
            "          name: GroovyExpression\n" +
            "          params:\n" +
            "            groovy:\n" +
            "              expression: \"true\"\n" +
            "    format:\n" +
            "      constants:\n" +
            "        - rowNumber: 1\n" +
            "          columnNumber: 2\n" +
            "          boundTo:\n" +
            "            variable: localization\n" +
            "            component: site\n" +
            "          exportHeader: \"Site\"\n" +
            "      headerLine: 2\n" +
            "      firstRowLine: 3\n" +
            "      columns:\n" +
            "        - header: \"typeSite\"\n" +
            "          boundTo:\n" +
            "            variable: localization\n" +
            "            component: typeSite\n" +
            "        - header: \"site\"\n" +
            "          boundTo:\n" +
            "            variable: localization\n" +
            "            component: site\n" +
            "        - header: \"date\"\n" +
            "          boundTo:\n" +
            "            variable: date\n" +
            "            component: day\n" +
            "        - header: \"heure\"\n" +
            "          boundTo:\n" +
            "            variable: date\n" +
            "            component: time\n" +
            "        - header: \"Couleur des individus\"\n" +
            "          boundTo:\n" +
            "            variable: Couleur des individus\n" +
            "            component: value\n" +
            "        - header: \"Nombre d'individus valeur\"\n" +
            "          boundTo:\n" +
            "            variable: Nombre d'individus\n" +
            "            component: value\n" +
            "        - header: \"Nombre d'individus ecart type\"\n" +
            "          boundTo:\n" +
            "            variable: Nombre d'individus\n" +
            "            component: standardDeviation\n" +
            "    repository:\n" +
            "      filePattern: \"(.*)_(.*)_(.*)_(.*).csv\"\n" +
            "      authorizationScope:\n" +
            "        localization: 1\n" +
            "      startDate:\n" +
            "        token: 3\n" +
            "      endDate:\n" +
            "        token: 4";

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

    @Test
    public void TestResolveAliasInYaml() {
        final byte[] resolvedBytes = service.resolveAliasInYaml(yaml1.getBytes());
        final String resolvedYaml = new String(resolvedBytes);
        System.out.println(resolvedYaml);
        Assert.assertEquals(yaml1Resolved, resolvedYaml);
    }

    @Test
    public void testGetBeanAssumeClass() {
        String data =
                "firstName: \"John\"\n" +
                        "lastName: \"Doe\"\n" +
                        "age: 31\n" +
                        "contactDetails:\n" +
                        "   - type: \"mobile\"\n" +
                        "     number: 123456789\n" +
                        "   - type: \"landline\"\n" +
                        "     number: 456786868\n" +
                        "homeAddress:\n" +
                        "   line: \"Xyz, DEF Street\"\n" +
                        "   city: \"City Y\"\n" +
                        "   state: \"State Y\"\n" +
                        "   zip: 345657";
        Yaml snakeYaml = new Yaml(new Constructor(Customer.class));
        Customer customer = snakeYaml.load(data);
        System.out.println(customer);
    }

    @Getter
    @Setter
    public static class Customer {
        private String firstName;
        private String lastName;
        private int age;
        private List<Contact> contactDetails;
        private Address homeAddress;
    }

    @Getter
    @Setter
    public static class Contact {
        private String type;
        private int number;
    }

    @Getter
    @Setter
    public static class Address {
        private String line;
        private String city;
        private String state;
        private Integer zip;
    }
}