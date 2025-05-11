package fr.inra.oresing.domain.data.deposit.validation.transformer;

import com.google.common.collect.ImmutableList;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.Datum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChainTransformersLineTransformerTest {

    @Test
    void transform_shouldApplyAllTransformersInOrder_DataDatum() {
        // Préparation des mocks
        LineTransformer t1 = mock(LineTransformer.class);
        LineTransformer t2 = mock(LineTransformer.class);
        DataDatum input = mock(DataDatum.class);
        DataDatum afterT1 = mock(DataDatum.class);
        DataDatum afterT2 = mock(DataDatum.class);

        when(t1.transform(input, Map.of())).thenReturn(afterT1);
        when(t2.transform(afterT1, Map.of())).thenReturn(afterT2);

        ChainTransformersLineTransformer chain = new ChainTransformersLineTransformer(ImmutableList.of(t1, t2));

        // Exécution
        DataDatum result = chain.transform(input, Map.of());

        // Vérification
        assertThat(result).isSameAs(afterT2);
        verify(t1).transform(input, Map.of());
        verify(t2).transform(afterT1, Map.of());
    }

    @Test
    void transform_shouldApplyAllTransformersInOrder_Datum() {
        // Préparation des mocks
        LineTransformer t1 = mock(LineTransformer.class);
        LineTransformer t2 = mock(LineTransformer.class);
        Datum input = mock(Datum.class);
        Datum afterT1 = mock(Datum.class);
        Datum afterT2 = mock(Datum.class);

        when(t1.transform(input)).thenReturn(afterT1);
        when(t2.transform(afterT1)).thenReturn(afterT2);

        ChainTransformersLineTransformer chain = new ChainTransformersLineTransformer(ImmutableList.of(t1, t2));

        // Exécution
        Datum result = chain.transform(input);

        // Vérification
        assertThat(result).isSameAs(afterT2);
        verify(t1).transform(input);
        verify(t2).transform(afterT1);
    }
}