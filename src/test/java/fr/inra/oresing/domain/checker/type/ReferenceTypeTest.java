package fr.inra.oresing.domain.checker.type;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

class ReferenceTypeTest {
    LineChecker.Transformer transformer;
    ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues;
    LineChecker checker;
    ReferenceValidationCheckResult checkGood;
    ReferenceValidationCheckResult checkGoodValueNotLabel;
    ReferenceValidationCheckResult checkBad;
    private DataColumn dataColumn;
    private String goodValue;
    private String badValue;
    private String goodValueNotLabel;
    private UUID uuid1;
    private UUID uuid2;
    private UUID uuid3;
    private UUID uuid4;

    @BeforeEach
    public void before() {
        dataColumn = new DataColumn("laColonne");
        goodValue = "leman";
        badValue = "annecy";
        goodValueNotLabel = "LéMan";
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        uuid3 = UUID.randomUUID();
        uuid4 = UUID.randomUUID();
        referenceValues = new ImmutableMap.Builder()
                .put(new DataValue.LineIdentityColumnName(Ltree.fromSql(goodValue), Ltree.fromSql(goodValue)), ImmutableSet.of(uuid1))
                .build();
        checker = Mockito.mock(LineChecker.OneChecker.class);

        Mockito.when(checker.target()).thenReturn(dataColumn);
        checkGood = (ReferenceValidationCheckResult) buildReference().check(goodValue, checker);
        checkGoodValueNotLabel = (ReferenceValidationCheckResult) buildReference().check(goodValueNotLabel, checker);
        checkBad =(ReferenceValidationCheckResult)  buildReference().check(badValue, checker);
    }

    ReferenceType buildReference() {
        return new ReferenceType(
                dataColumn,
                "",
                referenceValues,
                transformer
        );
    }


    @Test
    @Tag("SUITE")
    void check() {
        Assert.assertTrue(checkGood.isSuccess());
        Assertions.assertEquals(goodValue, checkGood.value().getValue().toString());
        Assertions.assertEquals(goodValue, checkGood.matchedReferenceHierarchicalKey ().stream().map(Ltree::toString).findFirst().orElse("null"));
        Assertions.assertEquals(uuid1, checkGood.matchedReferenceId ().stream().findFirst().orElse(null));
        Assert.assertTrue(checkGoodValueNotLabel.isSuccess());
        Assertions.assertEquals(goodValue, checkGoodValueNotLabel.value().getValue().toString());
        Assertions.assertEquals(goodValue, checkGoodValueNotLabel.matchedReferenceHierarchicalKey ().stream().map(Ltree::toString).findFirst().orElse("null"));
        Assertions.assertEquals(uuid1, checkGoodValueNotLabel.matchedReferenceId ().stream().findFirst().orElse(null));
        Assert.assertFalse(checkBad.isSuccess());
    }
}