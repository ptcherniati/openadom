package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescriptionBuilder;

public class FieldDescriptionBuilder {

    public static RightsRequestFieldBuilder rightsRequestField() {
        return new RightsRequestFieldBuilder();
    }

    public static AdditionalFileFieldBuilder additionalFileField() {
        return new AdditionalFileFieldBuilder();
    }

    public static class RightsRequestFieldBuilder {
        private int order = 1;
        private boolean required = false;
        private CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();

        public RightsRequestFieldBuilder order(int order) {
            this.order = order;
            return this;
        }

        public RightsRequestFieldBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public RightsRequestFieldBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public RightsRequestField build() {
            return new RightsRequestField(order, FieldDescription.FieldDescriptionType.RightsRequestField, required, checker);
        }
    }

    public static class AdditionalFileFieldBuilder {
        private int order = 1;
        private boolean required = false;
        private CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();

        public AdditionalFileFieldBuilder order(int order) {
            this.order = order;
            return this;
        }

        public AdditionalFileFieldBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public AdditionalFileFieldBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public AdditionalFileField build() {
            return new AdditionalFileField(order, FieldDescription.FieldDescriptionType.AdditionalFileField, required, checker);
        }
    }
}