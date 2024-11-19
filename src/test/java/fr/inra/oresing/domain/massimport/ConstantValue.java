package fr.inra.oresing.domain.massimport;


import fr.inra.oresing.domain.application.configuration.ConstantComponent;
import fr.inra.oresing.domain.application.configuration.FileColumnConstantHeader;

public record ConstantValue(String component, int lineNumber, int rowNumber) {
    public static ConstantValue of(final ConstantComponent component) {
        String componentName = component.componentKey();
        int rowNumber = component.rowNumber();
        int columnNumber = -1;
        return switch (component.constantImportHeader()) {
            case FileColumnConstantHeader fileColumnConstantHeader ->
                    new ConstantValue(componentName, component.rowNumber(), fileColumnConstantHeader.columnNumber());
            default -> null;
        };
    }
}
