package fr.inra.oresing.domain.data.deposit.configuration;

import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.time.LocalDateTime;
import java.util.*;

public class ConfigurationSi {
    private final DataImporter dataImporter;

    public ConfigurationSi(DataImporter dataImporter) {
        this.dataImporter = dataImporter;
    }

    public Authorization getLineAuthorization(DataDatum referenceDatum, long lineNumber, ReportErrors errors) {
        final fr.inra.oresing.domain.application.configuration.Authorization authorization = dataImporter.getDataImporterContext().getAuthorization();
        if (authorization == null) {
            return new Authorization();
        }

        BinaryFileDataset binaryFileDataset = Optional.ofNullable(dataImporter.getDataImporterContext().getPublishContextBuilder())
                .map(PublishContext.PublishContextBuilder::build)
                .map(PublishContext::fileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .orElse(null);

        final Map<String, List<Ltree>> requiredAuthorizations = buildRequiredAuthorizations(authorization, referenceDatum);
        LocalDateTimeRange timeScope;
        DateType timeScopeDateLineChecker = authorization.timeScope() != null ?
                dataImporter.getDataImporterContext().getLineCheckers().stream()
                        .filter(dateType -> dateType.target().column().equals(authorization.timeScope()))
                        .map(LineChecker::underlyingType)
                        .filter(DateType.class::isInstance)
                        .map(DateType.class::cast)
                        .collect(MoreCollectors.onlyElement()) :
                null;


        if (timeScopeDateLineChecker != null) {
            LocalDateTime value = ((DateType) referenceDatum.get(new DataColumn(authorization.timeScope())).getValuesToCheck()).getValue();
            timeScope = LocalDateTimeRange.parse(value, timeScopeDateLineChecker);
        } else {
            timeScope = LocalDateTimeRange.always();
        }
        dataImporter.checkTimescopRangeInDatasetRange(timeScope, errors, binaryFileDataset, lineNumber);
        return new Authorization(
                requiredAuthorizations,
                timeScope
        );
    }

    public Map<String, List<Ltree>> buildRequiredAuthorizations(fr.inra.oresing.domain.application.configuration.Authorization authorization, DataDatum referenceDatum) {
        Map<String, List<Ltree>> requiredAuthorizations = new LinkedHashMap<String, List<Ltree>>();
        authorization.authorizationScope().stream()
                .map(AuthorizationScopeComponentData::component)
                .map(DataColumn::new)
                .map(referenceDatum::get)
                .map(DataColumnValue::toJsonForDatabase)
                .filter(ReferenceType.class::isInstance)
                .map(ReferenceType.class::cast)
                .forEach(referenceType -> {
                            List<Ltree> hierarchyOfHierarchicalkeys = getHierarchyOfHierarchicalkeys(referenceType);
                            requiredAuthorizations.put(referenceType.getRefType(), hierarchyOfHierarchicalkeys);
                        }
                );
        return requiredAuthorizations;
    }

    public List<Ltree> getHierarchyOfHierarchicalkeys(ReferenceType referenceType) {
        List<ReferenceScope.NodeDescription> nodesForMenu = dataImporter.getDataImporterContext().getNodesForMenu();
        List<Ltree> hierarchicalKeys = new LinkedList<Ltree>();
        ReferenceScope.NodeDescription referenceNode = nodesForMenu.stream()
                .filter(node -> node.node_type().equals(referenceType.getRefType()))
                .filter(node -> node.node_key().equals(referenceType.getHierarchicalKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("reference of type %s and hirarchicalkey %s doesn't exist".formatted(referenceType.getRefType(), referenceType.getHierarchicalKey())));
        hierarchicalKeys.add(referenceNode.node().node_key());
        while (referenceNode.parent_nk() != null) {
            ReferenceScope.NodeDescription finalReferenceNode = referenceNode;
            referenceNode = nodesForMenu.stream()
                    .filter(node -> node.node_type().equals(finalReferenceNode.parent_type()))
                    .filter(node -> node.node_nk().equals(finalReferenceNode.parent_nk()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("reference of type %s and hirarchicalkey %s doesn't exist".formatted(referenceType.getRefType(), referenceType.getHierarchicalKey())));
            hierarchicalKeys.add(referenceNode.node().node_key());
        }
        return hierarchicalKeys;
    }
}