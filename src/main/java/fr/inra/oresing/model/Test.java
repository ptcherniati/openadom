package fr.inra.oresing.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
    public static void main(String[] args) {
        String datatype ="piegeage_en_montee";
        String ab = Arrays.stream(datatype.split("_"))
                .map(s -> s.substring(0, 1))
                .collect(Collectors.joining());
        System.out.println(ab);
    }
}
