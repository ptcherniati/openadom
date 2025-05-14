package fr.inra.oresing.domain.exceptions.application;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Arrays;
import java.util.List;
@Value
@EqualsAndHashCode(callSuper=false)
public class BadLabelNameException extends OreSiTechnicalException {
    /**
     * Le nom de l'application doit commencer par un caractère minuscule et ne contenir que des lettres minuscules, des chiffres ou le caractère souligné.
     * La longueur du nom doit être compris entre 2 et 20 caractères.
     */
    private static String BAD_LABEL_NAME = "BAD_LABEL_NAME";
    List<String> args;
    LabelType labelType;
    public BadLabelNameException(final LabelType labelType, final String name, final String... args) {
        super(name);
        this.labelType = labelType;
        this.args =Arrays.stream(args).toList();
    }


    public enum LabelType{
        APPLICATION,
        DATATYPE,
        REFERENCE
    }
}